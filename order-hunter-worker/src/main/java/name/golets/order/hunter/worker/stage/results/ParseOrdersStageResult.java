package name.golets.order.hunter.worker.stage.results;

import lombok.Getter;
import lombok.Setter;
import name.golets.order.hunter.common.flow.StageResult;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.worker.stage.ParseOrdersStage;
import name.golets.order.hunter.worker.util.JsonUtil;

/** Parsed orders produced by {@link ParseOrdersStage}. */
@Getter
@Setter
public class ParseOrdersStageResult implements StageResult<ParseOrdersStage> {

  private ParsedOrders parsedOrders = new ParsedOrders();

  @Override
  public Class<ParseOrdersStage> stageType() {
    return ParseOrdersStage.class;
  }

  @Override
  public String toString() {
    int mainCount = parsedOrders != null ? parsedOrders.getOrdersMapBySid().size() : 0;
    int helperGroupsCount =
        parsedOrders != null ? parsedOrders.getOrdersHelperMapByName().size() : 0;
    return JsonUtil.toOneLineJson(new ResultLogPayload(mainCount, helperGroupsCount));
  }

  private record ResultLogPayload(int mainCount, int helperGroupsCount) {}
}
