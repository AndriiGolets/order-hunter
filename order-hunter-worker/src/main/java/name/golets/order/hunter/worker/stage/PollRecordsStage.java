package name.golets.order.hunter.worker.stage;

import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.worker.config.CombinedOrdersPollPath;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.airportal.AirportalClient;
import name.golets.order.hunter.worker.stage.inputs.PollRecordsStageInput;
import name.golets.order.hunter.worker.stage.results.PollRecordsStageResult;
import org.slf4j.Marker;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Fetches {@link OrdersResponse} from the configured poll API. */
@Component
public class PollRecordsStage
    extends AbstractStage<PollOrdersFlowContext, PollRecordsStageInput, PollRecordsStageResult> {
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
  protected PollRecordsStageInput prepareInput(PollOrdersFlowContext context) {
    return new PollRecordsStageInput(combinedOrdersPollPath.pathAndQuery());
  }

  @Override
  protected Mono<PollRecordsStageResult> process(PollRecordsStageInput input) {
    return airportalClient
        .pollOrders(input.getPollPathAndQuery())
        .defaultIfEmpty(new OrdersResponse())
        .map(
            response -> {
              PollRecordsStageResult result = new PollRecordsStageResult();
              result.setOrdersResponse(response);
              return result;
            });
  }

  @Override
  protected void storeResult(PollOrdersFlowContext context, PollRecordsStageResult result) {
    context.setPollRecordsResult(result);
  }

  @Override
  protected Marker marker(PollOrdersFlowContext context) {
    return context.getSessionMarker();
  }

  @Override
  protected String stageName() {
    return "pollRecordsStage";
  }
}
