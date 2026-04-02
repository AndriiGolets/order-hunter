package name.golets.order.hunter.worker.controller;

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

  private final WorkerStateManager workerStateManager;

  public SqsEventController(WorkerStateManager workerStateManager) {
    this.workerStateManager = workerStateManager;
  }

  /**
   * Validates {@code event}, marks the worker started, and stores run parameters.
   *
   * @param event start command payload
   * @return completion when state has been updated
   */
  public Mono<Void> onStart(StartEvent event) {
    return Mono.fromRunnable(
        () -> {
          workerStateManager.setStarted(true);
          workerStateManager.setHeadsToTake(event.getHeadsToTake());
          workerStateManager.setOrderTypes(event.getOrderTypes());
        });
  }

  /**
   * Marks the worker stopped so the starter skips flow subscription.
   *
   * @param event stop command payload
   * @return completion when state has been updated
   */
  public Mono<Void> onStop(StopEvent event) {
    return Mono.fromRunnable(() -> workerStateManager.setStarted(false));
  }

  /**
   * Returns the current worker snapshot for operators.
   *
   * @param event status request payload
   * @return snapshot of started flag, budgets, and session metadata
   */
  public Mono<WorkerStatusSnapshot> onStatus(StatusEvent event) {
    return Mono.fromCallable(workerStateManager::toSnapshot);
  }
}
