package name.golets.order.hunter.common.flow;

import reactor.core.publisher.Mono;

/**
 * Base contract for a small reactive unit of work used inside a flow.
 *
 * @param <TContext> context type shared across stages in one flow run
 */
public interface Stage<TContext> {

  /**
   * Executes this stage using the provided flow context.
   *
   * @param context per-flow-run context
   * @return completion signal for this stage
   */
  Mono<Void> execute(TContext context);
}
