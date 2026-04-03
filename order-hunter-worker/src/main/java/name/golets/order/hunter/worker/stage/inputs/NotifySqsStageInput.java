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

/** Input for outbound SQS notification step. */
@Data
@AllArgsConstructor
public class NotifySqsStageInput implements StageInput {
  private final WorkerStateManager stateManager;
  private final Marker sessionMarker;
  private final List<Order> savedMainOrders;

  @Override
  public String toString() {
    int savedMainCount = savedMainOrders != null ? savedMainOrders.size() : 0;
    return JsonUtil.toOneLineJson(
        new InputLogPayload(savedMainCount, SimplifiedOrdersMapper.map(savedMainOrders)));
  }

  private record InputLogPayload(int savedMainCount, List<SimplifiedOrder> savedMainOrders) {}
}
