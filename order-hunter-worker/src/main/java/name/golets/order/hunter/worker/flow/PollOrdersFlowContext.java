package name.golets.order.hunter.worker.flow;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import name.golets.order.hunter.worker.error.StageError;
import name.golets.order.hunter.worker.error.WebClientError;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.worker.stage.results.PollRecordsStageResult;
import name.golets.order.hunter.worker.stage.results.SaveHelpersStageResult;
import name.golets.order.hunter.worker.stage.results.SaveMainOrdersStageResult;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/** Per-run context passed through poll–save–notify stages; holds explicit stage outputs. */
@Getter
@Setter
public final class PollOrdersFlowContext {

  private final WorkerStateManager stateManager;
  private final String flowRunId;
  private final Marker sessionMarker;
  private PollRecordsStageResult pollRecordsResult;
  private ParseOrdersStageResult parseOrdersResult;
  private FilterRecordsStageResult filterRecordsResult;
  private SaveMainOrdersStageResult saveMainOrdersResult;
  private SaveHelpersStageResult saveHelpersResult;
  private Throwable flowError;

  private PollOrdersFlowContext(
      WorkerStateManager stateManager, String flowRunId, Marker sessionMarker) {
    this.stateManager = stateManager;
    this.flowRunId = flowRunId;
    this.sessionMarker = sessionMarker;
  }

  /**
   * Creates a new context for one {@link name.golets.order.hunter.common.flow.Flow#start()}
   * invocation.
   *
   * @param stateManager shared worker state
   * @return fresh context instance
   */
  public static PollOrdersFlowContext begin(WorkerStateManager stateManager) {
    String flowRunId = UUID.randomUUID().toString();
    String sessionId =
        stateManager != null
                && stateManager.getSessionId() != null
                && !stateManager.getSessionId().isBlank()
            ? stateManager.getSessionId()
            : flowRunId;
    Marker marker = MarkerFactory.getMarker("sessionId=" + sessionId);
    return new PollOrdersFlowContext(stateManager, flowRunId, marker);
  }

  /**
   * Stores flow error in normalized form for centralized error logging.
   *
   * <p>Already-classified {@link WebClientError} and {@link StageError} are preserved. Transport
   * request failures are wrapped as {@link WebClientError}.
   *
   * @param error source error from stage execution
   */
  public void captureFlowError(Throwable error) {
    if (error == null) {
      this.flowError = null;
      return;
    }
    if (error instanceof WebClientError || error instanceof StageError) {
      this.flowError = error;
      return;
    }
    WebClientRequestException requestException = findCause(error, WebClientRequestException.class);
    if (requestException != null) {
      this.flowError = WebClientError.fromRequestException(requestException, "airportal");
      return;
    }
    this.flowError = error;
  }

  private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
    Throwable current = error;
    while (current != null) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
      current = current.getCause();
    }
    return null;
  }
}
