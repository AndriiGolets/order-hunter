package name.golets.order.hunter.worker.stage.inputs;

import lombok.AllArgsConstructor;
import lombok.Data;
import name.golets.order.hunter.common.flow.StageInput;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.worker.util.JsonUtil;

/** Input containing raw orders response that should be parsed. */
@Data
@AllArgsConstructor
public class ParseOrdersStageInput implements StageInput {
  private final OrdersResponse ordersResponse;

  @Override
  public String toString() {
    int recordsCount =
        ordersResponse != null && ordersResponse.getRecords() != null
            ? ordersResponse.getRecords().size()
            : 0;
    return JsonUtil.toOneLineJson(new InputLogPayload(recordsCount));
  }

  private record InputLogPayload(int recordsCount) {}
}
