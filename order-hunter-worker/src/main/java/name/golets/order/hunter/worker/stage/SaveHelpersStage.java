package name.golets.order.hunter.worker.stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.airportal.AirportalClient;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.stage.results.SaveHelpersStageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Parallel PATCH saves for helpers tied to successfully saved main orders.
 *
 * <p>{@link #execute} uses {@link Mono#defer} for the same reason as {@link SaveMainOrdersStage}.
 */
@Component
public class SaveHelpersStage implements Stage<PollOrdersFlowContext> {
  private static final Logger log = LoggerFactory.getLogger(SaveHelpersStage.class);
  private final AirportalClient airportalClient;
  private final int maxParallelOrdersToSaveThreads;

  /**
   * Creates stage dependencies for helper save operations.
   *
   * @param airportalClient outbound Airportal client
   * @param properties worker runtime properties
   */
  public SaveHelpersStage(AirportalClient airportalClient, OrderHunterProperties properties) {
    this.airportalClient = airportalClient;
    this.maxParallelOrdersToSaveThreads =
        Math.max(1, properties.getMaxParallelOrdersToSaveThreads());
  }

  /**
   * Saves helper orders related to filtered main orders in parallel, isolates per-branch failures,
   * and keeps successful saves in result/state.
   *
   * @param context flow session context
   * @return completion after all helper save branches complete
   */
  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.defer(
        () -> {
          SaveHelpersStageResult result = new SaveHelpersStageResult();
          context.setSaveHelpersResult(result);

          if (context.getStateManager() == null) {
            return Mono.empty();
          }
          List<Order> helperOrdersToSave = resolveHelpersToSave(context);
          if (helperOrdersToSave.isEmpty()) {
            log.debug(
                context.getSessionMarker(),
                "saveHelpersStage result stored for flowRunId={} savedCount=0",
                context.getFlowRunId());
            return Mono.empty();
          }

          return Flux.fromIterable(helperOrdersToSave)
              .flatMap(
                  order -> saveOneHelperOrder(context, result, order),
                  maxParallelOrdersToSaveThreads)
              .then(Mono.fromRunnable(() -> logResult(context, result)));
        });
  }

  private Mono<Void> saveOneHelperOrder(
      PollOrdersFlowContext context, SaveHelpersStageResult result, Order order) {
    if (order == null || order.getSid() == null || order.getArtist() == null) {
      log.error(
          context.getSessionMarker(),
          "saveHelpersStage skipped invalid helper for flowRunId={} helperSid={} artistSid={}",
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
                  "saveHelpersStage saved helper for flowRunId={} helperSid={} responseLength={}",
                  context.getFlowRunId(),
                  order.getSid(),
                  responseBody != null ? responseBody.length() : 0);
            })
        .then()
        .onErrorResume(
            error -> {
              log.error(
                  context.getSessionMarker(),
                  "saveHelpersStage failed for flowRunId={} helperSid={}",
                  context.getFlowRunId(),
                  order.getSid(),
                  error);
              return Mono.empty();
            });
  }

  private List<Order> resolveHelpersToSave(PollOrdersFlowContext context) {
    FilterRecordsStageResult filterResult = context.getFilterRecordsResult();
    if (filterResult == null
        || context.getParseOrdersResult() == null
        || context.getParseOrdersResult().getParsedOrders() == null) {
      return List.of();
    }

    ParsedOrders parsedOrders = context.getParseOrdersResult().getParsedOrders();
    Map<String, Map<String, Order>> helpersByMainKey = parsedOrders.getOrdersHelperMapByName();
    if (helpersByMainKey == null || helpersByMainKey.isEmpty()) {
      return List.of();
    }

    // Deduplicate by helper sid in case helper groups are reachable by both sid/name keys.
    Map<String, Order> dedupBySid = new LinkedHashMap<>();
    for (Order filteredOrder : filterResult.getFilteredOrders()) {
      if (filteredOrder == null) {
        continue;
      }
      Stream.of(filteredOrder.getSid(), filteredOrder.getName())
          .filter(key -> key != null && !key.isBlank())
          .map(helpersByMainKey::get)
          .filter(group -> group != null && !group.isEmpty())
          .forEach(group -> group.values().forEach(helper -> putHelper(dedupBySid, helper)));
    }
    return List.copyOf(dedupBySid.values());
  }

  private static void putHelper(Map<String, Order> dedupBySid, Order helper) {
    if (helper == null || helper.getSid() == null) {
      return;
    }
    dedupBySid.putIfAbsent(helper.getSid(), helper);
  }

  private void logResult(PollOrdersFlowContext context, SaveHelpersStageResult result) {
    log.debug(
        context.getSessionMarker(),
        "saveHelpersStage result stored for flowRunId={} savedCount={}",
        context.getFlowRunId(),
        result.getSavedOrders().size());
  }
}
