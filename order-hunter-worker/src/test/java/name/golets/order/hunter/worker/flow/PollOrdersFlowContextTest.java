package name.golets.order.hunter.worker.flow;

import static org.junit.jupiter.api.Assertions.assertSame;

import name.golets.order.hunter.worker.error.StageError;
import name.golets.order.hunter.worker.error.WebClientError;
import name.golets.order.hunter.worker.state.DefaultWorkerStateManager;
import org.junit.jupiter.api.Test;

/** Unit tests for flow context error normalization behavior. */
class PollOrdersFlowContextTest {

  /** Verifies that existing WebClientError is preserved without additional wrapping. */
  @Test
  void captureFlowError_keepsWebClientErrorUnchanged() {
    PollOrdersFlowContext context = PollOrdersFlowContext.begin(new DefaultWorkerStateManager());
    WebClientError error = WebClientError.fromResponse(400, "{\"errorMessage\":\"x\"}", "/api");

    context.captureFlowError(error);

    assertSame(error, context.getFlowError());
  }

  /** Verifies intentional stage errors are preserved for typed handling stage. */
  @Test
  void captureFlowError_keepsStageErrorUnchanged() {
    PollOrdersFlowContext context = PollOrdersFlowContext.begin(new DefaultWorkerStateManager());
    RuntimeException error = new IntentionalStageFailure();

    context.captureFlowError(error);

    assertSame(error, context.getFlowError());
  }

  /** Verifies unknown throwable stays unknown and falls back to internal-error logging branch. */
  @Test
  void captureFlowError_keepsUnknownThrowable() {
    PollOrdersFlowContext context = PollOrdersFlowContext.begin(new DefaultWorkerStateManager());
    IllegalStateException error = new IllegalStateException("boom");

    context.captureFlowError(error);

    assertSame(error, context.getFlowError());
  }

  private static final class IntentionalStageFailure extends RuntimeException
      implements StageError {
    @Override
    public String getErrorCode() {
      return "STAGE-001";
    }

    @Override
    public String getErrorCause() {
      return "INTENTIONAL";
    }

    @Override
    public String getErrorMessage() {
      return "Intentional stage failure";
    }

    @Override
    public String getErrorUrl() {
      return "parseOrdersStage";
    }
  }
}
