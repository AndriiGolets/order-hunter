package name.golets.order.hunter.worker.stage.results;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import name.golets.order.hunter.common.flow.StageResult;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.stage.FilterOrdersStage;
import name.golets.order.hunter.worker.util.JsonUtil;
import name.golets.order.hunter.worker.util.SimplifiedOrder;
import name.golets.order.hunter.worker.util.SimplifiedOrdersMapper;

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

  @Override
  public String toString() {
    int filteredCount = filteredOrders.size();
    int filteredHeads =
        filteredOrders.stream().mapToInt(order -> Math.max(0, order.getHeads())).sum();
    return JsonUtil.toOneLineJson(
        new ResultLogPayload(
            filteredCount, filteredHeads, SimplifiedOrdersMapper.map(filteredOrders)));
  }

  private record ResultLogPayload(
      int filteredCount, int filteredHeads, List<SimplifiedOrder> filteredOrders) {}
}
