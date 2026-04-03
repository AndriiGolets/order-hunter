package name.golets.order.hunter.worker.stage.inputs;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import name.golets.order.hunter.common.flow.StageInput;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import name.golets.order.hunter.worker.util.JsonUtil;
import name.golets.order.hunter.worker.util.SimplifiedOrder;
import name.golets.order.hunter.worker.util.SimplifiedOrdersMapper;
import org.slf4j.Marker;

/** Input required to save filtered main orders. */
@Data
@AllArgsConstructor
public class SaveMainOrdersStageInput implements StageInput {
  private final WorkerStateManager stateManager;
  private final Marker sessionMarker;
  private final List<Order> filteredOrders;

  @Override
  public String toString() {
    int filteredCount = filteredOrders != null ? filteredOrders.size() : 0;
    int filteredHeads =
        filteredOrders != null
            ? filteredOrders.stream().mapToInt(order -> Math.max(0, order.getHeads())).sum()
            : 0;
    return JsonUtil.toOneLineJson(
        new InputLogPayload(
            filteredCount, filteredHeads, SimplifiedOrdersMapper.map(filteredOrders)));
  }

  private record InputLogPayload(
      int filteredCount, int filteredHeads, List<SimplifiedOrder> filteredOrders) {}
}
