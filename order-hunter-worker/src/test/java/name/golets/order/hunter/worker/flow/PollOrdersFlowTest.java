package name.golets.order.hunter.worker.flow;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import name.golets.order.hunter.worker.stage.ErrorHandlingStage;
import name.golets.order.hunter.worker.stage.FilterRecordsStage;
import name.golets.order.hunter.worker.stage.NotifySqsStage;
import name.golets.order.hunter.worker.stage.ParseOrdersStage;
import name.golets.order.hunter.worker.stage.PollRecordsStage;
import name.golets.order.hunter.worker.stage.SaveHelpersStage;
import name.golets.order.hunter.worker.stage.SaveMainOrdersStage;
import name.golets.order.hunter.worker.state.DefaultWorkerStateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Verifies flow-level error routing into {@link ErrorHandlingStage}. */
@ExtendWith(MockitoExtension.class)
class PollOrdersFlowTest {

  @Mock private PollRecordsStage pollRecordsStage;
  @Mock private ParseOrdersStage parseOrdersStage;
  @Mock private FilterRecordsStage filterRecordsStage;
  @Mock private SaveMainOrdersStage saveMainOrdersStage;
  @Mock private SaveHelpersStage saveHelpersStage;
  @Mock private NotifySqsStage notifySqsStage;
  @Mock private ErrorHandlingStage errorHandlingStage;

  /**
   * Ensures poll-stage exceptions are intercepted by flow orchestration and delegated to
   * ErrorHandlingStage.
   */
  @Test
  void start_onPollErrorDelegatesToErrorHandlingStage() {
    PollOrdersFlow flow =
        new PollOrdersFlow(
            new DefaultWorkerStateManager(),
            pollRecordsStage,
            parseOrdersStage,
            filterRecordsStage,
            saveMainOrdersStage,
            saveHelpersStage,
            notifySqsStage,
            errorHandlingStage);

    RuntimeException boom = new RuntimeException("poll failed");
    when(pollRecordsStage.execute(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.error(boom));
    when(parseOrdersStage.execute(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
    when(filterRecordsStage.execute(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
    when(saveMainOrdersStage.execute(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
    when(saveHelpersStage.execute(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
    when(notifySqsStage.execute(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
    when(errorHandlingStage.execute(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

    StepVerifier.create(flow.start()).verifyComplete();

    verify(errorHandlingStage).execute(org.mockito.ArgumentMatchers.any());
  }
}
