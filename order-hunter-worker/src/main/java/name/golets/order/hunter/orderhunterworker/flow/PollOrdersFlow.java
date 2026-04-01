package name.golets.order.hunter.orderhunterworker.flow;

import name.golets.order.hunter.common.flow.Flow;
import name.golets.order.hunter.orderhunterworker.stage.ErrorHandlingStage;
import name.golets.order.hunter.orderhunterworker.stage.FilterRecordsStage;
import name.golets.order.hunter.orderhunterworker.stage.NotifySqsStage;
import name.golets.order.hunter.orderhunterworker.stage.ParseOrdersStage;
import name.golets.order.hunter.orderhunterworker.stage.PollRecordsStage;
import name.golets.order.hunter.orderhunterworker.stage.SaveHelpersStage;
import name.golets.order.hunter.orderhunterworker.stage.SaveMainOrdersStage;
import name.golets.order.hunter.orderhunterworker.state.WorkerStateManager;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Composes poll–parse–filter–save–notify stages for one run. Orchestration only; stage classes own
 * integration and business rules.
 */
@Component
public class PollOrdersFlow implements Flow {

  private final WorkerStateManager workerStateManager;
  private final PollRecordsStage pollRecordsStage;
  private final ParseOrdersStage parseOrdersStage;
  private final FilterRecordsStage filterRecordsStage;
  private final SaveMainOrdersStage saveMainOrdersStage;
  private final SaveHelpersStage saveHelpersStage;
  private final NotifySqsStage notifySqsStage;
  private final ErrorHandlingStage errorHandlingStage;

  /**
   * Creates a poll–save–notify flow wired with all stages.
   *
   * @param workerStateManager shared mutable state for the run context
   * @param pollRecordsStage first stage in the poll pipeline
   * @param parseOrdersStage parses API records into domain orders
   * @param filterRecordsStage applies head budget and deduplication rules
   * @param saveMainOrdersStage persists main orders in parallel
   * @param saveHelpersStage persists helper orders after mains
   * @param notifySqsStage emits outbound completion events
   * @param errorHandlingStage central error mapping hook
   */
  public PollOrdersFlow(
      WorkerStateManager workerStateManager,
      PollRecordsStage pollRecordsStage,
      ParseOrdersStage parseOrdersStage,
      FilterRecordsStage filterRecordsStage,
      SaveMainOrdersStage saveMainOrdersStage,
      SaveHelpersStage saveHelpersStage,
      NotifySqsStage notifySqsStage,
      ErrorHandlingStage errorHandlingStage) {
    this.workerStateManager = workerStateManager;
    this.pollRecordsStage = pollRecordsStage;
    this.parseOrdersStage = parseOrdersStage;
    this.filterRecordsStage = filterRecordsStage;
    this.saveMainOrdersStage = saveMainOrdersStage;
    this.saveHelpersStage = saveHelpersStage;
    this.notifySqsStage = notifySqsStage;
    this.errorHandlingStage = errorHandlingStage;
  }

  @Override
  public Mono<Void> start() {
    PollOrdersFlowContext context = PollOrdersFlowContext.begin(workerStateManager);
    return pollRecordsStage
        .execute(context)
        .then(parseOrdersStage.execute(context))
        .then(filterRecordsStage.execute(context))
        .then(saveMainOrdersStage.execute(context))
        .then(saveHelpersStage.execute(context))
        .then(notifySqsStage.execute(context))
        .then(errorHandlingStage.execute(context));
  }
}
