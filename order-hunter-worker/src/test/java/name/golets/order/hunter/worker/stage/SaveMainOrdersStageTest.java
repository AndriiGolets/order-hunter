package name.golets.order.hunter.worker.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.flow.FlowObservationContextKeys;
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

/** Unit tests for parallel main-order save behavior and strict failure propagation. */
@ExtendWith(MockitoExtension.class)
class SaveMainOrdersStageTest {

  @Mock private AirportalClient airportalClient;
  @Mock private WorkerStateManager stateManager;

  /** Verifies failed order branches propagate error and are not silently ignored. */
  @Test
  void execute_whenOneSaveFails_propagatesError() {
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

    StepVerifier.create(stage.execute(context))
        .expectErrorSatisfies(error -> assertEquals("server failure", error.getMessage()))
        .verify();

    verify(airportalClient).patchOrder("sid-1", "artist-1");
    verify(airportalClient).patchOrder("sid-2", "artist-2");
    verify(stateManager, times(1)).registerSuccessfulSave(first);
  }

  /** Verifies invalid orders fail the stage instead of being silently skipped. */
  @Test
  void execute_whenOrderInvalid_propagatesError() {
    Order invalidMissingArtist = new Order().setSid("sid-invalid");
    FilterRecordsStageResult filterResult = new FilterRecordsStageResult();
    filterResult.addFilteredOrder(invalidMissingArtist);

    OrderHunterProperties properties = new OrderHunterProperties();
    SaveMainOrdersStage stage = new SaveMainOrdersStage(airportalClient, properties);

    PollOrdersFlowContext context = PollOrdersFlowContext.begin(stateManager);
    context.setFilterRecordsResult(filterResult);

    StepVerifier.create(stage.execute(context))
        .expectErrorSatisfies(error -> assertTrue(error.getMessage().contains("Invalid order")))
        .verify();
  }

  /**
   * Verifies each main-order save computes and propagates before-save delay to outbound request
   * observation context.
   */
  @Test
  void execute_addsBeforeSaveDelayToPatchContext() {
    Order first = new Order().setSid("sid-1").setArtist("artist-1");
    Order second = new Order().setSid("sid-2").setArtist("artist-2");
    FilterRecordsStageResult filterResult = new FilterRecordsStageResult();
    filterResult.addFilteredOrder(first);
    filterResult.addFilteredOrder(second);

    AtomicInteger seenCalls = new AtomicInteger();
    when(airportalClient.patchOrder(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(
            ignored ->
                Mono.deferContextual(
                    contextView -> {
                      String delayText =
                          contextView.get(FlowObservationContextKeys.SAVE_BEFORE_SAVES_DELAY);
                      int delay = Integer.parseInt(delayText);
                      assertTrue(delay >= 0 && delay <= 7);
                      seenCalls.incrementAndGet();
                      return Mono.just("ok");
                    }));

    OrderHunterProperties properties = new OrderHunterProperties();
    properties.setBeforeOrderSavingJitterMax(7);
    SaveMainOrdersStage stage = new SaveMainOrdersStage(airportalClient, properties);

    PollOrdersFlowContext context = PollOrdersFlowContext.begin(stateManager);
    context.setFilterRecordsResult(filterResult);

    StepVerifier.create(stage.execute(context)).verifyComplete();

    assertEquals(2, seenCalls.get());
  }
}
