package name.golets.order.hunter.orderhunterworker.starter;

import name.golets.order.hunter.common.flow.Flow;
import name.golets.order.hunter.orderhunterworker.state.WorkerStateManager;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Periodically evaluates {@link WorkerStateManager} and runs {@link Flow#start()} in single-thread
 * sequence per tick. Skeleton: lifecycle hooks only; reactive tick scheduling is not wired yet.
 */
@Component
public class WorkerStarter implements SmartLifecycle {

  private final WorkerStateManager workerStateManager;
  private final Flow pollOrdersFlow;
  private volatile boolean running;

  public WorkerStarter(WorkerStateManager workerStateManager, Flow pollOrdersFlow) {
    this.workerStateManager = workerStateManager;
    this.pollOrdersFlow = pollOrdersFlow;
  }

  /**
   * Executes one tick: when started and under capacity, runs the poll flow to completion.
   *
   * @return completion of the flow run or an immediate complete if skipped
   */
  public Mono<Void> tickOnce() {
    if (!workerStateManager.isStarted()) {
      return Mono.empty();
    }
    if (workerStateManager.getHeadsTaken() >= workerStateManager.getHeadsToTake()) {
      return Mono.empty();
    }
    return pollOrdersFlow.start();
  }

  @Override
  public void start() {
    running = true;
  }

  @Override
  public void stop() {
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
