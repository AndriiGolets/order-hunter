package name.golets.order.hunter.worker.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.common.model.Record;
import name.golets.order.hunter.worker.config.CombinedOrdersPollPath;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.airportal.AirportalClient;
import name.golets.order.hunter.worker.state.DefaultWorkerStateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for poll stage success and failure propagation behavior. */
@ExtendWith(MockitoExtension.class)
class PollRecordsStageTest {

  @Mock private AirportalClient airportalClient;

  /** Verifies that a successful poll stores the full {@code OrdersResponse} in flow context. */
  @Test
  void execute_populatesContextWithPolledRecords() {
    Record first = new Record().setSid("sid-1");
    Record second = new Record().setSid("sid-2");
    OrdersResponse response = new OrdersResponse().setRecords(List.of(first, second));
    when(airportalClient.pollOrders("/api/poll?x=1")).thenReturn(Mono.just(response));
    PollRecordsStage pollRecordsStage =
        new PollRecordsStage(airportalClient, new CombinedOrdersPollPath("/api/poll?x=1"));

    PollOrdersFlowContext context = PollOrdersFlowContext.begin(new DefaultWorkerStateManager());

    StepVerifier.create(pollRecordsStage.execute(context)).verifyComplete();

    verify(airportalClient).pollOrders("/api/poll?x=1");
    assertNotNull(context.getPollRecordsResult());
    assertEquals(2, context.getPollRecordsResult().getOrdersResponse().getRecords().size());
    assertEquals(
        "sid-1", context.getPollRecordsResult().getOrdersResponse().getRecords().get(0).getSid());
  }

  /** Verifies that poll failures are propagated for flow-level ErrorHandlingStage processing. */
  @Test
  void execute_onPollErrorPropagatesException() {
    when(airportalClient.pollOrders("/api/poll?x=1"))
        .thenReturn(Mono.error(new IllegalStateException("upstream failure")));
    PollRecordsStage pollRecordsStage =
        new PollRecordsStage(airportalClient, new CombinedOrdersPollPath("/api/poll?x=1"));

    PollOrdersFlowContext context = PollOrdersFlowContext.begin(new DefaultWorkerStateManager());

    StepVerifier.create(pollRecordsStage.execute(context))
        .expectErrorSatisfies(error -> assertEquals("upstream failure", error.getMessage()))
        .verify();
  }
}
