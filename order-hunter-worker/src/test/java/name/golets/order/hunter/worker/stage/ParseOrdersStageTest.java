package name.golets.order.hunter.worker.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import name.golets.order.hunter.common.constants.OrderConstants;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.common.model.Record;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.results.PollRecordsStageResult;
import name.golets.order.hunter.worker.state.DefaultWorkerStateManager;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Unit tests for parsing poll records into typed domain orders. */
class ParseOrdersStageTest {

  /** Verifies parse stage transforms {@code OrdersResponse} into parsed main and helper maps. */
  @Test
  void execute_parsesMainAndHelperOrders() {
    final ParseOrdersStage stage = new ParseOrdersStage();
    final DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setOrderTypes(List.of(OrderType.NORMAL));
    final PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);

    final OrdersResponse response = new OrdersResponse();
    response.setRecords(
        List.of(
            new Record()
                .setSid("title-main")
                .setObjectId(OrderConstants.TITLE_OBJECT_ID)
                .setPrimary("Regular portrait"),
            new Record()
                .setSid("title-helper")
                .setObjectId(OrderConstants.TITLE_OBJECT_ID)
                .setPrimary("Get a digital file add-on"),
            new Record()
                .setSid("artist-1")
                .setObjectId(OrderConstants.ARTIST_OBJECT_ID)
                .setPrimary("Artist 1"),
            new Record()
                .setSid("main-order")
                .setObjectId(OrderConstants.ORDER_OBJECT_ID)
                .setOrderProductTitle("title-main")
                .setOrderName("#1")
                .setArtist("artist-1")
                .setShippingTitle("FREE"),
            new Record()
                .setSid("helper-order")
                .setObjectId(OrderConstants.ORDER_OBJECT_ID)
                .setOrderProductTitle("title-helper")
                .setOrderName("#1")
                .setArtist("artist-1")
                .setShippingTitle("FREE")));
    final PollRecordsStageResult pollResult = new PollRecordsStageResult();
    pollResult.setOrdersResponse(response);
    context.setPollRecordsResult(pollResult);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    assertNotNull(context.getParseOrdersResult());
    assertEquals(1, context.getParseOrdersResult().getParsedOrders().getOrdersMapBySid().size());
    assertEquals(
        1, context.getParseOrdersResult().getParsedOrders().getOrdersHelperMapByName().size());
  }

  /** Verifies parse stage does not apply business filtering and keeps parsed main orders. */
  @Test
  void execute_doesNotFilterByAllowedTypes() {
    final ParseOrdersStage stage = new ParseOrdersStage();
    final DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setOrderTypes(List.of(OrderType.FAST));
    final PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);

    final OrdersResponse response = new OrdersResponse();
    response.setRecords(
        List.of(
            new Record()
                .setSid("title-main")
                .setObjectId(OrderConstants.TITLE_OBJECT_ID)
                .setPrimary("Regular portrait"),
            new Record()
                .setSid("artist-1")
                .setObjectId(OrderConstants.ARTIST_OBJECT_ID)
                .setPrimary("Artist 1"),
            new Record()
                .setSid("main-order")
                .setObjectId(OrderConstants.ORDER_OBJECT_ID)
                .setOrderProductTitle("title-main")
                .setOrderName("#2")
                .setArtist("artist-1")
                .setShippingTitle("FREE")));
    final PollRecordsStageResult pollResult = new PollRecordsStageResult();
    pollResult.setOrdersResponse(response);
    context.setPollRecordsResult(pollResult);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    assertNotNull(context.getParseOrdersResult());
    assertEquals(1, context.getParseOrdersResult().getParsedOrders().getOrdersMapBySid().size());
  }
}
