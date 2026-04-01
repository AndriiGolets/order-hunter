package name.golets.order.hunter.orderhunterworker.flow;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import name.golets.order.hunter.orderhunterworker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.orderhunterworker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.orderhunterworker.stage.results.PollRecordsStageResult;
import name.golets.order.hunter.orderhunterworker.stage.results.SaveHelpersStageResult;
import name.golets.order.hunter.orderhunterworker.stage.results.SaveMainOrdersStageResult;
import name.golets.order.hunter.orderhunterworker.state.WorkerStateManager;

/** Per-run context passed through poll–save–notify stages; holds explicit stage outputs. */
@Getter
@Setter
public final class PollOrdersFlowContext {

  private final WorkerStateManager stateManager;
  private final String flowRunId;
  private PollRecordsStageResult pollRecordsResult;
  private ParseOrdersStageResult parseOrdersResult;
  private FilterRecordsStageResult filterRecordsResult;
  private SaveMainOrdersStageResult saveMainOrdersResult;
  private SaveHelpersStageResult saveHelpersResult;

  private PollOrdersFlowContext(WorkerStateManager stateManager, String flowRunId) {
    this.stateManager = stateManager;
    this.flowRunId = flowRunId;
  }

  /**
   * Creates a new context for one {@link name.golets.order.hunter.common.flow.Flow#start()}
   * invocation.
   *
   * @param stateManager shared worker state
   * @return fresh context instance
   */
  public static PollOrdersFlowContext begin(WorkerStateManager stateManager) {
    return new PollOrdersFlowContext(stateManager, UUID.randomUUID().toString());
  }
}
