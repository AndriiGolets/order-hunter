package name.golets.order.hunter.worker.stage;

import static org.mockito.Mockito.when;

import io.micrometer.observation.tck.TestObservationRegistry;
import java.util.List;
import java.util.Map;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

/** Verifies when statistics observation is emitted and what tags it contains. */
@ExtendWith(MockitoExtension.class)
class StatisticStageTest {

  @Mock private WorkerStateManager stateManager;

  /** Ensures statistics observation is skipped when parsed main orders are absent. */
  @Test
  void execute_skipsObservationWhenNoMainOrdersPresent() {
    final TestObservationRegistry observationRegistry = TestObservationRegistry.create();
    final StatisticStage stage = new StatisticStage(observationRegistry);
    final PollOrdersFlowContext context = PollOrdersFlowContext.begin(stateManager);

    ParseOrdersStageResult parseResult = new ParseOrdersStageResult();
    parseResult.setParsedOrders(new ParsedOrders());
    context.setParseOrdersResult(parseResult);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    observationRegistry.assertThat().doesNotHaveAnyObservation();
  }

  /** Ensures stage emits custom statistics span with requested order and state tags. */
  @Test
  void execute_emitsStatisticsObservationWithExpectedTags() {
    final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

    when(stateManager.isStarted()).thenReturn(true);
    when(stateManager.getHeadsToTake()).thenReturn(10);
    when(stateManager.getOrderTypes()).thenReturn(List.of());
    when(stateManager.getSessionId()).thenReturn("session-1");
    when(stateManager.getHunterId()).thenReturn("hunter-a");

    final PollOrdersFlowContext context = PollOrdersFlowContext.begin(stateManager);

    Order main = new Order().setSid("main-1").setName("Main One").setProductTitle("Main Product");
    Order helper =
        new Order().setSid("helper-1").setName("Helper One").setProductTitle("Helper Product");

    ParsedOrders parsedOrders = new ParsedOrders();
    parsedOrders.setOrdersMapBySid(Map.of("main-1", main));
    parsedOrders.setOrdersHelperMapByName(Map.of("group", Map.of("helper-1", helper)));

    ParseOrdersStageResult parseResult = new ParseOrdersStageResult();
    parseResult.setParsedOrders(parsedOrders);
    context.setParseOrdersResult(parseResult);

    FilterRecordsStageResult filterResult = new FilterRecordsStageResult();
    filterResult.addFilteredOrder(main);
    context.setFilterRecordsResult(filterResult);

    StepVerifier.create(new StatisticStage(observationRegistry).execute(context)).verifyComplete();

    observationRegistry
        .assertThat()
        .hasNumberOfObservationsWithNameEqualTo("order-hunter.flow.statistics", 1);
    observationRegistry.assertThat().hasAnObservationWithAKeyName("orders.main.polled");
    observationRegistry.assertThat().hasAnObservationWithAKeyName("orders.helpers.polled");
    observationRegistry.assertThat().hasAnObservationWithAKeyName("orders.main.filtered");
    observationRegistry.assertThat().hasAnObservationWithAKeyName("state.headsToTake");
    observationRegistry.assertThat().hasAnObservationWithAKeyName("state.headsTaken");
    observationRegistry.assertThat().hasAnObservationWithAKeyName("state.takenOrderSids");
  }
}
