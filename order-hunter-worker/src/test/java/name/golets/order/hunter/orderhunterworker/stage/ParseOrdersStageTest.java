package name.golets.order.hunter.orderhunterworker.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import name.golets.order.hunter.common.constants.OrderConstants;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.Record;
import name.golets.order.hunter.orderhunterworker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.orderhunterworker.stage.results.PollRecordsStageResult;
import name.golets.order.hunter.orderhunterworker.state.DefaultWorkerStateManager;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Unit tests for parsing poll records into typed domain orders. */
class ParseOrdersStageTest {

  /** Verifies parse stage keeps both main and helper orders for allowed order types. */
  @Test
  void execute_parsesMainAndHelperOrders() {
    final ParseOrdersStage stage = new ParseOrdersStage();
    final DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setOrderTypes(List.of(OrderType.NORMAL));
    final PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);

    final PollRecordsStageResult pollResult = new PollRecordsStageResult();
    pollResult.addRecord(
        new Record()
            .setSid("title-main")
            .setObjectId(OrderConstants.TITLE_OBJECT_ID)
            .setPrimary("Regular portrait"));
    pollResult.addRecord(
        new Record()
            .setSid("title-helper")
            .setObjectId(OrderConstants.TITLE_OBJECT_ID)
            .setPrimary("Get a digital file add-on"));
    pollResult.addRecord(
        new Record()
            .setSid("artist-1")
            .setObjectId(OrderConstants.ARTIST_OBJECT_ID)
            .setPrimary("Artist 1"));
    pollResult.addRecord(
        new Record()
            .setSid("main-order")
            .setObjectId(OrderConstants.ORDER_OBJECT_ID)
            .setOrderProductTitle("title-main")
            .setOrderName("#1")
            .setArtist("artist-1")
            .setShippingTitle("FREE"));
    pollResult.addRecord(
        new Record()
            .setSid("helper-order")
            .setObjectId(OrderConstants.ORDER_OBJECT_ID)
            .setOrderProductTitle("title-helper")
            .setOrderName("#1")
            .setArtist("artist-1")
            .setShippingTitle("FREE"));
    context.setPollRecordsResult(pollResult);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    assertNotNull(context.getParseOrdersResult());
    assertEquals(2, context.getParseOrdersResult().getOrders().size());
  }

  /** Verifies allowed types from worker state are enforced (non-matching records are excluded). */
  @Test
  void execute_filtersByAllowedTypes() {
    final ParseOrdersStage stage = new ParseOrdersStage();
    final DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setOrderTypes(List.of(OrderType.FAST));
    final PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);

    final PollRecordsStageResult pollResult = new PollRecordsStageResult();
    pollResult.addRecord(
        new Record()
            .setSid("title-main")
            .setObjectId(OrderConstants.TITLE_OBJECT_ID)
            .setPrimary("Regular portrait"));
    pollResult.addRecord(
        new Record()
            .setSid("artist-1")
            .setObjectId(OrderConstants.ARTIST_OBJECT_ID)
            .setPrimary("Artist 1"));
    pollResult.addRecord(
        new Record()
            .setSid("main-order")
            .setObjectId(OrderConstants.ORDER_OBJECT_ID)
            .setOrderProductTitle("title-main")
            .setOrderName("#2")
            .setArtist("artist-1")
            .setShippingTitle("FREE"));
    context.setPollRecordsResult(pollResult);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    assertNotNull(context.getParseOrdersResult());
    assertEquals(0, context.getParseOrdersResult().getOrders().size());
  }
}
