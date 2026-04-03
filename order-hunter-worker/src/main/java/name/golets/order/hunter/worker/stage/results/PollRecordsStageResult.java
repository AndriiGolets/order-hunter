package name.golets.order.hunter.worker.stage.results;

import lombok.Getter;
import lombok.Setter;
import name.golets.order.hunter.common.flow.StageResult;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.worker.stage.PollRecordsStage;
import name.golets.order.hunter.worker.util.JsonUtil;

/** Raw poll payload produced by {@link PollRecordsStage}. */
@Getter
@Setter
public class PollRecordsStageResult implements StageResult<PollRecordsStage> {

  private OrdersResponse ordersResponse = new OrdersResponse();

  @Override
  public Class<PollRecordsStage> stageType() {
    return PollRecordsStage.class;
  }

  @Override
  public String toString() {
    int recordsCount =
        ordersResponse != null && ordersResponse.getRecords() != null
            ? ordersResponse.getRecords().size()
            : 0;
    return JsonUtil.toOneLineJson(new ResultLogPayload(recordsCount));
  }

  private record ResultLogPayload(int recordsCount) {}
}
