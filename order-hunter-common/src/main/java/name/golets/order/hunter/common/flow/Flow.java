package name.golets.order.hunter.common.flow;

import reactor.core.publisher.Mono;

/**
 * Base contract for a reactive flow orchestration entry point.
 *
 * <p>Implementations should keep orchestration code minimal and delegate operational logic to
 * stages.
 */
public interface Flow {

  /**
   * Starts a single flow run.
   *
   * @return completion signal for the flow run
   */
  Mono<Void> start();
}
