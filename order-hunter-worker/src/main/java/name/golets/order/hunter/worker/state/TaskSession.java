package name.golets.order.hunter.worker.state;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.Order;

/**
 * Represents one polling task session.
 *
 * <p>Session metadata is immutable once created, while runtime counters and deduplication keys are
 * maintained via lock-free primitives.
 */
public final class TaskSession {

  private final String sessionId;
  private final String hunterId;
  private final int headsToTake;
  private final List<OrderType> orderTypes;
  private final Set<String> savedOrderSids;
  private final AtomicInteger headsTaken;
  private final Instant startedAt;
  private final AtomicReference<Instant> endedAt;

  /**
   * Creates a new task session with empty progress and deduplication state.
   *
   * @param sessionId session correlation identifier
   * @param hunterId worker instance identifier
   * @param headsToTake configured head budget
   * @param orderTypes allowed order types for polling
   * @param startedAt session start timestamp
   */
  public TaskSession(
      String sessionId,
      String hunterId,
      int headsToTake,
      List<OrderType> orderTypes,
      Instant startedAt) {
    this(
        normalize(sessionId),
        normalize(hunterId),
        headsToTake,
        safeOrderTypes(orderTypes),
        ConcurrentHashMap.newKeySet(),
        0,
        startedAt,
        null);
  }

  private TaskSession(
      String sessionId,
      String hunterId,
      int headsToTake,
      List<OrderType> orderTypes,
      Set<String> savedOrderSids,
      int headsTaken,
      Instant startedAt,
      Instant endedAt) {
    this.sessionId = sessionId;
    this.hunterId = hunterId;
    this.headsToTake = headsToTake;
    this.orderTypes = orderTypes;
    this.savedOrderSids = savedOrderSids;
    this.headsTaken = new AtomicInteger(headsTaken);
    this.startedAt = startedAt;
    this.endedAt = new AtomicReference<>(endedAt);
  }

  /**
   * Returns a new session with an updated session identifier while preserving runtime progress.
   *
   * @param value new session identifier
   * @return updated session instance
   */
  public TaskSession withSessionId(String value) {
    return copyWith(normalize(value), hunterId, headsToTake, orderTypes);
  }

  /**
   * Returns a new session with an updated worker identifier while preserving runtime progress.
   *
   * @param value new worker instance identifier
   * @return updated session instance
   */
  public TaskSession withHunterId(String value) {
    return copyWith(sessionId, normalize(value), headsToTake, orderTypes);
  }

  /**
   * Returns a new session with an updated head budget while preserving runtime progress.
   *
   * @param value new head budget
   * @return updated session instance
   */
  public TaskSession withHeadsToTake(int value) {
    return copyWith(sessionId, hunterId, value, orderTypes);
  }

  /**
   * Returns a new session with updated allowed order types while preserving runtime progress.
   *
   * @param value new allowed order types
   * @return updated session instance
   */
  public TaskSession withOrderTypes(List<OrderType> value) {
    return copyWith(sessionId, hunterId, headsToTake, safeOrderTypes(value));
  }

  private TaskSession copyWith(
      String copiedSessionId,
      String copiedHunterId,
      int copiedHeadsToTake,
      List<OrderType> copiedOrderTypes) {
    Set<String> copiedSavedSids = ConcurrentHashMap.newKeySet();
    copiedSavedSids.addAll(savedOrderSids);
    return new TaskSession(
        copiedSessionId,
        copiedHunterId,
        copiedHeadsToTake,
        copiedOrderTypes,
        copiedSavedSids,
        headsTaken.get(),
        startedAt,
        endedAt.get());
  }

  /**
   * Records a successfully saved order in this session.
   *
   * @param order saved order entity
   */
  public void registerSuccessfulSave(Order order) {
    if (order == null) {
      return;
    }
    if (order.getSid() != null) {
      savedOrderSids.add(order.getSid());
    }
    headsTaken.addAndGet(order.getHeads());
  }

  /**
   * Replaces current heads-taken value.
   *
   * @param value absolute heads-taken counter
   */
  public void setHeadsTaken(int value) {
    headsTaken.set(value);
  }

  /**
   * Marks this session as ended.
   *
   * @param value end timestamp (null clears end marker)
   */
  public void setEndedAt(Instant value) {
    endedAt.set(value);
  }

  /**
   * Returns the session correlation identifier.
   *
   * @return session correlation identifier
   */
  public String getSessionId() {
    return sessionId;
  }

  /**
   * Returns the worker instance identifier.
   *
   * @return worker instance identifier
   */
  public String getHunterId() {
    return hunterId;
  }

  /**
   * Returns the configured head budget.
   *
   * @return configured head budget
   */
  public int getHeadsToTake() {
    return headsToTake;
  }

  /**
   * Returns an immutable view of allowed order types.
   *
   * @return immutable view of allowed order types
   */
  public List<OrderType> getOrderTypes() {
    return List.copyOf(orderTypes);
  }

  /**
   * Returns the accumulated head count.
   *
   * @return accumulated head count
   */
  public int getHeadsTaken() {
    return headsTaken.get();
  }

  /**
   * Returns an immutable snapshot of already-saved order identifiers.
   *
   * @return immutable snapshot of already-saved order ids
   */
  public Set<String> getSavedOrderSids() {
    return Set.copyOf(savedOrderSids);
  }

  /**
   * Returns the session start timestamp.
   *
   * @return session start timestamp
   */
  public Instant getStartedAt() {
    return startedAt;
  }

  /**
   * Returns the session end timestamp.
   *
   * @return session end timestamp (or null when still active)
   */
  public Instant getEndedAt() {
    return endedAt.get();
  }

  /**
   * Returns whether accumulated heads reached the configured budget.
   *
   * @return whether accumulated heads reached configured budget
   */
  public boolean isCompleted() {
    return headsTaken.get() >= headsToTake;
  }

  private static String normalize(String value) {
    return value != null ? value : "";
  }

  private static List<OrderType> safeOrderTypes(List<OrderType> value) {
    return value != null ? List.copyOf(value) : List.of();
  }
}
