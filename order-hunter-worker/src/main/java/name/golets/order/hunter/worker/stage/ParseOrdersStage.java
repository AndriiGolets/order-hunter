package name.golets.order.hunter.worker.stage;

import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.common.utils.OrderParsingUtil;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.inputs.ParseOrdersStageInput;
import name.golets.order.hunter.worker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.worker.stage.results.PollRecordsStageResult;
import org.slf4j.Marker;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Transforms raw poll response into {@link ParsedOrders} using {@link OrderParsingUtil}. */
@Component
public class ParseOrdersStage
    extends AbstractStage<PollOrdersFlowContext, ParseOrdersStageInput, ParseOrdersStageResult> {

  /**
   * Parses raw records from {@code PollRecordsStageResult} into {@link ParsedOrders} and stores the
   * parsed object in flow context.
   *
   * @param context flow session context
   * @return completion after parsed result is written into context
   */
  @Override
  protected ParseOrdersStageInput prepareInput(PollOrdersFlowContext context) {
    PollRecordsStageResult pollResult = context.getPollRecordsResult();
    OrdersResponse response =
        pollResult != null ? pollResult.getOrdersResponse() : new OrdersResponse();
    return new ParseOrdersStageInput(response);
  }

  @Override
  protected Mono<ParseOrdersStageResult> process(ParseOrdersStageInput input) {
    return Mono.fromSupplier(
        () -> {
          ParseOrdersStageResult result = new ParseOrdersStageResult();
          ParsedOrders parsedOrders = OrderParsingUtil.parseOrders(input.getOrdersResponse(), null);
          result.setParsedOrders(parsedOrders);
          return result;
        });
  }

  @Override
  protected void storeResult(PollOrdersFlowContext context, ParseOrdersStageResult result) {
    context.setParseOrdersResult(result);
  }

  @Override
  protected Marker marker(PollOrdersFlowContext context) {
    return context.getSessionMarker();
  }

  @Override
  protected String stageName() {
    return "parseOrdersStage";
  }
}
