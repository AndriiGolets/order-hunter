package name.golets.order.hunter.worker.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.event.OrderTaken;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.sqs.OrderTakenSqsPublisher;
import name.golets.order.hunter.worker.stage.results.SaveHelpersStageResult;
import name.golets.order.hunter.worker.stage.results.SaveMainOrdersStageResult;
import name.golets.order.hunter.worker.state.DefaultWorkerStateManager;
import name.golets.order.hunter.worker.util.SimplifiedOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for outbound OrderTaken publication and state transition behavior. */
@ExtendWith(MockitoExtension.class)
class NotifySqsStageTest {

  @Mock private OrderTakenSqsPublisher orderTakenSqsPublisher;

  /**
   * Verifies completed task sets worker inactive and publishes saved main orders in event payload.
   */
  @Test
  void execute_completedTaskPublishesEventAndStopsWorker() {
    DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setStarted(true);
    state.setHeadsToTake(2);
    state.setHeadsTaken(2);
    PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);

    Order savedMain = new Order().setSid("main-1").setHeads(2);
    SaveMainOrdersStageResult saveMain = new SaveMainOrdersStageResult();
    saveMain.addSavedOrder(savedMain);
    context.setSaveMainOrdersResult(saveMain);

    when(orderTakenSqsPublisher.publish(any())).thenReturn(Mono.empty());
    TestObservationRegistry observationRegistry = TestObservationRegistry.create();
    NotifySqsStage stage = new NotifySqsStage(orderTakenSqsPublisher, observationRegistry);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    ArgumentCaptor<OrderTaken> captor = ArgumentCaptor.forClass(OrderTaken.class);
    verify(orderTakenSqsPublisher).publish(captor.capture());
    OrderTaken sent = captor.getValue();
    assertNotNull(sent.getProducedAt());
    assertEquals("1.0", sent.getEventVersion());
    assertTrue(sent.isCompleted());
    assertEquals(1, sent.getSavedOrders().size());
    SimplifiedOrder simplifiedOrder = sent.getSavedOrders().getFirst();
    assertEquals("main-1", simplifiedOrder.getSid());
    assertEquals(2, simplifiedOrder.getHeads());
    assertFalse(state.isStarted());
    observationRegistry.assertThat().hasAnObservationWithAKeyName("orderTaken");
  }

  /**
   * Verifies publish failures are retried and worker remains active when head target not reached.
   */
  @Test
  void execute_notCompletedRetriesPublishAndKeepsWorkerStarted() {
    DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setStarted(true);
    state.setHeadsToTake(10);
    state.setHeadsTaken(3);
    PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);

    SaveMainOrdersStageResult saveMain = new SaveMainOrdersStageResult();
    saveMain.addSavedOrder(new Order().setSid("main-1").setHeads(3));
    context.setSaveMainOrdersResult(saveMain);
    context.setSaveHelpersResult(new SaveHelpersStageResult());

    AtomicInteger attempts = new AtomicInteger();
    when(orderTakenSqsPublisher.publish(any()))
        .thenReturn(
            Mono.defer(
                () ->
                    attempts.getAndIncrement() == 0
                        ? Mono.error(new IllegalStateException("sqs down"))
                        : Mono.empty()));

    NotifySqsStage stage = new NotifySqsStage(orderTakenSqsPublisher, ObservationRegistry.create());

    StepVerifier.create(stage.execute(context)).verifyComplete();

    verify(orderTakenSqsPublisher, times(1)).publish(any());
    assertTrue(state.isStarted());
    assertEquals(3, state.getHeadsTaken());
    assertEquals(2, attempts.get());
  }

  /** Verifies no outbound SQS event is sent when the cycle saved zero orders. */
  @Test
  void execute_noSavedOrdersSkipsPublish() {
    DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setStarted(true);
    state.setHeadsToTake(10);
    state.setHeadsTaken(0);
    PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);
    context.setSaveMainOrdersResult(new SaveMainOrdersStageResult());
    context.setSaveHelpersResult(new SaveHelpersStageResult());

    NotifySqsStage stage = new NotifySqsStage(orderTakenSqsPublisher, ObservationRegistry.create());

    StepVerifier.create(stage.execute(context)).verifyComplete();

    verify(orderTakenSqsPublisher, never()).publish(any());
    assertTrue(state.isStarted());
    assertEquals(0, state.getHeadsTaken());
  }
}
