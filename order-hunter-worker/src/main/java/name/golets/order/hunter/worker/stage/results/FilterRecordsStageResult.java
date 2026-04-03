package name.golets.order.hunter.worker.stage.results;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import name.golets.order.hunter.common.flow.StageResult;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.stage.FilterOrdersStage;

/** Orders selected for saving after business filtering rules are applied. */
@Getter
public class FilterRecordsStageResult implements StageResult<FilterOrdersStage> {

  @Getter(AccessLevel.NONE)
  private final List<Order> filteredOrders = new ArrayList<>();

  @Override
  public Class<FilterOrdersStage> stageType() {
    return FilterOrdersStage.class;
  }

  public List<Order> getFilteredOrders() {
    return List.copyOf(filteredOrders);
  }

  /** Adds an order selected for saving. */
  public void addFilteredOrder(Order order) {
    if (order != null) {
      filteredOrders.add(order);
    }
  }
}
