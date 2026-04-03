package name.golets.order.hunter.worker.stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.flow.FlowObservationContextKeys;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.airportal.AirportalClient;
import name.golets.order.hunter.worker.stage.inputs.SaveHelpersStageInput;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.worker.stage.results.SaveHelpersStageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Parallel PATCH saves for helpers tied to successfully saved main orders.
 *
 * <p>{@link #execute} uses {@link Mono#defer} for the same reason as {@link SaveMainOrdersStage}.
 */
@Component
public class SaveHelpersStage
    extends AbstractStage<PollOrdersFlowContext, SaveHelpersStageInput, SaveHelpersStageResult> {
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

  @Override
  protected SaveHelpersStageInput prepareInput(PollOrdersFlowContext context) {
    ParseOrdersStageResult parseResult = context.getParseOrdersResult();
    ParsedOrders parsedOrders = parseResult != null ? parseResult.getParsedOrders() : null;
    List<Order> helperOrdersToSave =
        resolveHelpersToSave(context.getFilterRecordsResult(), parsedOrders);
    return new SaveHelpersStageInput(
        context.getStateManager(), context.getSessionMarker(), helperOrdersToSave);
  }

  @Override
  protected Mono<SaveHelpersStageResult> process(SaveHelpersStageInput input) {
    SaveHelpersStageResult result = new SaveHelpersStageResult();
    if (input.getStateManager() == null || input.getHelperOrdersToSave().isEmpty()) {
      return Mono.just(result);
    }
    return Flux.fromIterable(input.getHelperOrdersToSave())
        .flatMap(order -> saveOneHelperOrder(input, result, order), maxParallelOrdersToSaveThreads)
        .then(Mono.just(result));
  }

  @Override
  protected void storeResult(PollOrdersFlowContext context, SaveHelpersStageResult result) {
    context.setSaveHelpersResult(result);
  }

  @Override
  protected Marker marker(PollOrdersFlowContext context) {
    return context.getSessionMarker();
  }

  @Override
  protected String stageName() {
    return "saveHelpersStage";
  }

  private Mono<Void> saveOneHelperOrder(
      SaveHelpersStageInput input, SaveHelpersStageResult result, Order order) {
    if (order == null || order.getSid() == null || order.getArtist() == null) {
      return Mono.error(
          new IllegalStateException(
              "Invalid helper in saveHelpersStage helperSid="
                  + (order != null ? order.getSid() : null)
                  + " artistSid="
                  + (order != null ? order.getArtist() : null)));
    }

    return airportalClient
        .patchOrder(order.getSid(), order.getArtist())
        .contextWrite(
            reactorContext ->
                reactorContext
                    .put(FlowObservationContextKeys.SAVE_ORDER_KIND, "helper")
                    .put(
                        FlowObservationContextKeys.SAVE_PRODUCT_TITLE,
                        sanitizeProductTitle(order.getProductTitle())))
        .doOnNext(
            responseBody -> {
              result.addSavedOrder(order);
              input.getStateManager().registerSuccessfulSave(order);
              log.debug(
                  input.getSessionMarker(),
                  "saveHelpersStage saved helper helperSid={} responseLength={}",
                  order.getSid(),
                  responseBody != null ? responseBody.length() : 0);
            })
        .doOnError(
            error ->
                log.error(
                    input.getSessionMarker(),
                    "saveHelpersStage failed helperSid={}",
                    order.getSid(),
                    error))
        .then();
  }

  private static String sanitizeProductTitle(String productTitle) {
    return productTitle != null && !productTitle.isBlank() ? productTitle : "unknown";
  }

  private List<Order> resolveHelpersToSave(
      FilterRecordsStageResult filterResult, ParsedOrders parsedOrders) {
    if (filterResult == null || parsedOrders == null) {
      return List.of();
    }

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
}
