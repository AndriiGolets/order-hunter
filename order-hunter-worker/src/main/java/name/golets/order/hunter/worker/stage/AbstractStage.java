package name.golets.order.hunter.worker.stage;

import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.common.flow.StageInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import reactor.core.publisher.Mono;

/**
 * Template stage implementation that centralizes stage input/result logging and orchestration.
 *
 * <p>Concrete stages are responsible only for:
 *
 * <ul>
 *   <li>reading required data from flow context into typed input
 *   <li>executing business logic for that input
 *   <li>storing typed result back to flow context
 * </ul>
 *
 * @param <ContextT> flow context type
 * @param <InputT> stage input type
 * @param <ResultT> stage result type
 */
public abstract class AbstractStage<ContextT, InputT extends StageInput, ResultT>
    implements Stage<ContextT> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  /**
   * Executes the shared stage lifecycle:
   *
   * <ol>
   *   <li>prepare typed input from context
   *   <li>log input via {@code input.toString()}
   *   <li>process business logic
   *   <li>log result via {@code result.toString()}
   *   <li>store result in context
   * </ol>
   *
   * <p>Errors are never swallowed and are propagated to flow-level error handling.
   *
   * @param context flow context of the current run
   * @return completion signal for stage execution
   */
  @Override
  public final Mono<Void> execute(ContextT context) {
    return Mono.defer(
        () -> {
          InputT input = prepareInput(context);
          Marker marker = marker(context);
          log.debug(marker, "{} input={}", stageName(), input);
          return process(input)
              .doOnNext(
                  result -> {
                    log.debug(marker, "{} result={}", stageName(), result);
                    storeResult(context, result);
                  })
              .then();
        });
  }

  /**
   * Converts context into explicit typed input for stage business logic.
   *
   * @param context flow context
   * @return typed stage input
   */
  protected abstract InputT prepareInput(ContextT context);

  /**
   * Runs stage business logic for prepared input.
   *
   * @param input typed stage input
   * @return typed stage result
   */
  protected abstract Mono<ResultT> process(InputT input);

  /**
   * Persists stage result into flow context.
   *
   * @param context flow context
   * @param result stage result
   */
  protected abstract void storeResult(ContextT context, ResultT result);

  /**
   * Returns marker used for common stage debug logs.
   *
   * @param context flow context
   * @return marker for current flow session
   */
  protected abstract Marker marker(ContextT context);

  /**
   * Returns stage name used in common debug logs.
   *
   * @return log-friendly stage name
   */
  protected String stageName() {
    return getClass().getSimpleName();
  }
}
