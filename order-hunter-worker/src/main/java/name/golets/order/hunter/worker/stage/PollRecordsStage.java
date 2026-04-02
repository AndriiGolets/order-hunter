package name.golets.order.hunter.worker.stage;

import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.worker.config.CombinedOrdersPollPath;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.airportal.AirportalClient;
import name.golets.order.hunter.worker.stage.results.PollRecordsStageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Fetches {@link OrdersResponse} from the configured poll API. */
@Component
public class PollRecordsStage implements Stage<PollOrdersFlowContext> {
  private static final Logger log = LoggerFactory.getLogger(PollRecordsStage.class);
  private final AirportalClient airportalClient;
  private final CombinedOrdersPollPath combinedOrdersPollPath;

  /**
   * Creates poll stage dependencies.
   *
   * @param airportalClient outbound airportal API client
   * @param combinedOrdersPollPath merged poll path from worker configuration
   */
  public PollRecordsStage(
      AirportalClient airportalClient, CombinedOrdersPollPath combinedOrdersPollPath) {
    this.airportalClient = airportalClient;
    this.combinedOrdersPollPath = combinedOrdersPollPath;
  }

  /**
   * Executes one poll call and stores response in flow context.
   *
   * <p>On poll failure, no retry is attempted in this stage and the error is propagated to flow
   * error handling.
   *
   * @param context flow session context
   * @return completion after context receives stage result
   */
  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return airportalClient
        .pollOrders(combinedOrdersPollPath.pathAndQuery())
        .defaultIfEmpty(new OrdersResponse())
        .map(
            response -> {
              PollRecordsStageResult result = new PollRecordsStageResult();
              result.setOrdersResponse(response);
              return result;
            })
        .doOnNext(
            result -> {
              context.setPollRecordsResult(result);
              int recordsCount =
                  result.getOrdersResponse() != null
                          && result.getOrdersResponse().getRecords() != null
                      ? result.getOrdersResponse().getRecords().size()
                      : 0;
              log.debug(
                  context.getSessionMarker(),
                  "pollRecordsStage result stored for flowRunId={} recordsCount={}",
                  context.getFlowRunId(),
                  recordsCount);
            })
        .then();
  }
}
