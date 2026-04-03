package name.golets.order.hunter.worker.stage;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.flow.FlowObservationContextKeys;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class BetweenPollsDelayStage implements Stage<PollOrdersFlowContext> {
  private static final Logger log = LoggerFactory.getLogger(BetweenPollsDelayStage.class);
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

  /**
   * Applies delay when current cycle has no filtered orders.
   *
   * @param context flow session context
   * @return completion after delay, or immediate completion when skipped
   */
  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.deferContextual(
            contextView -> {
              int filteredOrdersCount = countSavedOrders(context);
              if (filteredOrdersCount > 0) {
                return Mono.empty();
              }

              int delayMillis = computeDelayMillis();
              if (delayMillis <= 0) {
                return Mono.empty();
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
                  .then()
                  .doOnError(observation::error)
                  .doFinally(signalType -> observation.stop());
            })
        .onErrorResume(
            error -> {
              log.error(
                  context.getSessionMarker(),
                  "Internal server error while applying between polls delay for flowRunId={}",
                  context.getFlowRunId(),
                  error);
              return Mono.empty();
            });
  }

  private int countSavedOrders(PollOrdersFlowContext context) {
    FilterRecordsStageResult filterResult = context.getFilterRecordsResult();
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
}
