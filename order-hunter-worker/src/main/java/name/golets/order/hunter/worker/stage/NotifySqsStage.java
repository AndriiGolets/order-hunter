package name.golets.order.hunter.worker.stage;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.event.OrderTaken;
import name.golets.order.hunter.worker.flow.FlowObservationContextKeys;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.sqs.OrderTakenSqsPublisher;
import name.golets.order.hunter.worker.stage.inputs.NotifySqsStageInput;
import name.golets.order.hunter.worker.stage.results.SaveMainOrdersStageResult;
import name.golets.order.hunter.worker.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Publishes {@link name.golets.order.hunter.worker.event.OrderTaken} to the outbound queue.
 *
 * <p>{@link #execute} uses {@link Mono#defer} so the event is built after save stages complete when
 * used in a {@code Mono.then} chain.
 */
@Component
public class NotifySqsStage
    extends AbstractStage<
        PollOrdersFlowContext, NotifySqsStageInput, NotifySqsStage.NotifySqsStageResult> {
  private static final Logger log = LoggerFactory.getLogger(NotifySqsStage.class);
  private static final String EVENT_VERSION = "1.0";
  private static final int RETRY_ATTEMPTS = 3;
  private final OrderTakenSqsPublisher orderTakenSqsPublisher;
  private final ObservationRegistry observationRegistry;

  /**
   * Creates stage dependencies for outbound notification.
   *
   * @param orderTakenSqsPublisher SQS publisher for OrderTaken events
   */
  public NotifySqsStage(
      OrderTakenSqsPublisher orderTakenSqsPublisher, ObservationRegistry observationRegistry) {
    this.orderTakenSqsPublisher = orderTakenSqsPublisher;
    this.observationRegistry = observationRegistry;
  }

  @Override
  protected NotifySqsStageInput prepareInput(PollOrdersFlowContext context) {
    SaveMainOrdersStageResult mainResult = context.getSaveMainOrdersResult();
    List<Order> savedMainOrders = mainResult != null ? mainResult.getSavedOrders() : List.of();
    return new NotifySqsStageInput(
        context.getStateManager(), context.getSessionMarker(), savedMainOrders);
  }

  @Override
  protected Mono<NotifySqsStageResult> process(NotifySqsStageInput input) {
    return Mono.deferContextual(
        contextView -> {
          if (input.getStateManager() == null) {
            return Mono.just(new NotifySqsStageResult(false, false, 0));
          }
          reconcileHeadsConsistency(input);
          OrderTaken event = buildOrderTakenEvent(input);
          if (event.getSavedOrders().isEmpty()) {
            log.debug(
                input.getSessionMarker(),
                "notifySqsStage skipped publish because no orders were saved");
            return Mono.just(new NotifySqsStageResult(false, event.isCompleted(), 0));
          }
          Observation observation =
              Observation.createNotStarted(
                      "order-hunter.sqs.publish.orderTaken", observationRegistry)
                  .lowCardinalityKeyValue("operation", "publishOrderTaken")
                  .highCardinalityKeyValue(
                      "orderTaken", JsonUtil.toOrderTakenObservationJson(event));
          if (contextView.hasKey(FlowObservationContextKeys.FLOW_OBSERVATION)) {
            Object parent = contextView.get(FlowObservationContextKeys.FLOW_OBSERVATION);
            if (parent instanceof Observation parentObservation) {
              observation.parentObservation(parentObservation);
            }
          }
          observation.start();
          return orderTakenSqsPublisher
              .publish(event)
              .retryWhen(
                  Retry.backoff(RETRY_ATTEMPTS, Duration.ofMillis(300))
                      .maxBackoff(Duration.ofSeconds(2))
                      .doBeforeRetry(
                          signal ->
                              log.warn(
                                  input.getSessionMarker(),
                                  "notifySqsStage publish retry={}",
                                  signal.totalRetries() + 1,
                                  signal.failure())))
              .doOnSuccess(ignored -> onPublished(input, event))
              .thenReturn(
                  new NotifySqsStageResult(
                      true, event.isCompleted(), event.getSavedOrders().size()))
              .doOnError(observation::error)
              .doFinally(signalType -> observation.stop());
        });
  }

  @Override
  protected void storeResult(PollOrdersFlowContext context, NotifySqsStageResult result) {
    // Notify stage does not store dedicated output in flow context.
  }

  @Override
  protected Marker marker(PollOrdersFlowContext context) {
    return context.getSessionMarker();
  }

  @Override
  protected String stageName() {
    return "notifySqsStage";
  }

  private OrderTaken buildOrderTakenEvent(NotifySqsStageInput input) {
    OrderTaken event = new OrderTaken();
    event.setEventVersion(EVENT_VERSION);
    event.setProducedAt(Instant.now());
    event.setSavedOrders(input.getSavedMainOrders());
    boolean completed =
        input.getStateManager().getHeadsTaken() >= input.getStateManager().getHeadsToTake();
    event.setCompleted(completed);
    return event;
  }

  private void onPublished(NotifySqsStageInput input, OrderTaken event) {
    log.info(
        input.getSessionMarker(),
        "notifySqsStage sent OrderTaken ordersSent={}",
        event.getSavedOrders().size());

    if (event.isCompleted()) {
      input.getStateManager().setStarted(false);
      log.info(
          input.getSessionMarker(),
          "notifySqsStage task completed headsTaken={} headsToTake={}",
          input.getStateManager().getHeadsTaken(),
          input.getStateManager().getHeadsToTake());
      return;
    }
    log.info(
        input.getSessionMarker(),
        "notifySqsStage task not completed headsTaken={} headsToTake={}",
        input.getStateManager().getHeadsTaken(),
        input.getStateManager().getHeadsToTake());
  }

  private void reconcileHeadsConsistency(NotifySqsStageInput input) {
    int headsFromSavedStages = 0;
    headsFromSavedStages += heads(input.getSavedMainOrders());

    int currentHeadsTaken = input.getStateManager().getHeadsTaken();
    if (headsFromSavedStages > currentHeadsTaken) {
      input.getStateManager().setHeadsTaken(headsFromSavedStages);
      log.warn(
          input.getSessionMarker(),
          "notifySqsStage reconciled headsTaken from {} to {}",
          currentHeadsTaken,
          headsFromSavedStages);
    }
  }

  private int heads(List<Order> orders) {
    if (orders == null) {
      return 0;
    }
    return orders.stream().mapToInt(order -> Math.max(0, order.getHeads())).sum();
  }

  record NotifySqsStageResult(boolean published, boolean completed, int ordersSent) {
    @Override
    public String toString() {
      return JsonUtil.toOneLineJson(this);
    }
  }
}
