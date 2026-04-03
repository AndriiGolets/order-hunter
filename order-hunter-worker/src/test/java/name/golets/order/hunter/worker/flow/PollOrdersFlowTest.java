package name.golets.order.hunter.worker.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import name.golets.order.hunter.worker.stage.BetweenPollsDelayStage;
import name.golets.order.hunter.worker.stage.ErrorHandlingStage;
import name.golets.order.hunter.worker.stage.FilterOrdersStage;
import name.golets.order.hunter.worker.stage.NotifySqsStage;
import name.golets.order.hunter.worker.stage.ParseOrdersStage;
import name.golets.order.hunter.worker.stage.PollRecordsStage;
import name.golets.order.hunter.worker.stage.SaveHelpersStage;
import name.golets.order.hunter.worker.stage.SaveMainOrdersStage;
import name.golets.order.hunter.worker.stage.StatisticStage;
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
  @Mock private FilterOrdersStage filterOrdersStage;
  @Mock private SaveMainOrdersStage saveMainOrdersStage;
  @Mock private SaveHelpersStage saveHelpersStage;
  @Mock private NotifySqsStage notifySqsStage;
  @Mock private StatisticStage statisticStage;
  @Mock private ErrorHandlingStage errorHandlingStage;
  @Mock private BetweenPollsDelayStage betweenPollsDelayStage;

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
            filterOrdersStage,
            saveMainOrdersStage,
            saveHelpersStage,
            notifySqsStage,
            statisticStage,
            errorHandlingStage,
            betweenPollsDelayStage);

    RuntimeException boom = new RuntimeException("poll failed");
    when(pollRecordsStage.execute(any())).thenReturn(Mono.error(boom));
    lenient().when(parseOrdersStage.execute(any())).thenReturn(Mono.empty());
    lenient().when(filterOrdersStage.execute(any())).thenReturn(Mono.empty());
    lenient().when(saveMainOrdersStage.execute(any())).thenReturn(Mono.empty());
    lenient().when(saveHelpersStage.execute(any())).thenReturn(Mono.empty());
    lenient().when(notifySqsStage.execute(any())).thenReturn(Mono.empty());
    lenient().when(statisticStage.execute(any())).thenReturn(Mono.empty());
    when(errorHandlingStage.execute(any())).thenReturn(Mono.empty());
    when(betweenPollsDelayStage.execute(any())).thenReturn(Mono.empty());

    StepVerifier.create(flow.start()).verifyComplete();

    verify(errorHandlingStage).execute(any());
  }
}
