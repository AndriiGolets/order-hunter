package name.golets.order.hunter.worker.stage.inputs;

import lombok.AllArgsConstructor;
import lombok.Data;
import name.golets.order.hunter.common.flow.StageInput;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.util.JsonUtil;

/** Input carrying parsed orders and flow context for statistics emission. */
@Data
@AllArgsConstructor
public class StatisticStageInput implements StageInput {
  private final PollOrdersFlowContext context;
  private final ParsedOrders parsedOrders;

  @Override
  public String toString() {
    int mainCount = parsedOrders != null ? parsedOrders.getOrdersMapBySid().size() : 0;
    int helperGroupsCount =
        parsedOrders != null ? parsedOrders.getOrdersHelperMapByName().size() : 0;
    return JsonUtil.toOneLineJson(new InputLogPayload(mainCount, helperGroupsCount));
  }

  private record InputLogPayload(int mainCount, int helperGroupsCount) {}
}
