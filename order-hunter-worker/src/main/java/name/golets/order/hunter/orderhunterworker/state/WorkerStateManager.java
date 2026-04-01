package name.golets.order.hunter.orderhunterworker.state;

import java.util.List;
import java.util.Set;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.Order;

/** Mutable worker runtime state shared by the SQS controller, starter, and flow stages. */
public interface WorkerStateManager {

  /**
   * Reports whether inbound start processing is active.
   *
   * @return whether the starter should subscribe to a flow run on the next tick
   */
  boolean isStarted();

  /**
   * Sets the started flag from transport commands.
   *
   * @param started when false, subsequent ticks skip flow execution
   */
  void setStarted(boolean started);

  /** Returns configured head budget for the current run. */
  int getHeadsToTake();

  /** Sets the head budget from a start command. */
  void setHeadsToTake(int headsToTake);

  /**
   * Returns accumulated heads from successful saves in this session.
   *
   * @return accumulated head count from successfully saved assignments in this session
   */
  int getHeadsTaken();

  /** Replaces the accumulated head count (used when resetting session state). */
  void setHeadsTaken(int headsTaken);

  /** Returns allowed order types for polling. */
  List<OrderType> getOrderTypes();

  /** Stores allowed order types from a start command. */
  void setOrderTypes(List<OrderType> orderTypes);

  /**
   * Returns identifiers for orders already processed in this worker instance.
   *
   * @return order {@code sid} values already processed for deduplication
   */
  Set<String> getTakenOrderSids();

  /**
   * Records a successful save and updates head totals and deduplication keys.
   *
   * <p>Also updates {@link #getHeadsTaken()}.
   */
  void registerSuccessfulSave(Order order);

  /**
   * Returns the current session correlation id.
   *
   * @return correlation id for the current command session
   */
  String getSessionId();

  /** Assigns the session correlation id. */
  void setSessionId(String sessionId);

  /** Returns the configured worker instance id. */
  String getHunterId();

  /** Stores the worker instance id from configuration. */
  void setHunterId(String hunterId);

  /** Builds a snapshot for inbound status commands. */
  WorkerStatusSnapshot toSnapshot();
}
