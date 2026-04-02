package name.golets.order.hunter.worker.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.List;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.worker.state.DefaultWorkerStateManager;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Unit tests for filtering parsed orders by remaining heads, preference, and dedup rules. */
class FilterRecordsStageTest {

  /** Verifies higher-head main orders are selected first and may overshoot remaining heads. */
  @Test
  void execute_prefersHigherHeadsEvenWhenOvershootingTarget() {
    final FilterRecordsStage stage = new FilterRecordsStage();
    final DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setHeadsToTake(1);
    state.setHeadsTaken(0);
    final PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);

    final ParseOrdersStageResult parse = new ParseOrdersStageResult();
    ParsedOrders parsedOrders = new ParsedOrders();
    parsedOrders.getOrdersMapBySid().put("main-5h", main("main-5h", "#1", 5));
    parsedOrders.getOrdersMapBySid().put("main-3h", main("main-3h", "#2", 3));
    parsedOrders.getOrdersMapBySid().put("main-2h", main("main-2h", "#3", 2));
    parse.setParsedOrders(parsedOrders);
    context.setParseOrdersResult(parse);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    assertNotNull(context.getFilterRecordsResult());
    assertEquals(1, context.getFilterRecordsResult().getFilteredOrders().size());
    assertEquals("main-5h", context.getFilterRecordsResult().getFilteredOrders().get(0).getSid());
  }

  /** Verifies filtering by saved SIDs and selected order types against main orders map only. */
  @Test
  void execute_filtersBySavedSidsAndAllowedTypes() {
    final FilterRecordsStage stage = new FilterRecordsStage();
    final DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setHeadsToTake(6);
    state.setHeadsTaken(0);
    state.setOrderTypes(List.of(OrderType.FAST));
    state.registerSuccessfulSave(main("main-3h", "#1", 3));
    final PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);

    final ParseOrdersStageResult parse = new ParseOrdersStageResult();
    ParsedOrders parsedOrders = new ParsedOrders();
    parsedOrders
        .getOrdersMapBySid()
        .put("main-3h", main("main-3h", "#1", 3)); // skipped by saved sid
    parsedOrders
        .getOrdersMapBySid()
        .put("main-fast-2h", main("main-fast-2h", "#2", 2).setOrderType(OrderType.FAST));
    parsedOrders
        .getOrdersMapBySid()
        .put("main-normal-5h", main("main-normal-5h", "#3", 5).setOrderType(OrderType.NORMAL));
    parsedOrders.setOrdersHelperMapByName(new HashMap<>());
    parsedOrders
        .getOrdersHelperMapByName()
        .computeIfAbsent("#2", key -> new HashMap<>())
        .put("helper-2h-a", helper("helper-2h-a", "#2"));
    parse.setParsedOrders(parsedOrders);
    context.setParseOrdersResult(parse);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    assertNotNull(context.getFilterRecordsResult());
    assertEquals(1, context.getFilterRecordsResult().getFilteredOrders().size());
    assertEquals(
        "main-fast-2h", context.getFilterRecordsResult().getFilteredOrders().get(0).getSid());
    assertEquals(1, parse.getParsedOrders().getOrdersHelperMapByName().size());
    assertEquals(3, parse.getParsedOrders().getOrdersMapBySid().size());
  }

  /** Verifies collector keeps selecting sorted orders until head target is reached or exceeded. */
  @Test
  void execute_collectsOrdersUntilTargetReached() {
    final FilterRecordsStage stage = new FilterRecordsStage();
    final DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setHeadsToTake(6);
    state.setHeadsTaken(0);
    final PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);

    final ParseOrdersStageResult parse = new ParseOrdersStageResult();
    ParsedOrders parsedOrders = new ParsedOrders();
    parsedOrders.getOrdersMapBySid().put("order-5", main("order-5", "#1", 5));
    parsedOrders.getOrdersMapBySid().put("order-3", main("order-3", "#2", 3));
    parsedOrders.getOrdersMapBySid().put("order-2", main("order-2", "#3", 2));
    parse.setParsedOrders(parsedOrders);
    context.setParseOrdersResult(parse);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    assertNotNull(context.getFilterRecordsResult());
    assertEquals(2, context.getFilterRecordsResult().getFilteredOrders().size());
    assertEquals("order-5", context.getFilterRecordsResult().getFilteredOrders().get(0).getSid());
    assertEquals("order-3", context.getFilterRecordsResult().getFilteredOrders().get(1).getSid());
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
