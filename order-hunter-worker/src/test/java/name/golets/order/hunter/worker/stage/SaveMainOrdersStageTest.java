package name.golets.order.hunter.worker.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.airportal.AirportalClient;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for parallel main-order save behavior and per-branch error isolation. */
@ExtendWith(MockitoExtension.class)
class SaveMainOrdersStageTest {

  @Mock private AirportalClient airportalClient;
  @Mock private WorkerStateManager stateManager;

  /**
   * Verifies failed order branches are logged/skipped while successful branches update state and
   * stage result.
   */
  @Test
  void execute_continuesWhenOneSaveFailsAndKeepsSuccessfulSaves() {
    Order first = new Order().setSid("sid-1").setArtist("artist-1");
    Order second = new Order().setSid("sid-2").setArtist("artist-2");
    FilterRecordsStageResult filterResult = new FilterRecordsStageResult();
    filterResult.addFilteredOrder(first);
    filterResult.addFilteredOrder(second);

    when(airportalClient.patchOrder("sid-1", "artist-1")).thenReturn(Mono.just("{\"ok\":true}"));
    when(airportalClient.patchOrder("sid-2", "artist-2"))
        .thenReturn(Mono.error(new IllegalStateException("server failure")));

    OrderHunterProperties properties = new OrderHunterProperties();
    properties.setMaxParallelOrdersToSaveThreads(4);
    SaveMainOrdersStage stage = new SaveMainOrdersStage(airportalClient, properties);

    PollOrdersFlowContext context = PollOrdersFlowContext.begin(stateManager);
    context.setFilterRecordsResult(filterResult);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    verify(airportalClient).patchOrder("sid-1", "artist-1");
    verify(airportalClient).patchOrder("sid-2", "artist-2");
    verify(stateManager, times(1)).registerSuccessfulSave(first);
    assertNotNull(context.getSaveMainOrdersResult());
    assertEquals(1, context.getSaveMainOrdersResult().getSavedOrders().size());
    assertEquals("sid-1", context.getSaveMainOrdersResult().getSavedOrders().get(0).getSid());
  }

  /**
   * Verifies invalid orders are skipped without calling Airportal and valid orders are still saved.
   */
  @Test
  void execute_skipsInvalidOrderAndSavesValidOnes() {
    Order invalidMissingArtist = new Order().setSid("sid-invalid");
    Order valid = new Order().setSid("sid-valid").setArtist("artist-valid");
    FilterRecordsStageResult filterResult = new FilterRecordsStageResult();
    filterResult.addFilteredOrder(invalidMissingArtist);
    filterResult.addFilteredOrder(valid);

    when(airportalClient.patchOrder("sid-valid", "artist-valid")).thenReturn(Mono.just("ok"));

    OrderHunterProperties properties = new OrderHunterProperties();
    SaveMainOrdersStage stage = new SaveMainOrdersStage(airportalClient, properties);

    PollOrdersFlowContext context = PollOrdersFlowContext.begin(stateManager);
    context.setFilterRecordsResult(filterResult);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    verify(airportalClient, times(1)).patchOrder("sid-valid", "artist-valid");
    verify(stateManager, times(1)).registerSuccessfulSave(valid);
    assertEquals(List.of(valid), context.getSaveMainOrdersResult().getSavedOrders());
  }
}
