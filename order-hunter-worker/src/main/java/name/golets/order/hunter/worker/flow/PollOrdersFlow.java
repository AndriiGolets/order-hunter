package name.golets.order.hunter.worker.flow;

import name.golets.order.hunter.common.flow.Flow;
import name.golets.order.hunter.worker.stage.BetweenPollsDelayStage;
import name.golets.order.hunter.worker.stage.ErrorHandlingStage;
import name.golets.order.hunter.worker.stage.FilterOrdersStage;
import name.golets.order.hunter.worker.stage.NotifySqsStage;
import name.golets.order.hunter.worker.stage.ParseOrdersStage;
import name.golets.order.hunter.worker.stage.PollRecordsStage;
import name.golets.order.hunter.worker.stage.SaveHelpersStage;
import name.golets.order.hunter.worker.stage.SaveMainOrdersStage;
import name.golets.order.hunter.worker.stage.StatisticStage;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Composes poll–parse–filter–save–notify stages for one run. Orchestration only; stage classes own
 * integration and business rules.
 *
 * <p>After success/error handling, the flow optionally applies between-polls delay via {@link
 * BetweenPollsDelayStage}.
 */
@Component
public class PollOrdersFlow implements Flow {

  private final WorkerStateManager workerStateManager;
  private final PollRecordsStage pollRecordsStage;
  private final ParseOrdersStage parseOrdersStage;
  private final FilterOrdersStage filterOrdersStage;
  private final SaveMainOrdersStage saveMainOrdersStage;
  private final SaveHelpersStage saveHelpersStage;
  private final NotifySqsStage notifySqsStage;
  private final StatisticStage statisticStage;
  private final ErrorHandlingStage errorHandlingStage;
  private final BetweenPollsDelayStage betweenPollsDelayStage;

  /**
   * Creates a poll–save–notify flow wired with all stages.
   *
   * @param workerStateManager shared mutable state for the run context
   * @param pollRecordsStage first stage in the poll pipeline
   * @param parseOrdersStage parses API records into domain orders
   * @param filterOrdersStage applies head budget and deduplication rules
   * @param saveMainOrdersStage persists main orders in parallel
   * @param saveHelpersStage persists helper orders after mains
   * @param notifySqsStage emits outbound completion events
   * @param statisticStage emits final per-run statistics observation
   * @param errorHandlingStage central error mapping hook
   * @param betweenPollsDelayStage applies optional post-cycle delay
   */
  public PollOrdersFlow(
      WorkerStateManager workerStateManager,
      PollRecordsStage pollRecordsStage,
      ParseOrdersStage parseOrdersStage,
      FilterOrdersStage filterOrdersStage,
      SaveMainOrdersStage saveMainOrdersStage,
      SaveHelpersStage saveHelpersStage,
      NotifySqsStage notifySqsStage,
      StatisticStage statisticStage,
      ErrorHandlingStage errorHandlingStage,
      BetweenPollsDelayStage betweenPollsDelayStage) {
    this.workerStateManager = workerStateManager;
    this.pollRecordsStage = pollRecordsStage;
    this.parseOrdersStage = parseOrdersStage;
    this.filterOrdersStage = filterOrdersStage;
    this.saveMainOrdersStage = saveMainOrdersStage;
    this.saveHelpersStage = saveHelpersStage;
    this.notifySqsStage = notifySqsStage;
    this.statisticStage = statisticStage;
    this.errorHandlingStage = errorHandlingStage;
    this.betweenPollsDelayStage = betweenPollsDelayStage;
  }

  /**
   * Runs stages one after another on the same {@link PollOrdersFlowContext}.
   *
   * @return completion when the pipeline (including optional between-polls delay) finishes
   */
  @Override
  public Mono<Void> start() {
    PollOrdersFlowContext context = PollOrdersFlowContext.begin(workerStateManager);
    return pollRecordsStage
        .execute(context)
        .then(parseOrdersStage.execute(context))
        .then(filterOrdersStage.execute(context))
        .then(saveMainOrdersStage.execute(context))
        .then(saveHelpersStage.execute(context))
        .then(notifySqsStage.execute(context))
        .then(statisticStage.execute(context))
        .onErrorResume(
            error -> {
              context.captureFlowError(error);
              return errorHandlingStage.execute(context);
            })
        .then(betweenPollsDelayStage.execute(context));
  }
}
