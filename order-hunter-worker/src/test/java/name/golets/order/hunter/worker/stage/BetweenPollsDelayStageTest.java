package name.golets.order.hunter.worker.stage;

import io.micrometer.observation.tck.TestObservationRegistry;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.state.DefaultWorkerStateManager;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Verifies delay stage execution rules and observation publishing. */
class BetweenPollsDelayStageTest {

  /** Ensures stage skips both delay and observation when filtered orders are present. */
  @Test
  void execute_skipsDelayAndObservationWhenFilteredOrdersPresent() {
    TestObservationRegistry observationRegistry = TestObservationRegistry.create();
    BetweenPollsDelayStage stage =
        new BetweenPollsDelayStage(properties(30, true), () -> observationRegistry);
    PollOrdersFlowContext context = PollOrdersFlowContext.begin(new DefaultWorkerStateManager());
    FilterRecordsStageResult filterResult = new FilterRecordsStageResult();
    filterResult.addFilteredOrder(new Order().setSid("sid-1"));
    context.setFilterRecordsResult(filterResult);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    observationRegistry.assertThat().doesNotHaveAnyObservation();
  }

  /** Ensures fixed delay and observation are emitted when filtered orders are absent. */
  @Test
  void execute_appliesConfiguredDelayAndCreatesObservationWhenNoFilteredOrders() {
    TestObservationRegistry observationRegistry = TestObservationRegistry.create();
    BetweenPollsDelayStage stage =
        new BetweenPollsDelayStage(properties(25, true), () -> observationRegistry);
    PollOrdersFlowContext context = PollOrdersFlowContext.begin(new DefaultWorkerStateManager());

    StepVerifier.withVirtualTime(() -> stage.execute(context))
        .thenAwait(java.time.Duration.ofMillis(25))
        .verifyComplete();

    observationRegistry
        .assertThat()
        .hasNumberOfObservationsWithNameEqualTo("order-hunter.flow.betweenPollsDelay", 1);
    observationRegistry.assertThat().hasAnObservationWithAKeyName("delay.millis");
  }

  /** Ensures disabled-randomize mode uses exact configured delay value. */
  @Test
  void execute_usesExactConfiguredDelayWhenRandomizationDisabled() {
    TestObservationRegistry observationRegistry = TestObservationRegistry.create();
    BetweenPollsDelayStage stage =
        new BetweenPollsDelayStage(properties(11, true), () -> observationRegistry);
    PollOrdersFlowContext context = PollOrdersFlowContext.begin(new DefaultWorkerStateManager());

    StepVerifier.withVirtualTime(() -> stage.execute(context))
        .thenAwait(java.time.Duration.ofMillis(11))
        .verifyComplete();

    observationRegistry
        .assertThat()
        .hasNumberOfObservationsWithNameEqualTo("order-hunter.flow.betweenPollsDelay", 1);
  }

  private static OrderHunterProperties properties(
      int betweenPollsJitterMax, boolean disableRandomize) {
    OrderHunterProperties properties = new OrderHunterProperties();
    properties.setBetweenPollsJitterMax(betweenPollsJitterMax);
    properties.setDisableJitterRandomize(disableRandomize);
    return properties;
  }
}
