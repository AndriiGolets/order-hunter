package name.golets.order.hunter.orderhunterworker.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.orderhunterworker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.orderhunterworker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.orderhunterworker.state.DefaultWorkerStateManager;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Unit tests for filtering parsed orders by remaining heads, preference, and dedup rules. */
class FilterRecordsStageTest {

  /** Verifies higher-head main orders are selected first and may overshoot remaining heads. */
  @Test
  void execute_prefersHigherHeadsEvenWhenOvershootingTarget() {
    final FilterRecordsStage stage = new FilterRecordsStage();
    final DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setHeadsToTake(3);
    state.setHeadsTaken(1); // remaining = 2
    final PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);

    final ParseOrdersStageResult parse = new ParseOrdersStageResult();
    parse.addOrder(main("main-3h", "#1", 3));
    parse.addOrder(main("main-2h", "#2", 2));
    parse.addOrder(main("main-1h", "#3", 1));
    context.setParseOrdersResult(parse);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    assertNotNull(context.getFilterRecordsResult());
    assertEquals(1, context.getFilterRecordsResult().getMainOrders().size());
    assertEquals("main-3h", context.getFilterRecordsResult().getMainOrders().get(0).getSid());
  }

  /**
   * Verifies already-taken mains are skipped and helpers are attached by selected main order name.
   */
  @Test
  void execute_skipsTakenOrdersAndKeepsHelpersForSelectedMain() {
    final FilterRecordsStage stage = new FilterRecordsStage();
    final DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setHeadsToTake(6);
    state.setHeadsTaken(1); // remaining = 2
    state.registerSuccessfulSave(main("main-3h", "#1", 3));
    final PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);

    final ParseOrdersStageResult parse = new ParseOrdersStageResult();
    parse.addOrder(main("main-3h", "#1", 3)); // must be skipped by dedup
    parse.addOrder(main("main-2h", "#2", 2)); // selected
    parse.addOrder(helper("helper-2h-a", "#2"));
    parse.addOrder(helper("helper-2h-b", "#2"));
    parse.addOrder(helper("helper-other", "#9"));
    context.setParseOrdersResult(parse);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    assertNotNull(context.getFilterRecordsResult());
    assertEquals(1, context.getFilterRecordsResult().getMainOrders().size());
    assertEquals("main-2h", context.getFilterRecordsResult().getMainOrders().get(0).getSid());
    assertEquals(2, context.getFilterRecordsResult().getHelperOrders().size());
  }

  private static Order main(String sid, String name, int heads) {
    return new Order()
        .setSid(sid)
        .setName(name)
        .setHeads(heads)
        .setOrderType(OrderType.NORMAL)
        .setRecordHelper(false);
  }

  private static Order helper(String sid, String name) {
    return new Order()
        .setSid(sid)
        .setName(name)
        .setHeads(1)
        .setOrderType(OrderType.NORMAL)
        .setRecordHelper(true);
  }
}
