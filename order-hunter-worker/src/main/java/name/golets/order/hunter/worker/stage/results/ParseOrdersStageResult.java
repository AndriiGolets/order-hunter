package name.golets.order.hunter.worker.stage.results;

import lombok.Getter;
import lombok.Setter;
import name.golets.order.hunter.common.flow.StageResult;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.worker.stage.ParseOrdersStage;

/** Parsed orders produced by {@link ParseOrdersStage}. */
@Getter
@Setter
public class ParseOrdersStageResult implements StageResult<ParseOrdersStage> {

  private ParsedOrders parsedOrders = new ParsedOrders();

  @Override
  public Class<ParseOrdersStage> stageType() {
    return ParseOrdersStage.class;
  }
}
