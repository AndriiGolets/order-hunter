package name.golets.order.hunter.worker.stage.inputs;

import lombok.AllArgsConstructor;
import lombok.Data;
import name.golets.order.hunter.common.flow.StageInput;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import name.golets.order.hunter.worker.util.JsonUtil;

/** Input required to filter parsed orders against current worker state. */
@Data
@AllArgsConstructor
public class FilterOrdersStageInput implements StageInput {
  private final WorkerStateManager stateManager;
  private final ParsedOrders parsedOrders;

  @Override
  public String toString() {
    int headsToTake = stateManager != null ? Math.max(0, stateManager.getHeadsToTake()) : 0;
    int savedSidsCount = stateManager != null ? stateManager.getSavedOrderSids().size() : 0;
    int parsedMainCount = parsedOrders != null ? parsedOrders.getOrdersMapBySid().size() : 0;
    return JsonUtil.toOneLineJson(
        new InputLogPayload(headsToTake, savedSidsCount, parsedMainCount));
  }

  private record InputLogPayload(int headsToTake, int savedSidsCount, int parsedMainCount) {}
}
