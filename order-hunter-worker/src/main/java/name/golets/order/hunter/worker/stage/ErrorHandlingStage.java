package name.golets.order.hunter.worker.stage;

import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.worker.error.StageError;
import name.golets.order.hunter.worker.error.WebClientError;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Central place for mapping technical failures to structured outcomes; composed alongside parallel
 * save branches in the full flow.
 */
@Component
public class ErrorHandlingStage implements Stage<PollOrdersFlowContext> {
  private static final Logger log = LoggerFactory.getLogger(ErrorHandlingStage.class);

  /**
   * Logs flow-level failure details propagated by orchestration.
   *
   * @param context flow context that may contain {@code flowError}
   * @return completion after error details are logged
   */
  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.fromRunnable(
        () -> {
          Throwable error = context != null ? context.getFlowError() : null;
          if (error == null) {
            return;
          }
          String flowRunId = context != null ? context.getFlowRunId() : "unknown";
          org.slf4j.Marker marker = context != null ? context.getSessionMarker() : null;
          if (error instanceof WebClientError webClientError) {
            log.error(
                marker,
                "Flow failed for flowRunId={} with WebClientError: "
                    + "code={}, cause={}, message={}, url={}",
                flowRunId,
                webClientError.getErrorCode(),
                webClientError.getErrorCause(),
                webClientError.getErrorMessage(),
                webClientError.getErrorUrl(),
                error);
            return;
          }
          if (error instanceof StageError stageError) {
            log.error(
                marker,
                "Flow failed for flowRunId={} with StageError: "
                    + "code={}, cause={}, message={}, url={}",
                flowRunId,
                stageError.getErrorCode(),
                stageError.getErrorCause(),
                stageError.getErrorMessage(),
                stageError.getErrorUrl(),
                error);
            return;
          }
          log.error(marker, "Internal Server Error for flowRunId={}", flowRunId, error);
        });
  }
}
