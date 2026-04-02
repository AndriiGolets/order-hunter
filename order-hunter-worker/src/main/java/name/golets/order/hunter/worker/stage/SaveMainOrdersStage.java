package name.golets.order.hunter.worker.stage;

import java.util.List;
import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.airportal.AirportalClient;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.stage.results.SaveMainOrdersStageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Parallel PATCH saves for filtered main orders with bounded concurrency. */
@Component
public class SaveMainOrdersStage implements Stage<PollOrdersFlowContext> {
  private static final Logger log = LoggerFactory.getLogger(SaveMainOrdersStage.class);
  private final AirportalClient airportalClient;
  private final int maxParallelOrdersToSaveThreads;

  /**
   * Creates stage dependencies for parallel main-order save operations.
   *
   * @param airportalClient outbound Airportal client
   * @param properties worker runtime properties
   */
  public SaveMainOrdersStage(AirportalClient airportalClient, OrderHunterProperties properties) {
    this.airportalClient = airportalClient;
    this.maxParallelOrdersToSaveThreads =
        Math.max(1, properties.getMaxParallelOrdersToSaveThreads());
  }

  /**
   * Saves filtered main orders in parallel. Failures of individual orders are logged and skipped,
   * while successful saves are reflected in stage result and state manager.
   *
   * @param context flow session context
   * @return completion after all save branches complete
   */
  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    SaveMainOrdersStageResult result = new SaveMainOrdersStageResult();
    context.setSaveMainOrdersResult(result);

    if (context.getStateManager() == null) {
      return Mono.empty();
    }
    List<Order> filteredOrders = readFilteredOrders(context);
    if (filteredOrders.isEmpty()) {
      log.debug(
          context.getSessionMarker(),
          "saveMainOrdersStage result stored for flowRunId={} savedCount=0",
          context.getFlowRunId());
      return Mono.empty();
    }

    return Flux.fromIterable(filteredOrders)
        .flatMap(order -> saveOneMainOrder(context, result, order), maxParallelOrdersToSaveThreads)
        .then(Mono.fromRunnable(() -> logResult(context, result)));
  }

  private Mono<Void> saveOneMainOrder(
      PollOrdersFlowContext context, SaveMainOrdersStageResult result, Order order) {
    if (order == null || order.getSid() == null || order.getArtist() == null) {
      log.error(
          context.getSessionMarker(),
          "saveMainOrdersStage skipped invalid order for flowRunId={} orderSid={} artistSid={}",
          context.getFlowRunId(),
          order != null ? order.getSid() : null,
          order != null ? order.getArtist() : null);
      return Mono.empty();
    }

    return airportalClient
        .patchOrder(order.getSid(), order.getArtist())
        .doOnNext(
            responseBody -> {
              result.addSavedOrder(order);
              context.getStateManager().registerSuccessfulSave(order);
              log.debug(
                  context.getSessionMarker(),
                  "saveMainOrdersStage saved order for flowRunId={} orderSid={} responseLength={}",
                  context.getFlowRunId(),
                  order.getSid(),
                  responseBody != null ? responseBody.length() : 0);
            })
        .then()
        .onErrorResume(
            error -> {
              log.error(
                  context.getSessionMarker(),
                  "saveMainOrdersStage failed for flowRunId={} orderSid={}",
                  context.getFlowRunId(),
                  order.getSid(),
                  error);
              return Mono.empty();
            });
  }

  private List<Order> readFilteredOrders(PollOrdersFlowContext context) {
    FilterRecordsStageResult filterResult = context.getFilterRecordsResult();
    if (filterResult == null) {
      return List.of();
    }
    return filterResult.getFilteredOrders();
  }

  private void logResult(PollOrdersFlowContext context, SaveMainOrdersStageResult result) {
    log.debug(
        context.getSessionMarker(),
        "saveMainOrdersStage result stored for flowRunId={} savedCount={}",
        context.getFlowRunId(),
        result.getSavedOrders().size());
  }
}
