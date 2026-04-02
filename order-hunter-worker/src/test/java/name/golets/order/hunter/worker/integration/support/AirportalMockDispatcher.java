package name.golets.order.hunter.worker.integration.support;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.springframework.lang.Nullable;

/**
 * Routes MockWebServer traffic for airportal poll (GET) and save ({@code PATCH} under {@code
 * /api/records/}). Supports parallel PATCH handling via per-path response rules.
 */
public final class AirportalMockDispatcher extends Dispatcher {

  private static final MockResponse EMPTY_200 =
      jsonResponse(200, "{\"result_info\":{\"total_results\":0},\"records\":[]}");

  private final AtomicInteger pollCount = new AtomicInteger();
  private final AtomicInteger patchCount = new AtomicInteger();
  private final ConcurrentMap<String, Integer> patchStatusByOrderSid = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicInteger> patchAttemptsByOrderSid =
      new ConcurrentHashMap<>();

  private final Object pollSequenceLock = new Object();

  @Nullable private Queue<MockResponse> pollSequenceQueue;

  private volatile PollMode pollMode = PollMode.EMPTY_FOREVER;
  private final AtomicInteger poll500Remaining = new AtomicInteger();

  private volatile int patchHandlingDelayMs;
  private final AtomicInteger patchConcurrent = new AtomicInteger();
  private final AtomicInteger patchMaxConcurrent = new AtomicInteger();

  /** How the poll endpoint responds across successive GET requests. */
  public enum PollMode {
    /** Every poll returns an empty {@link name.golets.order.hunter.common.model.OrdersResponse}. */
    EMPTY_FOREVER,
    /** First {@link #poll500Remaining} polls return HTTP 500, then {@link #EMPTY_FOREVER}. */
    FIRST_POLLS_THEN_EMPTY,
    /**
     * Dequeues {@link #pollSequenceQueue} per GET (three empty JSON responses, one payload, then
     * falls back to empty).
     */
    POLL_SEQUENCE_THEN_EMPTY
  }

  /** Resets all counters and patch rules; default poll mode is empty forever. */
  public void reset() {
    pollCount.set(0);
    patchCount.set(0);
    patchStatusByOrderSid.clear();
    patchAttemptsByOrderSid.clear();
    synchronized (pollSequenceLock) {
      pollSequenceQueue = null;
    }
    pollMode = PollMode.EMPTY_FOREVER;
    poll500Remaining.set(0);
    patchHandlingDelayMs = 0;
    patchConcurrent.set(0);
    patchMaxConcurrent.set(0);
  }

  /**
   * When positive, each PATCH handler sleeps this long while holding a concurrency slot so parallel
   * save calls overlap (for asserting {@link #getPatchMaxConcurrent()} vs worker parallelism).
   */
  public void setPatchHandlingDelayMs(int delayMs) {
    this.patchHandlingDelayMs = Math.max(0, delayMs);
  }

  /** Peak number of PATCH requests executing {@link #setPatchHandlingDelayMs} concurrently. */
  public int getPatchMaxConcurrent() {
    return patchMaxConcurrent.get();
  }

  public void setPollEmptyForever() {
    pollMode = PollMode.EMPTY_FOREVER;
  }

  public void setFirstPolls500ThenEmpty(int errorPollCount) {
    pollMode = PollMode.FIRST_POLLS_THEN_EMPTY;
    poll500Remaining.set(errorPollCount);
  }

  /**
   * Each poll GET dequeues the next JSON body; when the queue is empty, further polls return empty
   * records (same as {@link PollMode#POLL_SEQUENCE_THEN_EMPTY} fallback).
   */
  public void setPollBodiesThenEmpty(List<String> ordersResponseJsonBodies) {
    synchronized (pollSequenceLock) {
      pollSequenceQueue = new ArrayDeque<>(ordersResponseJsonBodies.size());
      for (String json : ordersResponseJsonBodies) {
        pollSequenceQueue.add(jsonResponse(200, json));
      }
    }
    pollMode = PollMode.POLL_SEQUENCE_THEN_EMPTY;
  }

  /**
   * Configures three empty polls followed by one JSON payload, then empty polls again.
   *
   * @param ordersResponseJson full JSON body for the single non-empty poll response (after three
   *     empty polls)
   */
  public void setThreeEmptyThenPayloadThenEmpty(String ordersResponseJson) {
    synchronized (pollSequenceLock) {
      pollSequenceQueue = new ArrayDeque<>(4);
      // Fresh MockResponse per queued poll: OkHttp may consume body buffers when a response is
      // served; reusing the same EMPTY_200 instance three times can drain the body after the 1st.
      String emptyJson = "{\"result_info\":{\"total_results\":0},\"records\":[]}";
      pollSequenceQueue.add(jsonResponse(200, emptyJson));
      pollSequenceQueue.add(jsonResponse(200, emptyJson));
      pollSequenceQueue.add(jsonResponse(200, emptyJson));
      pollSequenceQueue.add(jsonResponse(200, ordersResponseJson));
    }
    pollMode = PollMode.POLL_SEQUENCE_THEN_EMPTY;
  }

  /** HTTP status for PATCH {@code /api/records/{orderSid}}; default 200 when absent. */
  public void setPatchHttpStatus(String orderSid, int status) {
    patchStatusByOrderSid.put(orderSid, status);
  }

  public int getPollCount() {
    return pollCount.get();
  }

  public int getPatchCount() {
    return patchCount.get();
  }

  public int getPatchAttemptsForOrderSid(String orderSid) {
    AtomicInteger n = patchAttemptsByOrderSid.get(orderSid);
    return n == null ? 0 : n.get();
  }

  @Override
  public MockResponse dispatch(RecordedRequest request) {
    if ("GET".equalsIgnoreCase(request.getMethod())) {
      return handlePoll();
    }
    if ("PATCH".equalsIgnoreCase(request.getMethod())) {
      String path = request.getPath();
      if (path != null && path.startsWith("/api/records/")) {
        return handlePatch(path);
      }
    }
    return new MockResponse().setResponseCode(404);
  }

  private MockResponse handlePoll() {
    pollCount.incrementAndGet();
    return switch (pollMode) {
      case EMPTY_FOREVER -> EMPTY_200;
      case FIRST_POLLS_THEN_EMPTY -> {
        if (poll500Remaining.get() > 0) {
          poll500Remaining.decrementAndGet();
          yield new MockResponse().setResponseCode(500).setBody("poll error");
        }
        yield EMPTY_200;
      }
      case POLL_SEQUENCE_THEN_EMPTY -> {
        synchronized (pollSequenceLock) {
          if (pollSequenceQueue != null) {
            MockResponse next = pollSequenceQueue.poll();
            if (next != null) {
              yield next;
            }
          }
        }
        yield EMPTY_200;
      }
    };
  }

  private MockResponse handlePatch(String path) {
    int delayMs = patchHandlingDelayMs;
    if (delayMs > 0) {
      int current = patchConcurrent.incrementAndGet();
      patchMaxConcurrent.updateAndGet(max -> Math.max(max, current));
      try {
        Thread.sleep(delayMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        patchConcurrent.decrementAndGet();
      }
    }

    patchCount.incrementAndGet();
    String sid = path.substring(path.lastIndexOf('/') + 1);
    patchAttemptsByOrderSid.computeIfAbsent(sid, k -> new AtomicInteger()).incrementAndGet();
    int status = patchStatusByOrderSid.getOrDefault(sid, 200);
    if (status >= 400) {
      return new MockResponse().setResponseCode(status).setBody("save error");
    }
    return new MockResponse().setResponseCode(200).setBody("{}");
  }

  private static MockResponse jsonResponse(int code, String json) {
    Buffer body = new Buffer();
    body.writeUtf8(json);
    return new MockResponse()
        .setResponseCode(code)
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(body);
  }
}
