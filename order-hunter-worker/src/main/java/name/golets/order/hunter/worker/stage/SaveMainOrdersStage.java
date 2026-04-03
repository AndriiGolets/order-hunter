package name.golets.order.hunter.worker.stage;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.flow.FlowObservationContextKeys;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.airportal.AirportalClient;
import name.golets.order.hunter.worker.stage.inputs.SaveMainOrdersStageInput;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.stage.results.SaveMainOrdersStageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Parallel PATCH saves for filtered main orders with bounded concurrency.
 *
 * <p>{@link #execute} is wrapped in {@link Mono#defer} so {@code Mono.then(this::execute)} does not
 * read the context until subscribe time (after prior stages have populated filter results).
 */
@Component
public class SaveMainOrdersStage
    extends AbstractStage<
        PollOrdersFlowContext, SaveMainOrdersStageInput, SaveMainOrdersStageResult> {
  private static final Logger log = LoggerFactory.getLogger(SaveMainOrdersStage.class);
  private final AirportalClient airportalClient;
  private final int maxParallelOrdersToSaveThreads;
  private final int beforeOrderSavingJitterMax;
  private final boolean disableJitterRandomize;

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
    this.beforeOrderSavingJitterMax = Math.max(0, properties.getBeforeOrderSavingJitterMax());
    this.disableJitterRandomize = properties.isDisableJitterRandomize();
  }

  @Override
  protected SaveMainOrdersStageInput prepareInput(PollOrdersFlowContext context) {
    FilterRecordsStageResult filterResult = context.getFilterRecordsResult();
    List<Order> filteredOrders =
        filterResult != null ? filterResult.getFilteredOrders() : List.of();
    return new SaveMainOrdersStageInput(
        context.getStateManager(), context.getSessionMarker(), filteredOrders);
  }

  @Override
  protected Mono<SaveMainOrdersStageResult> process(SaveMainOrdersStageInput input) {
    SaveMainOrdersStageResult result = new SaveMainOrdersStageResult();
    if (input.getStateManager() == null || input.getFilteredOrders().isEmpty()) {
      return Mono.just(result);
    }
    return Flux.fromIterable(input.getFilteredOrders())
        .flatMap(order -> saveOneMainOrder(input, result, order), maxParallelOrdersToSaveThreads)
        .then(Mono.just(result));
  }

  @Override
  protected void storeResult(PollOrdersFlowContext context, SaveMainOrdersStageResult result) {
    context.setSaveMainOrdersResult(result);
  }

  @Override
  protected Marker marker(PollOrdersFlowContext context) {
    return context.getSessionMarker();
  }

  @Override
  protected String stageName() {
    return "saveMainOrdersStage";
  }

  private Mono<Void> saveOneMainOrder(
      SaveMainOrdersStageInput input, SaveMainOrdersStageResult result, Order order) {
    if (order == null || order.getSid() == null || order.getArtist() == null) {
      return Mono.error(
          new IllegalStateException(
              "Invalid order in saveMainOrdersStage orderSid="
                  + (order != null ? order.getSid() : null)
                  + " artistSid="
                  + (order != null ? order.getArtist() : null)));
    }

    int delayMillis = computeBeforeSaveDelayMillis();
    Mono<String> saveOrderMono =
        airportalClient
            .patchOrder(order.getSid(), order.getArtist())
            .contextWrite(
                reactorContext ->
                    reactorContext
                        .put(FlowObservationContextKeys.SAVE_ORDER_KIND, "main")
                        .put(
                            FlowObservationContextKeys.SAVE_PRODUCT_TITLE,
                            sanitizeProductTitle(order.getProductTitle()))
                        .put(
                            FlowObservationContextKeys.SAVE_BEFORE_SAVES_DELAY,
                            Integer.toString(delayMillis)));

    Mono<String> delayedSaveMono =
        delayMillis > 0
            ? Mono.delay(Duration.ofMillis(delayMillis)).then(saveOrderMono)
            : saveOrderMono;

    return delayedSaveMono
        .doOnNext(
            responseBody -> {
              result.addSavedOrder(order);
              input.getStateManager().registerSuccessfulSave(order);
              log.debug(
                  input.getSessionMarker(),
                  "saveMainOrdersStage saved order orderSid={} responseLength={}",
                  order.getSid(),
                  responseBody != null ? responseBody.length() : 0);
            })
        .doOnError(
            error ->
                log.error(
                    input.getSessionMarker(),
                    "saveMainOrdersStage failed orderSid={}",
                    order.getSid(),
                    error))
        .then();
  }

  private int computeBeforeSaveDelayMillis() {
    if (beforeOrderSavingJitterMax == 0) {
      return 0;
    }
    if (disableJitterRandomize) {
      return beforeOrderSavingJitterMax;
    }
    return ThreadLocalRandom.current().nextInt(0, beforeOrderSavingJitterMax + 1);
  }

  private static String sanitizeProductTitle(String productTitle) {
    return productTitle != null && !productTitle.isBlank() ? productTitle : "unknown";
  }
}
