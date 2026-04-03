package name.golets.order.hunter.worker.stage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.event.OrderTaken;
import name.golets.order.hunter.worker.flow.FlowObservationContextKeys;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.sqs.OrderTakenSqsPublisher;
import name.golets.order.hunter.worker.stage.results.SaveMainOrdersStageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class NotifySqsStage implements Stage<PollOrdersFlowContext> {
  private static final Logger log = LoggerFactory.getLogger(NotifySqsStage.class);
  private static final String EVENT_VERSION = "1.0";
  private static final int RETRY_ATTEMPTS = 3;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
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

  /**
   * Publishes OrderTaken event based on successful save results, updates completion flags in state,
   * and retries SQS publish failures.
   *
   * <p>If no orders were saved in the current flow cycle, no outbound event is sent.
   *
   * @param context flow session context
   * @return completion when event is published and state is adjusted
   */
  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.deferContextual(
        contextView -> {
          if (context.getStateManager() == null) {
            return Mono.empty();
          }
          reconcileHeadsConsistency(context);
          OrderTaken event = buildOrderTakenEvent(context);
          if (event.getSavedOrders().isEmpty()) {
            log.debug(
                context.getSessionMarker(),
                "notifySqsStage skipped publish for flowRunId={} because no orders were saved",
                context.getFlowRunId());
            return Mono.empty();
          }
          Observation observation =
              Observation.createNotStarted(
                      "order-hunter.sqs.publish.orderTaken", observationRegistry)
                  .lowCardinalityKeyValue("operation", "publishOrderTaken")
                  .highCardinalityKeyValue("orderTaken", toJson(event));
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
                                  context.getSessionMarker(),
                                  "notifySqsStage publish retry={} flowRunId={}",
                                  signal.totalRetries() + 1,
                                  context.getFlowRunId(),
                                  signal.failure())))
              .doOnSuccess(ignored -> onPublished(context, event))
              .doOnError(observation::error)
              .doFinally(signalType -> observation.stop());
        });
  }

  private static String toJson(OrderTaken event) {
    try {
      return OBJECT_MAPPER.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  private OrderTaken buildOrderTakenEvent(PollOrdersFlowContext context) {
    OrderTaken event = new OrderTaken();
    event.setEventVersion(EVENT_VERSION);
    event.setProducedAt(Instant.now());
    event.setSavedOrders(readSavedMainOrders(context));
    boolean completed =
        context.getStateManager().getHeadsTaken() >= context.getStateManager().getHeadsToTake();
    event.setCompleted(completed);
    return event;
  }

  private List<Order> readSavedMainOrders(PollOrdersFlowContext context) {
    SaveMainOrdersStageResult result = context.getSaveMainOrdersResult();
    if (result == null) {
      return List.of();
    }
    return result.getSavedOrders();
  }

  private void onPublished(PollOrdersFlowContext context, OrderTaken event) {
    log.info(
        context.getSessionMarker(),
        "notifySqsStage sent OrderTaken for flowRunId={} ordersSent={}",
        context.getFlowRunId(),
        event.getSavedOrders().size());

    if (event.isCompleted()) {
      context.getStateManager().setStarted(false);
      log.info(
          context.getSessionMarker(),
          "notifySqsStage task completed for flowRunId={} headsTaken={} headsToTake={}",
          context.getFlowRunId(),
          context.getStateManager().getHeadsTaken(),
          context.getStateManager().getHeadsToTake());
      return;
    }
    log.info(
        context.getSessionMarker(),
        "notifySqsStage task not completed for flowRunId={} headsTaken={} headsToTake={}",
        context.getFlowRunId(),
        context.getStateManager().getHeadsTaken(),
        context.getStateManager().getHeadsToTake());
  }

  private void reconcileHeadsConsistency(PollOrdersFlowContext context) {
    int headsFromSavedStages = 0;
    SaveMainOrdersStageResult mainResult = context.getSaveMainOrdersResult();
    if (mainResult != null) {
      headsFromSavedStages += heads(mainResult.getSavedOrders());
    }

    int currentHeadsTaken = context.getStateManager().getHeadsTaken();
    if (headsFromSavedStages > currentHeadsTaken) {
      context.getStateManager().setHeadsTaken(headsFromSavedStages);
      log.warn(
          context.getSessionMarker(),
          "notifySqsStage reconciled headsTaken for flowRunId={} from {} to {}",
          context.getFlowRunId(),
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
}
