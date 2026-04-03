package name.golets.order.hunter.worker.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import name.golets.order.hunter.worker.event.StartEvent;
import name.golets.order.hunter.worker.event.StatusEvent;
import name.golets.order.hunter.worker.event.StopEvent;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import name.golets.order.hunter.worker.state.WorkerStatusSnapshot;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Transport entry point for inbound SQS commands. Updates {@link WorkerStateManager} only; does not
 * run poll/save flow logic.
 */
@Component
public class SqsEventController {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  private final WorkerStateManager workerStateManager;
  private final ObservationRegistry observationRegistry;

  public SqsEventController(
      WorkerStateManager workerStateManager, ObservationRegistry observationRegistry) {
    this.workerStateManager = workerStateManager;
    this.observationRegistry = observationRegistry;
  }

  /**
   * Validates {@code event}, marks the worker started, and stores run parameters.
   *
   * @param event start command payload
   * @return completion when state has been updated
   */
  public Mono<Void> onStart(StartEvent event) {
    return observe(
        "order-hunter.sqs.command.start",
        "startEvent",
        toJson(event),
        Mono.fromRunnable(
            () -> {
              workerStateManager.setStarted(true);
              workerStateManager.setHeadsToTake(event.getHeadsToTake());
              workerStateManager.setOrderTypes(event.getOrderTypes());
            }));
  }

  /**
   * Marks the worker stopped so the starter skips flow subscription.
   *
   * @param event stop command payload
   * @return completion when state has been updated
   */
  public Mono<Void> onStop(StopEvent event) {
    return observe(
        "order-hunter.sqs.command.stop",
        Mono.fromRunnable(() -> workerStateManager.setStarted(false)));
  }

  /**
   * Returns the current worker snapshot for operators.
   *
   * @param event status request payload
   * @return snapshot of started flag, budgets, and session metadata
   */
  public Mono<WorkerStatusSnapshot> onStatus(StatusEvent event) {
    return observe(
        "order-hunter.sqs.command.status", Mono.fromCallable(workerStateManager::toSnapshot));
  }

  private <T> Mono<T> observe(String observationName, Mono<T> publisher) {
    return observe(observationName, null, null, publisher);
  }

  private <T> Mono<T> observe(
      String observationName, String tagName, String tagValue, Mono<T> publisher) {
    return Mono.defer(
        () -> {
          Observation observation =
              Observation.createNotStarted(observationName, observationRegistry);
          if (tagName != null && tagValue != null) {
            observation.highCardinalityKeyValue(tagName, tagValue);
          }
          observation.start();
          return publisher
              .doOnError(observation::error)
              .doFinally(signalType -> observation.stop());
        });
  }

  private static String toJson(StartEvent event) {
    try {
      return OBJECT_MAPPER.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }
}
