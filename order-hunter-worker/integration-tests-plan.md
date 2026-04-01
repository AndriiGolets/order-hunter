# order-hunter-worker — integration test plan

This document describes planned Spring Boot integration tests for the poll–save–notify worker flow described in [worker-BRD.md](worker-BRD.md). It ties together fixture JSON (shape: `OrdersResponse` in `order-hunter-common`), mocking strategy, and end-to-end scenarios. Use it to implement tests and to catch contract or timing gaps before they flake in CI.

## Scope and references

- **In scope:** `SqsEventController` command handling (`StartEvent`, `StopEvent`, `StatusEvent`), `WorkerStarter` tick loop, HTTP poll/save via WebClient against a mock HTTP server, outbound `OrderTaken` publication (SQS client mocked).
- **BRD alignment:** Stages `pollRecordsStage` → … → `notifySqsStage`; no automatic retry on HTTP failures ([worker-BRD.md](worker-BRD.md) §4.7, parallel save failure policy).
- **Outbound contract:** `OrderTaken` includes saved orders, `completed` when `headsTaken >= headsToTake` (BRD). **`headsTaken`** is the **accumulated head count** from successfully assigned orders (not the number of order rows). Selection **always prefers orders with a higher head count** even when that exceeds `headsToTake` ([worker-BRD.md](worker-BRD.md) filter preference rule).

## Test fixtures (`src/test/resources`)

| File | Role | Notes |
|------|------|--------|
| `freeOrders.json` | Poll response body | Matches `OrdersResponse`: 8 `records` per `result_info` — 6 main orders and 2 order helpers. Order `ov2_recLQ1ExOBR4FuUjm` represents a **2-head** product (see parsing rules in `OrderParsingUtil`). |
| `oneOrder.json` | Poll response body | Single order `ov2_recCH2TXf4vkGD4vQ`. |
| `twoOrders.json` | Poll response body | Two orders: `ov2_recTFfDu6XEt9sk1O`, `ov2_recLQ1ExOBR4FuUjm` (2 heads), plus helper `ov2_rechIpUbg4Q9cexkY`. |
| `threeOrder.Json` | Poll response body | Same as `twoOrders.json` plus the same single order as `oneOrder.json` (3 main-line orders in the mix; helpers as in two-order fixture). **Filename casing** differs from others — fix or reference consistently so builds work on case-sensitive filesystems. |

Load fixtures with the test classpath and deserialize to `OrdersResponse` (or raw JSON strings) so poll stubs return byte-identical API payloads.

## Mocking strategy

- **HTTP:** Use **MockWebServer** (or equivalent “WebServerMock”) with **separate configuration for poll vs save**:
  - **Poll mock:** enqueue responses for the free/priority/fast-track poll URL(s) configured in test `application.yml`.
  - **Save mock:** enqueue responses for `PATCH` (or configured save path) per BRD (`airportalHost + saveArtistNamePath + artistSid`).
  - Assert request counts, paths, and presence of required headers (`xApiToken`, `xStackId`) where valuable.
- **SQS / controller:** Mock **Amazon SQS client** and **`SqsEventController`** (or the listener entry point) with standard Mockito `@Mock` / `@MockBean` patterns so inbound commands are invoked directly in tests without a real queue.
- **OrderTaken:** Capture outbound messages via mocked SQS `sendMessage` (or dedicated outbound port if abstracted) and assert payload fields.

## Test application configuration

Override for deterministic timing:

```yaml
order-hunter:
  disableJitterRandomize: true
  betweenPollsJitterMax: 500
```

Keep other timeouts (`pollingTimeout`, `savingTimeout`) explicit in test profile so failures surface quickly. Point `airportalHost` at the mock server base URL.

## Scenario catalogue

### Scenario 1 — Stop suppresses further polling

1. Deliver **StartEvent** with `headsToTake = 1`, order types **NORMAL**.
2. Poll stub returns **empty** `OrdersResponse` for every poll.
3. Wait **~3 s** (enough for multiple ticks at `betweenPollsJitterMax` with jitter disabled).
4. Deliver **StopEvent**.
5. **Assert:** No further poll requests after stop (use request counter or latch after stop timestamp).
6. **Assert:** Save stub **never** invoked.

**Validates:** `WorkerStateManager.started`, starter loop respecting stop, no save when no candidates.

**Risks:** Flaky if wait is shorter than one full tick + flow duration; prefer awaiting a condition (e.g. “N polls observed”) then stop, or use deterministic scheduler if introduced later.

---

### Scenario 2 — Poll HTTP 500 on first call, then OK; flow continues

1. Start with `headsToTake = 1`, **NORMAL**.
2. First poll response: **HTTP 500**; subsequent polls: **200** + empty body.
3. Wait **~3 s**, then **StopEvent**.
4. **Assert:** Poll mock received **more than one** request (worker does not permanently halt after one failure — align with BRD “no retry” per failed *order* save, but poll tick continues).
5. **Assert:** Save stub **not** called.

**Validates:** Error handling for poll does not brick the loop; empty body after error yields no save.

**Risks:** If implementation treats poll 500 as fatal and stops ticking, this scenario documents **expected** behavior and should fail until product decision is coded. BRD does not mandate poll retries; it mandates stage error handling without canceling unrelated work.

---

### Scenario 3 — Restart after stop (second StartEvent)

1. Same as scenario 2 through stop.
2. Send **second StartEvent** (same parameters).
3. **Assert:** Poll mock receives requests again after the second start.

**Validates:** Stop clears or gates `started`; a new session can poll again without process restart.

**Risks:** If state (`headsTaken`, deduplication set) is not reset on start/stop, behavior may differ; document whether **StartEvent** resets session or appends to it and assert accordingly.

---

### Scenario 4 — Empty polls, then `freeOrders.json`, then empty; take 2-head order; stop after `OrderTaken`

**Selection rule (required behavior):** Among eligible main orders, the flow **always prefers a higher head count**. In `freeOrders.json`, order **`ov2_recLQ1ExOBR4FuUjm`** has **2 heads**. With **`headsToTake = 1`**, that order **must still be chosen** (one assignment that contributes 2 heads), not a 1-head order that merely meets the minimum. This matches the BRD: sort by heads descending and prefer the larger-head order even when it overshoots the target.

1. Start with `headsToTake = 1`, **NORMAL**.
2. Poll sequence: **empty** for first **3** responses, then **200** + body from `freeOrders.json`, then **empty** for subsequent polls.
3. As soon as **OrderTaken** is observed, send **StopEvent**.
4. Wait **~3 s**.
5. **Assert:** Exactly **4** poll requests hit the poll mock (3 empty + 1 with data).
6. **Assert:** **OrderTaken** sent once; **`headsTaken == 2`** (head count from the single taken order); **`completed == true`** because `headsTaken >= headsToTake`; saved main order **`sid == ov2_recLQ1ExOBR4FuUjm`**.
7. **Assert:** Save mock invoked **once** for the main save path (or once per BRD stage if split — match implementation: main vs helper saves).

**Validates:** Higher-heads preference with overshoot, `headsTaken` as accumulated heads, `completed` flag, notify after successful save path, stop halting further polls.

**Risks / ambiguities:**

- **Fourth poll timing:** The fourth request must be the one returning `freeOrders.json`; if the flow issues an extra poll before save completes, the “4 requests” assertion may need relaxation or stricter sync (e.g. await save then count).
- **Helpers:** `freeOrders.json` includes helpers; BRD saves helpers after mains — clarify whether save mock call count **includes** helper PATCHes (then “1 time” may be wrong).

---

### Scenario 5 — `headsToTake = 3`; two mains (2 + 1 heads); parallel saves; one 500; other still succeeds

**Selection:** With **`headsToTake = 3`**, `filterRecordsStage` should pick **two main orders** whose head counts sum to the target: **one order with 2 heads and one with 1 head** (after sorting by heads descending and applying the same preference rules as Scenario 4 over `freeOrders.json`). **One HTTP save per main order** (helpers, if any for the saved mains, may add more PATCHes — stub them to **200** or extend assertions if the flow always saves helpers in the same tick).

**Goal:** Prove **parallel save failure isolation** ([worker-BRD.md](worker-BRD.md) parallel save policy): when **two main saves** run concurrently, a **500 on one branch** must **not** prevent the **other** order from completing successfully. This is **not** retry: expect **exactly two** main save HTTP calls total (one 500, one 200), not a third call retrying the failed order.

**Save mock requirements:** The mock must **support parallel in-flight save requests** (e.g. MockWebServer with concurrent connections; avoid a single-threaded handler that serializes both requests before responding). Configure **500 vs 200 by request identity** (path/body/`sid`), **not** by “first vs second request,” because arrival order is nondeterministic under parallelism. Example: fail the **2-head** main’s `PATCH`, succeed the **1-head** main — then assert **`OrderTaken`** contains **only** the 1-head order’s `sid` and **`headsTaken == 1`** (swap failure mapping if the test should prove the 2-head path succeeded instead).

1. Configure **save** mock: **one** main order’s `PATCH` → **500**, the **other** main → **200** (identity-based).
2. Start with **`headsToTake = 3`**, **NORMAL**.
3. Same poll pattern as scenario 4 (3× empty, then `freeOrders.json`, then empty).
4. On **OrderTaken**, send **StopEvent**; wait **~3 s**.
5. **Assert:** **4** poll requests (same pattern as scenario 4).
6. **Assert:** **Exactly 2** main-order save HTTP calls (parallel), **no** third call for retry of the failed main.
7. **Assert:** **`OrderTaken`** lists **only** the successful main; failed main’s `sid` **absent**; **`headsTaken`** equals that order’s head count; explicitly verify the **other** order (the non-failed branch) was saved.

**Validates:** Parallel main saves; one failure does not block the other; outbound event and progress reflect only successes; no save retry.

**Risks / ambiguities:**

- **Helper PATCHes:** May follow mains; stub helpers to 200 or count them separately so “2 saves” stays scoped to the two mains.
- **Failure mapping:** Keep one fixed assignment (which `sid` gets 500) so expected `headsTaken` and event contents stay stable.

## Cross-cutting issues and flakiness

| Topic | Issue | Mitigation |
|--------|--------|------------|
| Time-based waits | Fixed `sleep(3000)` is brittle under load | Prefer `Awaitility` (or similar) with timeouts and polling intervals; cap total test time. |
| Jitter | Random jitter breaks counting | `disableJitterRandomize: true` as specified. |
| Async flow | Order of poll vs save vs SQS | Use blocking queues / `CountDownLatch` for `OrderTaken` and for mock request recording. |
| Threading | Parallel saves reorder mock hits | Assert **multiset** of paths/sids or use strict ordering only if API guarantees it. |
| Empty response | Valid `OrdersResponse` shape | Use `{ "result_info": { "total_results": 0 }, "records": [] }` or equivalent so parsing matches production. |
| Headers | Contract drift | Spot-check `xApiToken` / `xStackId` on at least one poll and one save test. |

## Suggested additional test cases

1. **Duplicate start:** Two `StartEvent`s without stop — assert idempotent state or last-write-wins per product rules.
2. **Stop before any start:** `StopEvent` only — no polls, no errors.
3. **StatusEvent:** Returns `started`, `headsToTake`, `headsTaken`, and session metadata per BRD.
4. **Order type filtering:** Start with non-NORMAL types (if applicable) and stub path or query so only the correct poll endpoint is hit.
5. **Already-taken deduplication:** Second tick with same `freeOrders.json` after first successful take — same `sid` must not be saved again; `OrderTaken` reflects only new work.
6. **All save branches fail:** Poll returns one candidate; every save returns 5xx — no successful `OrderTaken` (or empty list) per BRD; state `headsTaken` unchanged.
7. **Poll timeout:** Short `pollingTimeout` with delayed response — stage error handled; tick continues (align with implementation).
8. **`oneOrder.json` / `twoOrders.json` / `threeOrder.Json`:** Targeted filter tests for minimum subset to reach `headsToTake` (1 vs 2 vs 3 heads) and helper association rules.
9. **maxParallelOrdersToSaveThreads:** Fixture with many mains; verify concurrency cap (e.g. via semaphore or request in-flight counter) if observable.
10. **Outbound serialization:** `OrderTaken` JSON matches consumer schema (`eventVersion`, `producedAt`, order list).

## Implementation checklist

- [ ] Test profile `application.yml` with mock host, jitter flags, and short timeouts.
- [ ] Shared test utility: start mock server, reset queues, build `OrdersResponse` from fixture files.
- [ ] Clear naming: `WebServerPollMock` vs `WebServerSaveMock` (or one server with path routing).
- [ ] Document `headsTaken` (sum of heads from saved mains/helpers per contract) and `completed` in test Javadoc; align Scenario 4 assertions with higher-heads preference.
- [ ] Rename `threeOrder.Json` → `threeOrders.json` for consistency (optional but recommended for Linux CI).
