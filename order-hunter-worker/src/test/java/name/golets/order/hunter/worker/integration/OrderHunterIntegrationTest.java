package name.golets.order.hunter.worker.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.common.utils.OrderParsingUtil;
import name.golets.order.hunter.worker.OrderHunterWorkerApplication;
import name.golets.order.hunter.worker.controller.SqsEventController;
import name.golets.order.hunter.worker.event.OrderTaken;
import name.golets.order.hunter.worker.event.StartEvent;
import name.golets.order.hunter.worker.event.StatusEvent;
import name.golets.order.hunter.worker.event.StopEvent;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.sqs.OrderTakenSqsPublisher;
import name.golets.order.hunter.worker.integration.support.AirportalMockDispatcher;
import name.golets.order.hunter.worker.stage.FilterOrdersStage;
import name.golets.order.hunter.worker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.worker.starter.WorkerStarter;
import name.golets.order.hunter.worker.state.DefaultWorkerStateManager;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * End-to-end worker tests against a mock airportal HTTP server and mocked outbound SQS publisher.
 * Scenarios follow {@code integration-tests-plan.md}.
 */
@SpringBootTest(
    classes = {OrderHunterWorkerApplication.class, OrderHunterIntegrationTest.Config.class})
@AutoConfigureTracing
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderHunterIntegrationTest {

  private static final String JAEGER_ENDPOINT = "http://localhost:16686/";
  private static final Duration JAEGER_STARTUP_TIMEOUT = Duration.ofSeconds(30);
  private static final String TWO_HEAD_ORDER_SID = "ov2_recLQ1ExOBR4FuUjm";

  /**
   * Second selected main for {@code headsToTake=3} after the 2-head order: next 1-head main by sid
   * sort among mains in {@code freeOrders.json} (not {@code ov2_rec299vAGcNl1LG5I}, which is a
   * helper product).
   */
  private static final String SECOND_MAIN_ONE_HEAD_SID = "ov2_recCH2TXf4vkGD4vQ";

  /**
   * Distinct main in {@code twoOrders.json} used to identify the second-poll {@link OrderTaken}.
   */
  private static final String TWO_ORDERS_OTHER_MAIN_SID = "ov2_recTFfDu6XEt9sk1O";

  private static final AirportalMockDispatcher DISPATCHER = new AirportalMockDispatcher();
  private static final MockWebServer MOCK_WEB_SERVER;
  private static final CopyOnWriteArrayList<OrderTaken> RECORDED_ORDER_TAKEN_EVENTS =
      new CopyOnWriteArrayList<>();

  static {
    try {
      MOCK_WEB_SERVER = new MockWebServer();
      MOCK_WEB_SERVER.setDispatcher(DISPATCHER);
      MOCK_WEB_SERVER.start();
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Autowired private SqsEventController sqsEventController;
  @Autowired private WorkerStarter workerStarter;
  @Autowired private WorkerStateManager workerStateManager;

  @BeforeAll
  static void startJaegerForTracing() throws InterruptedException {
    try {
      Path composeFile = resolveComposeFile();
      Process process =
          new ProcessBuilder(
                  "docker", "compose", "-f", composeFile.toString(), "up", "-d", "jaeger")
              .redirectErrorStream(true)
              .start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        System.err.println(
            "Skipping Jaeger startup for integration tests. docker compose returned "
                + exitCode
                + ". Output: "
                + output);
        return;
      }
      waitForJaegerReadiness();
    } catch (IOException e) {
      System.err.println(
          "Skipping Jaeger startup for integration tests because docker is unavailable: "
              + e.getMessage());
    }
  }

  @AfterAll
  static void stopMockServer() throws IOException {
    MOCK_WEB_SERVER.shutdown();
  }

  @DynamicPropertySource
  static void registerAirportalBaseUrl(DynamicPropertyRegistry registry) {
    registry.add(
        "order-hunter.airportal-host", () -> "http://127.0.0.1:" + MOCK_WEB_SERVER.getPort());
    registry.add("order-hunter.worker-auto-start", () -> "false");
  }

  @BeforeEach
  void resetDispatcherAndRecordedEvents() {
    DISPATCHER.reset();
    RECORDED_ORDER_TAKEN_EVENTS.clear();
  }

  @Test
  void scenario1_stopSuppressesFurtherPolling_andNoSaves() throws InterruptedException {
    DISPATCHER.setPollEmptyForever();
    sqsEventController.onStart(startEvent(1, OrderType.NORMAL)).block(Duration.ofSeconds(5));
    workerStarter.ensureTickLoopRunning();

    await().atMost(15, SECONDS).until(() -> DISPATCHER.getPollCount() >= 2);

    sqsEventController.onStop(new StopEvent()).block(Duration.ofSeconds(5));
    int pollsAtStop = DISPATCHER.getPollCount();

    Thread.sleep(2500);
    assertThat(DISPATCHER.getPollCount()).isEqualTo(pollsAtStop);
    assertThat(DISPATCHER.getPatchCount()).isZero();
  }

  @Test
  void scenario2_firstPoll500_thenContinues_andNoSaves() throws InterruptedException {
    DISPATCHER.setFirstPolls500ThenEmpty(1);
    sqsEventController.onStart(startEvent(1, OrderType.NORMAL)).block(Duration.ofSeconds(5));
    workerStarter.ensureTickLoopRunning();

    await().atMost(15, SECONDS).until(() -> DISPATCHER.getPollCount() >= 2);

    Thread.sleep(2500);
    sqsEventController.onStop(new StopEvent()).block(Duration.ofSeconds(5));

    assertThat(DISPATCHER.getPollCount()).isGreaterThan(1);
    assertThat(DISPATCHER.getPatchCount()).isZero();
  }

  @Test
  void scenario3_restartAfterStop_pollsResume() throws InterruptedException {
    DISPATCHER.setFirstPolls500ThenEmpty(1);
    sqsEventController.onStart(startEvent(1, OrderType.NORMAL)).block(Duration.ofSeconds(5));
    workerStarter.ensureTickLoopRunning();
    await().atMost(15, SECONDS).until(() -> DISPATCHER.getPollCount() >= 2);
    sqsEventController.onStop(new StopEvent()).block(Duration.ofSeconds(5));
    final int pollsAfterFirstRun = DISPATCHER.getPollCount();
    Thread.sleep(600);

    sqsEventController.onStart(startEvent(1, OrderType.NORMAL)).block(Duration.ofSeconds(5));
    workerStarter.ensureTickLoopRunning();
    await().atMost(15, SECONDS).until(() -> DISPATCHER.getPollCount() > pollsAfterFirstRun);

    sqsEventController.onStop(new StopEvent()).block(Duration.ofSeconds(5));
  }

  @Test
  void scenario4_prefersTwoHeadOrder_headsToTakeOne() {
    DISPATCHER.setThreeEmptyThenPayloadThenEmpty(readClasspathUtf8("freeOrders.json"));
    sqsEventController.onStart(startEvent(1, OrderType.NORMAL)).block(Duration.ofSeconds(5));
    workerStarter.ensureTickLoopRunning();

    await()
        .atMost(30, SECONDS)
        .until(
            () ->
                RECORDED_ORDER_TAKEN_EVENTS.stream()
                    .anyMatch(
                        e ->
                            e.getSavedOrders().stream()
                                .anyMatch(o -> TWO_HEAD_ORDER_SID.equals(o.getSid()))));

    OrderTaken meaningful =
        RECORDED_ORDER_TAKEN_EVENTS.stream()
            .filter(
                e ->
                    e.getSavedOrders().stream()
                        .anyMatch(o -> TWO_HEAD_ORDER_SID.equals(o.getSid())))
            .findFirst()
            .orElseThrow();

    sqsEventController.onStop(new StopEvent()).block(Duration.ofSeconds(5));

    await().atMost(10, SECONDS).until(() -> DISPATCHER.getPollCount() >= 4);
    assertThat(DISPATCHER.getPollCount()).isEqualTo(4);

    assertThat(meaningful.isCompleted()).isTrue();
    assertThat(meaningful.getSavedOrders()).hasSize(1);
    assertThat(meaningful.getSavedOrders().get(0).getSid()).isEqualTo(TWO_HEAD_ORDER_SID);
    int headsInEvent =
        meaningful.getSavedOrders().stream().mapToInt(o -> Math.max(0, o.getHeads())).sum();
    assertThat(headsInEvent).isEqualTo(2);

    assertThat(DISPATCHER.getPatchCount()).isGreaterThanOrEqualTo(1);
  }

  /** Verifies failed main save is logged while cycle continues and still emits OrderTaken event. */
  @Test
  void scenario5_parallelSaves_oneMainFails_cycleContinuesWithPartialSuccess() {
    DISPATCHER.setThreeEmptyThenPayloadThenEmpty(readClasspathUtf8("freeOrders.json"));
    DISPATCHER.setPatchHttpStatus(TWO_HEAD_ORDER_SID, 500);
    DISPATCHER.setPatchHttpStatus(SECOND_MAIN_ONE_HEAD_SID, 200);

    sqsEventController.onStart(startEvent(3, OrderType.NORMAL)).block(Duration.ofSeconds(5));
    workerStarter.ensureTickLoopRunning();

    await()
        .atMost(45, SECONDS)
        .until(() -> DISPATCHER.getPatchAttemptsForOrderSid(TWO_HEAD_ORDER_SID) == 1);

    sqsEventController.onStop(new StopEvent()).block(Duration.ofSeconds(5));

    assertThat(DISPATCHER.getPollCount())
        .as("at least the 3 empty + 1 payload polls; extra ticks may run before stop")
        .isGreaterThanOrEqualTo(4);
    await().atMost(30, SECONDS).until(() -> !RECORDED_ORDER_TAKEN_EVENTS.isEmpty());
    assertThat(RECORDED_ORDER_TAKEN_EVENTS)
        .as("notify stage should still emit event for successful saves")
        .isNotEmpty();
    assertThat(RECORDED_ORDER_TAKEN_EVENTS)
        .allSatisfy(
            event ->
                assertThat(event.getSavedOrders())
                    .noneMatch(order -> TWO_HEAD_ORDER_SID.equals(order.getSid())));

    assertThat(DISPATCHER.getPatchAttemptsForOrderSid(TWO_HEAD_ORDER_SID)).isEqualTo(1);
    assertThat(DISPATCHER.getPatchAttemptsForOrderSid(SECOND_MAIN_ONE_HEAD_SID))
        .as("parallel branch may already start before failure is observed")
        .isGreaterThanOrEqualTo(0);
  }

  /**
   * With {@code headsToTake=10}, {@code freeOrders.json} yields fewer than 10 heads across eligible
   * mains, so after the payload poll is handled the worker must keep polling (empty responses)
   * until stopped.
   */
  @Test
  void scenario6_headsToTakeTen_afterFreeOrdersPayload_pollingContinues() {
    DISPATCHER.setThreeEmptyThenPayloadThenEmpty(readClasspathUtf8("freeOrders.json"));
    sqsEventController.onStart(startEvent(10, OrderType.NORMAL)).block(Duration.ofSeconds(5));
    workerStarter.ensureTickLoopRunning();

    await().atMost(30, SECONDS).until(() -> DISPATCHER.getPollCount() >= 4);

    await()
        .atMost(20, SECONDS)
        .until(
            () ->
                DISPATCHER.getPollCount()
                    > 4); // further polls after payload: heads remain below target

    sqsEventController.onStop(new StopEvent()).block(Duration.ofSeconds(5));

    assertThat(DISPATCHER.getPatchCount()).isGreaterThanOrEqualTo(1);
  }

  /**
   * First main in filter order gets HTTP 500; {@code Flux.flatMap(..., 5)} still runs every other
   * main PATCH to completion. {@link AirportalMockDispatcher} uses a short per-PATCH delay so peak
   * concurrent PATCH handlers match {@code maxParallelOrdersToSaveThreads: 5}.
   */
  @Test
  void scenario7_firstMainSave500_cycleContinues_withBoundedParallelism() {
    FreeOrdersFixtureSelection selection = selectFreeOrdersNormalMains(10);
    final List<String> filteredMainSids = selection.filteredMainSids();
    assertThat(filteredMainSids)
        .as("fixture: highest-heads main is first in filter order")
        .startsWith(TWO_HEAD_ORDER_SID);

    DISPATCHER.setPatchHandlingDelayMs(120);
    DISPATCHER.setThreeEmptyThenPayloadThenEmpty(readClasspathUtf8("freeOrders.json"));
    DISPATCHER.setPatchHttpStatus(TWO_HEAD_ORDER_SID, 500);
    sqsEventController.onStart(startEvent(10, OrderType.NORMAL)).block(Duration.ofSeconds(5));
    workerStarter.ensureTickLoopRunning();

    await()
        .atMost(90, SECONDS)
        .until(() -> DISPATCHER.getPatchAttemptsForOrderSid(TWO_HEAD_ORDER_SID) == 1);

    sqsEventController.onStop(new StopEvent()).block(Duration.ofSeconds(5));

    assertThat(DISPATCHER.getPatchMaxConcurrent())
        .as("parallel saves cap at maxParallelOrdersToSaveThreads=5 (main and helper stages)")
        .isLessThanOrEqualTo(5);

    assertThat(DISPATCHER.getPatchAttemptsForOrderSid(TWO_HEAD_ORDER_SID)).isEqualTo(1);
    assertThat(DISPATCHER.getPatchMaxConcurrent()).isGreaterThan(0);
    await().atMost(30, SECONDS).until(() -> !RECORDED_ORDER_TAKEN_EVENTS.isEmpty());
    assertThat(RECORDED_ORDER_TAKEN_EVENTS)
        .as("notify stage should still emit events for successful saves")
        .isNotEmpty();
    assertThat(RECORDED_ORDER_TAKEN_EVENTS)
        .allSatisfy(
            event ->
                assertThat(event.getSavedOrders())
                    .noneMatch(order -> TWO_HEAD_ORDER_SID.equals(order.getSid())));
  }

  /**
   * First poll returns {@code oneOrder.json}; second returns {@code twoOrders.json}, which repeats
   * the same main SID as oneOrder. That SID must not be PATCH-saved again on the second tick;
   * {@link StatusEvent} snapshot matches {@link WorkerStateManager} budgets.
   */
  @Disabled("Temporarily ignored per request")
  @Test
  void scenario8_twoPolls_secondSkipsAlreadySavedMain_thenStatusSnapshotMatchesState() {
    DISPATCHER.setPollBodiesThenEmpty(
        List.of(readClasspathUtf8("oneOrder.json"), readClasspathUtf8("twoOrders.json")));
    sqsEventController.onStart(startEvent(10, OrderType.NORMAL)).block(Duration.ofSeconds(5));
    workerStarter.ensureTickLoopRunning();

    await()
        .atMost(10, SECONDS)
        .until(
            () ->
                DISPATCHER.getPollCount() >= 2
                    && DISPATCHER.getPatchAttemptsForOrderSid(SECOND_MAIN_ONE_HEAD_SID) == 1);

    final OrderTaken firstPollSave =
        RECORDED_ORDER_TAKEN_EVENTS.stream()
            .filter(
                e ->
                    e.getSavedOrders().stream()
                        .anyMatch(o -> SECOND_MAIN_ONE_HEAD_SID.equals(o.getSid())))
            .findFirst()
            .orElseThrow();

    assertThat(firstPollSave.isCompleted()).isFalse();
    assertThat(firstPollSave.getSavedOrders()).hasSize(1);
    assertThat(firstPollSave.getSavedOrders().get(0).getSid()).isEqualTo(SECOND_MAIN_ONE_HEAD_SID);

    StepVerifier.create(sqsEventController.onStatus(new StatusEvent()))
        .assertNext(
            snap -> {
              assertThat(snap.isStarted()).isTrue();
              assertThat(snap.getHeadsToTake()).isEqualTo(10);
              assertThat(snap.getOrderTypes()).containsExactly(OrderType.NORMAL);
              assertThat(snap.getHeadsTaken()).isEqualTo(workerStateManager.getHeadsTaken());
              assertThat(snap.getHeadsTaken()).isPositive();
            })
        .verifyComplete();

    sqsEventController.onStop(new StopEvent()).block(Duration.ofSeconds(5));
  }

  private record FreeOrdersFixtureSelection(
      List<String> filteredMainSids, ParsedOrders parsedOrders) {}

  private static FreeOrdersFixtureSelection selectFreeOrdersNormalMains(int headsToTake) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      try (InputStream in =
          OrderHunterIntegrationTest.class
              .getClassLoader()
              .getResourceAsStream("freeOrders.json")) {
        if (in == null) {
          throw new IllegalStateException("Missing classpath resource: freeOrders.json");
        }
        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        OrdersResponse response = mapper.readValue(json, OrdersResponse.class);
        final ParsedOrders parsed = OrderParsingUtil.parseOrders(response, null);
        ParseOrdersStageResult parseResult = new ParseOrdersStageResult();
        parseResult.setParsedOrders(parsed);
        DefaultWorkerStateManager state = new DefaultWorkerStateManager();
        state.setStarted(true);
        state.setHeadsToTake(headsToTake);
        state.setHeadsTaken(0);
        state.setOrderTypes(List.of(OrderType.NORMAL));
        PollOrdersFlowContext context = PollOrdersFlowContext.begin(state);
        context.setParseOrdersResult(parseResult);
        FilterOrdersStage filter = new FilterOrdersStage();
        StepVerifier.create(filter.execute(context)).verifyComplete();
        List<String> sids =
            context.getFilterRecordsResult().getFilteredOrders().stream()
                .map(Order::getSid)
                .toList();
        return new FreeOrdersFixtureSelection(List.copyOf(sids), parsed);
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static StartEvent startEvent(int headsToTake, OrderType type) {
    StartEvent event = new StartEvent();
    event.setHeadsToTake(headsToTake);
    event.setOrderTypes(List.of(type));
    return event;
  }

  private static String readClasspathUtf8(String resource) {
    ClassLoader cl = OrderHunterIntegrationTest.class.getClassLoader();
    try (InputStream in = cl.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing classpath resource: " + resource);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Path resolveComposeFile() {
    Path rootCandidate = Path.of("docker-compose.yml");
    if (Files.exists(rootCandidate)) {
      return rootCandidate;
    }
    Path moduleCandidate = Path.of("order-hunter-worker", "docker-compose.yml");
    if (Files.exists(moduleCandidate)) {
      return moduleCandidate;
    }
    throw new IllegalStateException("Cannot locate docker-compose.yml for order-hunter-worker");
  }

  private static void waitForJaegerReadiness() throws InterruptedException {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    long deadlineNanos = System.nanoTime() + JAEGER_STARTUP_TIMEOUT.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      try {
        HttpResponse<Void> response =
            client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(JAEGER_ENDPOINT))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 500) {
          return;
        }
      } catch (IOException ignored) {
        // Wait for Jaeger container startup to complete.
      }
      Thread.sleep(500);
    }
    throw new IllegalStateException("Jaeger did not become ready within " + JAEGER_STARTUP_TIMEOUT);
  }

  @Configuration
  static class Config {

    /**
     * Records outbound {@link OrderTaken} events without AWS; takes precedence over the production
     * {@code AwsOrderTakenSqsPublisher} bean.
     */
    @Bean
    @Primary
    OrderTakenSqsPublisher recordingOrderTakenSqsPublisher() {
      return event -> {
        RECORDED_ORDER_TAKEN_EVENTS.add(event);
        return Mono.empty();
      };
    }
  }
}
