package name.golets.order.hunter.worker.flow;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import java.util.UUID;
import name.golets.order.hunter.common.flow.Flow;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Observation wrapper that adds one root span for each poll flow cycle. */
@Component
@Primary
public class ObservedPollOrdersFlow implements Flow {

  private final PollOrdersFlow delegate;
  private final ObservationRegistry observationRegistry;

  /**
   * Creates observed flow wrapper.
   *
   * @param delegate business flow orchestration
   * @param observationRegistry registry used to create root flow observations
   */
  public ObservedPollOrdersFlow(PollOrdersFlow delegate, ObservationRegistry observationRegistry) {
    this.delegate = delegate;
    this.observationRegistry = observationRegistry;
  }

  @Override
  public Mono<Void> start() {
    Observation observation =
        Observation.createNotStarted("order-hunter.flow.cycle", observationRegistry)
            .lowCardinalityKeyValue("flow", "pollOrders")
            .highCardinalityKeyValue("cycle.id", UUID.randomUUID().toString())
            .start();
    return delegate
        .start()
        .doOnSuccess(ignored -> observation.lowCardinalityKeyValue("outcome", "success"))
        .doOnError(
            error -> {
              observation.lowCardinalityKeyValue("outcome", "error");
              observation.error(error);
            })
        .doFinally(signalType -> observation.stop())
        .contextWrite(
            reactorContext ->
                reactorContext
                    .put(ObservationThreadLocalAccessor.KEY, observation)
                    .put(FlowObservationContextKeys.FLOW_OBSERVATION, observation));
  }
}
