package name.golets.order.hunter.orderhunterworker.stage.results;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import name.golets.order.hunter.common.flow.StageResult;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.orderhunterworker.stage.ParseOrdersStage;

/** Parsed orders produced by {@link ParseOrdersStage}. */
@Getter
public class ParseOrdersStageResult implements StageResult<ParseOrdersStage> {

  @Getter(AccessLevel.NONE)
  private final List<Order> orders = new ArrayList<>();

  @Override
  public Class<ParseOrdersStage> stageType() {
    return ParseOrdersStage.class;
  }

  public List<Order> getOrders() {
    return List.copyOf(orders);
  }

  /** Appends a parsed order for filtering. */
  public void addOrder(Order order) {
    if (order != null) {
      orders.add(order);
    }
  }
}
