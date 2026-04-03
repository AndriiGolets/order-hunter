package name.golets.order.hunter.worker.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.common.utils.OrderParsingUtil;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.FilterOrdersStage;
import name.golets.order.hunter.worker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.worker.state.DefaultWorkerStateManager;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Guards that {@code freeOrders.json} still parses and filters for NORMAL integration scenarios.
 */
class FreeOrdersFixtureParsingTest {

  @Test
  void freeOrdersJson_containsTwoHeadMainEligibleForNormalFilter() throws IOException {
    OrdersResponse response = readOrdersResponse("freeOrders.json");
    ParsedOrders parsed = OrderParsingUtil.parseOrders(response, null);

    assertTrue(
        parsed.getOrdersMapBySid().containsKey("ov2_recLQ1ExOBR4FuUjm"),
        "Expected main order sid from integration plan");

    DefaultWorkerStateManager state = new DefaultWorkerStateManager();
    state.setStarted(true);
    state.setHeadsToTake(1);
    state.setHeadsTaken(0);
    state.setOrderTypes(java.util.List.of(OrderType.NORMAL));
    PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);
    ParseOrdersStageResult parseResult = new ParseOrdersStageResult();
    parseResult.setParsedOrders(parsed);
    context.setParseOrdersResult(parseResult);

    FilterOrdersStage filter = new FilterOrdersStage();
    StepVerifier.create(filter.execute(context)).verifyComplete();

    assertTrue(
        context.getFilterRecordsResult().getFilteredOrders().stream()
            .anyMatch(o -> "ov2_recLQ1ExOBR4FuUjm".equals(o.getSid())));
  }

  private static OrdersResponse readOrdersResponse(String resource) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    try (InputStream in =
        FreeOrdersFixtureParsingTest.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing " + resource);
      }
      String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return mapper.readValue(json, OrdersResponse.class);
    }
  }
}
