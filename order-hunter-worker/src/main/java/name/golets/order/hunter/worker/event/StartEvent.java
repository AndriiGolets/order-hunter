package name.golets.order.hunter.worker.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import name.golets.order.hunter.common.enums.OrderType;

/** Inbound SQS command that activates polling with a head budget and allowed order types. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class StartEvent {

  private String eventVersion;
  private Instant producedAt;
  private int headsToTake;

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
