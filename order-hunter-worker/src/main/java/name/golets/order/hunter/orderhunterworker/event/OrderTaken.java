package name.golets.order.hunter.orderhunterworker.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import name.golets.order.hunter.common.model.Order;

/**
 * Outbound SQS event reporting successfully saved orders and completion relative to the head
 * budget.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class OrderTaken {

  private String eventVersion;
  private Instant producedAt;
  private boolean completed;

  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private List<Order> savedOrders = new ArrayList<>();

  public List<Order> getSavedOrders() {
    return List.copyOf(savedOrders);
  }

  public void setSavedOrders(List<Order> savedOrders) {
    this.savedOrders = savedOrders != null ? new ArrayList<>(savedOrders) : new ArrayList<>();
  }
}
