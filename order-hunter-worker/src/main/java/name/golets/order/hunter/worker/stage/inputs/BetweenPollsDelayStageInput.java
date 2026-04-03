package name.golets.order.hunter.worker.stage.inputs;

import lombok.AllArgsConstructor;
import lombok.Data;
import name.golets.order.hunter.common.flow.StageInput;
import name.golets.order.hunter.worker.util.JsonUtil;

/** Input controlling whether delay should be applied between polling cycles. */
@Data
@AllArgsConstructor
public class BetweenPollsDelayStageInput implements StageInput {
  private final int filteredOrdersCount;

  @Override
  public String toString() {
    return JsonUtil.toOneLineJson(this);
  }
}
