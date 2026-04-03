package name.golets.order.hunter.worker.starter;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import name.golets.order.hunter.common.flow.Flow;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/** Verifies starter tick-loop pacing when worker is idle and when started. */
@ExtendWith(MockitoExtension.class)
class WorkerStarterTest {

  @Mock private WorkerStateManager workerStateManager;
  @Mock private Flow pollOrdersFlow;

  /** Ensures the starter uses configured idle delay while stopped, avoiding a tight no-op loop. */
  @Test
  void ensureTickLoopRunning_appliesIdleDelayWhenWorkerStopped() throws InterruptedException {
    when(workerStateManager.isStarted()).thenReturn(false);

    OrderHunterProperties properties = new OrderHunterProperties();
    properties.setWorkerAutoStart(false);
    properties.setIdleDelay(100);
    WorkerStarter starter = new WorkerStarter(workerStateManager, pollOrdersFlow, properties);

    starter.ensureTickLoopRunning();
    Thread.sleep(260);
    starter.stop();

    verify(pollOrdersFlow, never()).start();
    verify(workerStateManager, atMost(6)).isStarted();
  }

  /** Ensures no starter-level delay is added when active so cadence is controlled by the flow. */
  @Test
  void ensureTickLoopRunning_doesNotApplyIdleDelayWhenWorkerStarted() throws InterruptedException {
    when(workerStateManager.isStarted()).thenReturn(true);
    when(workerStateManager.getHeadsTaken()).thenReturn(0);
    when(workerStateManager.getHeadsToTake()).thenReturn(1);
    when(pollOrdersFlow.start()).thenReturn(Mono.delay(Duration.ofMillis(40)).then());

    OrderHunterProperties properties = new OrderHunterProperties();
    properties.setWorkerAutoStart(false);
    properties.setIdleDelay(1000);
    WorkerStarter starter = new WorkerStarter(workerStateManager, pollOrdersFlow, properties);

    starter.ensureTickLoopRunning();
    Thread.sleep(190);
    starter.stop();

    verify(pollOrdersFlow, atLeast(3)).start();
  }
}
