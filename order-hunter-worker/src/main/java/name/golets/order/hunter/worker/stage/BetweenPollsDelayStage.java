package name.golets.order.hunter.worker.stage;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.flow.FlowObservationContextKeys;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.inputs.BetweenPollsDelayStageInput;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.util.JsonUtil;
import org.slf4j.Marker;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Applies optional delay between poll cycles after a flow run.
 *
 * <p>The stage runs only when no orders were selected by filtering in the current cycle. When at
 * least one order was selected, delay is skipped and no observation span is created.
 */
@Component
public class BetweenPollsDelayStage
    extends AbstractStage<
        PollOrdersFlowContext,
        BetweenPollsDelayStageInput,
        BetweenPollsDelayStage.BetweenPollsDelayStageResult> {
  private static final String SPAN_NAME = "order-hunter.flow.betweenPollsDelay";

  private final int betweenPollsJitterMax;
  private final boolean disableJitterRandomize;
  private final ObjectFactory<ObservationRegistry> observationRegistryFactory;

  /**
   * Creates delay stage dependencies.
   *
   * @param properties worker runtime properties
   * @param observationRegistryFactory factory for delay observation registry
   */
  public BetweenPollsDelayStage(
      OrderHunterProperties properties,
      ObjectFactory<ObservationRegistry> observationRegistryFactory) {
    this.betweenPollsJitterMax = Math.max(0, properties.getBetweenPollsJitterMax());
    this.disableJitterRandomize = properties.isDisableJitterRandomize();
    this.observationRegistryFactory = observationRegistryFactory;
  }

  @Override
  protected BetweenPollsDelayStageInput prepareInput(PollOrdersFlowContext context) {
    return new BetweenPollsDelayStageInput(countSavedOrders(context.getFilterRecordsResult()));
  }

  @Override
  protected Mono<BetweenPollsDelayStageResult> process(BetweenPollsDelayStageInput input) {
    return Mono.deferContextual(
        contextView -> {
          if (input.getFilteredOrdersCount() > 0) {
            return Mono.just(new BetweenPollsDelayStageResult(false, 0));
          }

          int delayMillis = computeDelayMillis();
          if (delayMillis <= 0) {
            return Mono.just(new BetweenPollsDelayStageResult(false, 0));
          }

          ObservationRegistry observationRegistry = observationRegistryFactory.getObject();
          Observation observation =
              Observation.createNotStarted(SPAN_NAME, observationRegistry)
                  .lowCardinalityKeyValue("stage", "betweenPollsDelay")
                  .lowCardinalityKeyValue("delay.millis", Integer.toString(delayMillis));
          if (contextView.hasKey(FlowObservationContextKeys.FLOW_OBSERVATION)) {
            Object parent = contextView.get(FlowObservationContextKeys.FLOW_OBSERVATION);
            if (parent instanceof Observation parentObservation) {
              observation.parentObservation(parentObservation);
            }
          }
          observation.start();
          return Mono.delay(Duration.ofMillis(delayMillis))
              .thenReturn(new BetweenPollsDelayStageResult(true, delayMillis))
              .doOnError(observation::error)
              .doFinally(signalType -> observation.stop());
        });
  }

  @Override
  protected void storeResult(PollOrdersFlowContext context, BetweenPollsDelayStageResult result) {
    // Delay stage has no context output.
  }

  @Override
  protected Marker marker(PollOrdersFlowContext context) {
    return context.getSessionMarker();
  }

  @Override
  protected String stageName() {
    return "betweenPollsDelayStage";
  }

  private int countSavedOrders(FilterRecordsStageResult filterResult) {
    if (filterResult == null) {
      return 0;
    }
    return filterResult.getFilteredOrders().size();
  }

  private int computeDelayMillis() {
    if (betweenPollsJitterMax == 0) {
      return 0;
    }
    if (disableJitterRandomize) {
      return betweenPollsJitterMax;
    }
    return ThreadLocalRandom.current().nextInt(0, betweenPollsJitterMax + 1);
  }

  record BetweenPollsDelayStageResult(boolean delayApplied, int delayMillis) {
    @Override
    public String toString() {
      return JsonUtil.toOneLineJson(this);
    }
  }
}
