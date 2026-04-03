package name.golets.order.hunter.worker.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.airportal.AirportalClient;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for helper-order parallel save behavior with per-item error handling. */
@ExtendWith(MockitoExtension.class)
class SaveHelpersStageTest {

  @Mock private AirportalClient airportalClient;
  @Mock private WorkerStateManager stateManager;

  /** Verifies helper orders are resolved by filtered-main key and successful saves update state. */
  @Test
  void execute_savesHelpersMatchingFilteredOrdersKey() {
    Order filteredMain = new Order().setSid("main-1").setName("#1");
    FilterRecordsStageResult filterResult = new FilterRecordsStageResult();
    filterResult.addFilteredOrder(filteredMain);

    Order helper1 = new Order().setSid("helper-1").setArtist("artist-1");
    Order helper2 = new Order().setSid("helper-2").setArtist("artist-2");
    ParsedOrders parsedOrders = new ParsedOrders();
    parsedOrders.setOrdersHelperMapByName(
        Map.of(
            "main-1",
            new HashMap<>(
                Map.of(
                    "helper-1", helper1,
                    "helper-2", helper2))));
    ParseOrdersStageResult parseResult = new ParseOrdersStageResult();
    parseResult.setParsedOrders(parsedOrders);

    when(airportalClient.patchOrder("helper-1", "artist-1")).thenReturn(Mono.just("ok-1"));
    when(airportalClient.patchOrder("helper-2", "artist-2")).thenReturn(Mono.just("ok-2"));

    OrderHunterProperties properties = new OrderHunterProperties();
    properties.setMaxParallelOrdersToSaveThreads(3);
    SaveHelpersStage stage = new SaveHelpersStage(airportalClient, properties);

    PollOrdersFlowContext context = PollOrdersFlowContext.begin(stateManager);
    context.setFilterRecordsResult(filterResult);
    context.setParseOrdersResult(parseResult);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    verify(airportalClient).patchOrder("helper-1", "artist-1");
    verify(airportalClient).patchOrder("helper-2", "artist-2");
    verify(stateManager).registerSuccessfulSave(helper1);
    verify(stateManager).registerSuccessfulSave(helper2);
    assertNotNull(context.getSaveHelpersResult());
    assertEquals(2, context.getSaveHelpersResult().getSavedOrders().size());
  }

  /** Verifies helper save failure is logged and skipped while successful helpers continue. */
  @Test
  void execute_whenOneHelperSaveFails_continuesWithSuccessfulHelperSaves() {
    Order filteredMain = new Order().setSid("main-1");
    FilterRecordsStageResult filterResult = new FilterRecordsStageResult();
    filterResult.addFilteredOrder(filteredMain);

    Order helperSuccess = new Order().setSid("helper-ok").setArtist("artist-ok");
    Order helperFail = new Order().setSid("helper-fail").setArtist("artist-fail");
    ParsedOrders parsedOrders = new ParsedOrders();
    parsedOrders.setOrdersHelperMapByName(
        Map.of(
            "main-1",
            new HashMap<>(
                Map.of(
                    "helper-ok", helperSuccess,
                    "helper-fail", helperFail))));
    ParseOrdersStageResult parseResult = new ParseOrdersStageResult();
    parseResult.setParsedOrders(parsedOrders);

    when(airportalClient.patchOrder("helper-ok", "artist-ok")).thenReturn(Mono.just("ok"));
    when(airportalClient.patchOrder("helper-fail", "artist-fail"))
        .thenReturn(Mono.error(new IllegalStateException("server failure")));

    SaveHelpersStage stage = new SaveHelpersStage(airportalClient, new OrderHunterProperties());

    PollOrdersFlowContext context = PollOrdersFlowContext.begin(stateManager);
    context.setFilterRecordsResult(filterResult);
    context.setParseOrdersResult(parseResult);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    verify(airportalClient).patchOrder("helper-ok", "artist-ok");
    verify(airportalClient).patchOrder("helper-fail", "artist-fail");
    verify(stateManager, times(1)).registerSuccessfulSave(helperSuccess);
    assertNotNull(context.getSaveHelpersResult());
    assertEquals(1, context.getSaveHelpersResult().getSavedOrders().size());
    assertEquals("helper-ok", context.getSaveHelpersResult().getSavedOrders().get(0).getSid());
  }
}
