# AI_RULES.md

## Purpose
This repository is a production-grade Java Spring Boot WebFlux service.
The primary goals are:
- maintainability
- explicit architecture
- predictable observability
- minimal hidden magic
- safe AI-assisted changes

---

## Global Rules For AI Agents

### 1. Change Scope
- Only modify files directly relevant to the task.
- Do not refactor unrelated code unless explicitly requested.
- Prefer the smallest correct change.
- Before creating new files/classes, check if an existing component should be extended.

### 2. Simplicity First
- Prefer simple, direct code over clever abstractions.
- Do not introduce additional layers, wrappers, facades, or helper classes unless they remove duplication across 3+ real usages.
- Avoid speculative abstractions.
- Avoid "future-proofing" code that is not needed by the current requirement.

### 3. Architecture Constraints
- Follow package-by-feature, not package-by-layer, unless explicitly stated otherwise.
- Each feature should contain only the classes required for that feature.
- Do not create circular dependencies between packages.
- Controllers must not contain business logic.
- Services must not depend on controllers.
- Domain logic must not depend on infrastructure details.
- DTOs must not leak into domain logic.
- Avoid static utility classes unless the logic is pure and stateless.

### 4. WebFlux Rules
- Use reactive types end-to-end for reactive flows.
- Do not call `.block()`, `.blockOptional()`, `.toFuture().get()`, or any equivalent in production code.
- Do not mix imperative and reactive code unless at a clearly isolated boundary.
- Do not introduce `subscribe()` in application code except in framework bootstrap or explicitly approved fire-and-forget infrastructure.
- Prefer composition with `flatMap`, `map`, `zip`, `onErrorResume`, and `switchIfEmpty`.
- Avoid deeply nested reactive chains; extract private methods for readability.

### 5. WebClient Rules
- Always create clients via injected `WebClient.Builder`.
- Never use `WebClient.create(...)` directly.
- Use URI templates, not string concatenation.
- Configure timeouts explicitly at the HTTP client layer.
- Add retries only when explicitly safe and idempotent.

### 6. Observability Rules
- Use Micrometer Observation as the primary application-level observability API.
- Prefer `ObservationRegistry` / `Observation` over direct tracer/span APIs in business code.
- Add business observations only at meaningful boundaries:
  - use-case execution
  - external integration call
  - expensive fallback
- Do not create spans for every tiny method.
- Never put IDs or high-cardinality values into low-cardinality tags.
- If adding custom tags, prefer shared conventions/filters over ad-hoc per-method span mutation.

### 7. Error Handling
- Use explicit exception mapping at API boundaries.
- Avoid swallowing exceptions.
- For business failures represented as normal responses, preserve observability with explicit outcome tags.

### 8. Testing Rules
- New business logic must include tests.
- Prefer unit tests for pure logic and slice/integration tests for wiring.
- Do not mock everything by default.
- Use realistic tests for WebFlux behavior.
- Architecture rules should be enforced by ArchUnit tests.

### 9. Dependency Hygiene
- Do not add new dependencies unless required.
- Prefer Spring Boot managed dependencies.
- If adding a dependency, explain why it is needed and whether it is test-only or runtime.
- Avoid overlapping libraries that solve the same problem.

### 10. Output Expectations
When proposing changes:
1. Explain the change briefly.
2. Show the exact files to modify.
3. Keep code production-ready.
4. Do not leave TODO placeholders unless explicitly requested.
5. If the task is ambiguous, state assumptions explicitly before generating code.

## 11. Documentation Rules (Mandatory)

All new or modified logic must include documentation updates in the same change.

1. Required for production code
- Every new class must have Javadoc.
- Every new public method must have Javadoc.
- Every modified public method must have its Javadoc reviewed and updated if behavior, assumptions, side effects, error handling, or reactive behavior changed.
- Protected/package-private methods should have Javadoc when they contain non-trivial business logic or are important extension points.
- Private methods do not require Javadoc unless the logic is complex, non-obvious, or business-critical.

2. Required for tests
- Every test class must have a short class-level Javadoc describing the scope of the test.
- Every test method must include Javadoc explaining:
  - what behavior is being verified
  - why the scenario matters
  - any important edge case, reactive behavior, or business rule being asserted

3. Required inline comments
- Add inline comments only for:
  - non-obvious business rules
  - important reactive flow decisions
  - retry/timeout/error handling rationale
  - observability decisions (tags, span boundaries, conventions)
  - tricky framework workarounds
- Do not add obvious comments that simply restate the code.

4. Required when logic changes
- If requested work changes behavior (not just refactoring), add or update comments/Javadocs to explicitly describe:
  - what changed logically
  - the intended behavior
  - important assumptions or constraints
- Documentation updates are part of the change and must not be omitted.
