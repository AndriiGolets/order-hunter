package name.golets.order.hunter.orderhunterworker.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.common.model.Record;
import name.golets.order.hunter.orderhunterworker.config.CombinedOrdersPollPath;
import name.golets.order.hunter.orderhunterworker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.orderhunterworker.integration.airportal.AirportalClient;
import name.golets.order.hunter.orderhunterworker.state.DefaultWorkerStateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for poll stage behavior and non-fatal error fallback contract. */
@ExtendWith(MockitoExtension.class)
class PollRecordsStageTest {

  @Mock private AirportalClient airportalClient;

  /**
   * Verifies that a successful poll fills {@code PollRecordsStageResult} and stores it in flow
   * context.
   */
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
    assertEquals(2, context.getPollRecordsResult().getRecords().size());
    assertEquals("sid-1", context.getPollRecordsResult().getRecords().get(0).getSid());
  }

  /**
   * Verifies that poll failures are logged and converted to an empty stage result so the flow can
   * continue without retry.
   */
  @Test
  void execute_onPollErrorStoresEmptyResultAndCompletes() {
    when(airportalClient.pollOrders("/api/poll?x=1"))
        .thenReturn(Mono.error(new IllegalStateException("upstream failure")));
    PollRecordsStage pollRecordsStage =
        new PollRecordsStage(airportalClient, new CombinedOrdersPollPath("/api/poll?x=1"));

    PollOrdersFlowContext context = PollOrdersFlowContext.begin(new DefaultWorkerStateManager());

    StepVerifier.create(pollRecordsStage.execute(context)).verifyComplete();

    assertNotNull(context.getPollRecordsResult());
    assertEquals(0, context.getPollRecordsResult().getRecords().size());
  }
}
