package name.golets.order.hunter.orderhunterworker.state;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import name.golets.order.hunter.common.enums.OrderType;

/** Read-only view of worker state returned for status queries. */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class WorkerStatusSnapshot {

  private boolean started;
  private int headsToTake;
  private int headsTaken;
  private String sessionId;
  private String hunterId;
  private Instant lastFlowStartedAt;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private List<OrderType> orderTypes = new ArrayList<>();

  public List<OrderType> getOrderTypes() {
    return List.copyOf(orderTypes);
  }

  public void setOrderTypes(List<OrderType> orderTypes) {
    this.orderTypes = orderTypes != null ? new ArrayList<>(orderTypes) : new ArrayList<>();
  }
}
