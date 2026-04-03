package name.golets.order.hunter.worker.starter;

import java.time.Duration;
import name.golets.order.hunter.common.flow.Flow;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Periodically evaluates {@link WorkerStateManager} and runs {@link Flow#start()} once per tick,
 * while applying an idle delay when the worker is stopped. Tick errors do not stop the loop.
 */
@Component
public class WorkerStarter implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(WorkerStarter.class);

  private final WorkerStateManager workerStateManager;
  private final Flow pollOrdersFlow;
  private final OrderHunterProperties properties;
  private volatile boolean running;
  private volatile Disposable tickDisposable;

  /**
   * Creates a starter that runs the poll flow on a periodic schedule.
   *
   * @param workerStateManager shared worker flags and head budget
   * @param pollOrdersFlow poll–save–notify flow executed on each tick
   * @param properties starter/flow timing configuration
   */
  public WorkerStarter(
      WorkerStateManager workerStateManager,
      Flow pollOrdersFlow,
      OrderHunterProperties properties) {
    this.workerStateManager = workerStateManager;
    this.pollOrdersFlow = pollOrdersFlow;
    this.properties = properties;
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
    if (properties.isWorkerAutoStart()) {
      ensureTickLoopRunning();
    }
  }

  /**
   * Subscribes the periodic tick loop if not already active. Used from integration tests when
   * {@link OrderHunterProperties#isWorkerAutoStart()} is false.
   */
  public synchronized void ensureTickLoopRunning() {
    if (tickDisposable != null && !tickDisposable.isDisposed()) {
      return;
    }
    tickDisposable =
        Mono.defer(this::safeTick)
            .repeat()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                null, err -> log.error("WorkerStarter tick loop terminated unexpectedly", err));
  }

  private Mono<Void> safeTick() {
    if (!workerStateManager.isStarted()) {
      return Mono.delay(Duration.ofMillis(Math.max(0, properties.getIdleDelay()))).then();
    }
    return tickOnce()
        .onErrorResume(
            error -> {
              log.warn("WorkerStarter tick failed; continuing loop", error);
              return Mono.empty();
            });
  }

  @Override
  public void stop() {
    running = false;
    Disposable d = tickDisposable;
    if (d != null && !d.isDisposed()) {
      d.dispose();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
