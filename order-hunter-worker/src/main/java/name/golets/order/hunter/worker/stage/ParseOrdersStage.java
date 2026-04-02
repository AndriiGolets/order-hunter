package name.golets.order.hunter.worker.stage;

import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.common.utils.OrderParsingUtil;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.results.ParseOrdersStageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Transforms raw poll response into {@link ParsedOrders} using {@link OrderParsingUtil}. */
@Component
public class ParseOrdersStage implements Stage<PollOrdersFlowContext> {
  private static final Logger log = LoggerFactory.getLogger(ParseOrdersStage.class);

  /**
   * Parses raw records from {@code PollRecordsStageResult} into {@link ParsedOrders} and stores the
   * parsed object in flow context.
   *
   * @param context flow session context
   * @return completion after parsed result is written into context
   */
  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.fromRunnable(
        () -> {
          ParseOrdersStageResult result = new ParseOrdersStageResult();
          OrdersResponse response =
              context.getPollRecordsResult() != null
                  ? context.getPollRecordsResult().getOrdersResponse()
                  : new OrdersResponse();
          ParsedOrders parsedOrders = OrderParsingUtil.parseOrders(response, null);
          result.setParsedOrders(parsedOrders);
          context.setParseOrdersResult(result);

          int mainCount = parsedOrders.getOrdersMapBySid().size();
          int helperGroupsCount = parsedOrders.getOrdersHelperMapByName().size();
          log.debug(
              context.getSessionMarker(),
              "parseOrdersStage result stored for flowRunId={} mainCount={} helperGroupsCount={}",
              context.getFlowRunId(),
              mainCount,
              helperGroupsCount);
        });
  }
}
