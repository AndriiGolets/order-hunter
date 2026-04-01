package name.golets.order.hunter.orderhunterworker.stage;

import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.common.model.Record;
import name.golets.order.hunter.orderhunterworker.config.CombinedOrdersPollPath;
import name.golets.order.hunter.orderhunterworker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.orderhunterworker.integration.airportal.AirportalClient;
import name.golets.order.hunter.orderhunterworker.stage.results.PollRecordsStageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Fetches {@link name.golets.order.hunter.common.model.Record} rows from the configured poll API.
 */
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
   * Executes one poll call and stores records in flow context.
   *
   * <p>On poll failure, no retry is attempted in this stage. A warning is logged, an empty stage
   * result is stored, and the flow continues.
   *
   * @param context flow session context
   * @return completion after context receives stage result
   */
  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return airportalClient
        .pollOrders(combinedOrdersPollPath.pathAndQuery())
        .defaultIfEmpty(new OrdersResponse())
        .map(this::toStageResult)
        .onErrorResume(
            error -> {
              log.warn(
                  "Poll request failed for flowRunId={}, continuing without records.",
                  context.getFlowRunId(),
                  error);
              return Mono.just(new PollRecordsStageResult());
            })
        .doOnNext(context::setPollRecordsResult)
        .then();
  }

  private PollRecordsStageResult toStageResult(OrdersResponse response) {
    PollRecordsStageResult result = new PollRecordsStageResult();
    if (response == null || response.getRecords() == null) {
      return result;
    }
    for (Record record : response.getRecords()) {
      result.addRecord(record);
    }
    return result;
  }
}
