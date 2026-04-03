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

/** Input required to save helper orders related to selected mains. */
@Data
@AllArgsConstructor
public class SaveHelpersStageInput implements StageInput {
  private final WorkerStateManager stateManager;
  private final Marker sessionMarker;
  private final List<Order> helperOrdersToSave;

  @Override
  public String toString() {
    int helperCount = helperOrdersToSave != null ? helperOrdersToSave.size() : 0;
    return JsonUtil.toOneLineJson(
        new InputLogPayload(helperCount, SimplifiedOrdersMapper.map(helperOrdersToSave)));
  }

  private record InputLogPayload(int helperCount, List<SimplifiedOrder> helperOrders) {}
}
