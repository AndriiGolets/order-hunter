package name.golets.order.hunter.worker.stage.inputs;

import lombok.AllArgsConstructor;
import lombok.Data;
import name.golets.order.hunter.common.flow.StageInput;
import name.golets.order.hunter.worker.util.JsonUtil;

/** Input for polling raw order records from the external API. */
@Data
@AllArgsConstructor
public class PollRecordsStageInput implements StageInput {
  private final String pollPathAndQuery;

  @Override
  public String toString() {
    return JsonUtil.toOneLineJson(this);
  }
}
