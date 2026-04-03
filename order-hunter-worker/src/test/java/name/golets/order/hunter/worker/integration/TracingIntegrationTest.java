package name.golets.order.hunter.worker.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.concurrent.atomic.AtomicReference;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.common.utils.OrderParsingUtil;
import name.golets.order.hunter.worker.OrderHunterWorkerApplication;
import name.golets.order.hunter.worker.controller.SqsEventController;
import name.golets.order.hunter.worker.event.OrderTaken;
import name.golets.order.hunter.worker.event.StartEvent;
import name.golets.order.hunter.worker.event.StopEvent;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.integration.sqs.OrderTakenSqsPublisher;
import name.golets.order.hunter.worker.integration.support.AirportalMockDispatcher;
import name.golets.order.hunter.worker.stage.FilterOrdersStage;
import name.golets.order.hunter.worker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.worker.starter.WorkerStarter;
import name.golets.order.hunter.worker.state.DefaultWorkerStateManager;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

/** Integration test that validates Micrometer-to-OTLP trace export into Jaeger for worker flows. */
@SpringBootTest(classes = {OrderHunterWorkerApplication.class, TracingIntegrationTest.Config.class})
@AutoConfigureTracing
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TracingIntegrationTest {

  private static final String JAEGER_UI_ENDPOINT = "http://localhost:16686/";
  private static final String JAEGER_TRACES_ENDPOINT =
      "http://localhost:16686/api/traces?service=order-hunter-worker&limit=20";
  private static final String FLOW_CYCLE_SPAN_NAME = "order-hunter.flow.cycle";
  private static final String SQS_START_SPAN_NAME = "order-hunter.sqs.command.start";
  private static final String SQS_STOP_SPAN_NAME = "order-hunter.sqs.command.stop";
  private static final String SQS_PUBLISH_SPAN_NAME = "order-hunter.sqs.publish.orderTaken";
  private static final String STATISTICS_SPAN_NAME = "order-hunter.flow.statistics";
  private static final Duration JAEGER_STARTUP_TIMEOUT = Duration.ofSeconds(30);
  private static final String TWO_HEAD_ORDER_SID = "ov2_recLQ1ExOBR4FuUjm";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final AtomicReference<OrderTaken> LAST_ORDER_TAKEN_EVENT = new AtomicReference<>();
  private static final AirportalMockDispatcher DISPATCHER = new AirportalMockDispatcher();
  private static final MockWebServer MOCK_WEB_SERVER;
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

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

  @BeforeAll
  static void restartJaegerForCleanTraceState() throws InterruptedException {
    Assumptions.assumeTrue(
        isDockerComposeAvailable(), "Docker compose is required for tracing test");
    runDockerCompose("up", "-d", "jaeger");
    runDockerCompose("restart", "jaeger");
    waitForJaegerReadiness();
  }

  @AfterAll
  static void stopMockServer() throws IOException {
    MOCK_WEB_SERVER.shutdown();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "order-hunter.airportal-host", () -> "http://127.0.0.1:" + MOCK_WEB_SERVER.getPort());
    registry.add("order-hunter.worker-auto-start", () -> "false");
  }

  @BeforeEach
  void resetDispatcher() {
    DISPATCHER.reset();
    LAST_ORDER_TAKEN_EVENT.set(null);
  }

  /**
   * Reuses the scenario-7 behavior: first filtered main save fails while remaining mains complete
   * in parallel, then verifies Jaeger contains one flow-cycle root span with airportal WebClient
   * child spans and traced SQS command spans.
   */
  @Test
  void scenario7_emitsTracesVisibleInJaeger() {
    FreeOrdersFixtureSelection selection = selectFreeOrdersNormalMains(10);
    final List<String> filteredMainSids = selection.filteredMainSids();
    assertThat(filteredMainSids).startsWith(TWO_HEAD_ORDER_SID);

    DISPATCHER.setPatchHandlingDelayMs(120);
    DISPATCHER.setThreeEmptyThenPayloadThenEmpty(readClasspathUtf8("freeOrders.json"));
    DISPATCHER.setPatchHttpStatus(TWO_HEAD_ORDER_SID, 500);

    sqsEventController.onStart(startEvent(10, OrderType.NORMAL)).block(Duration.ofSeconds(5));
    workerStarter.ensureTickLoopRunning();

    await()
        .atMost(90, SECONDS)
        .until(
            () ->
                filteredMainSids.stream()
                    .allMatch(sid -> DISPATCHER.getPatchAttemptsForOrderSid(sid) >= 1));

    sqsEventController.onStop(new StopEvent()).block(Duration.ofSeconds(5));

    await().atMost(180, SECONDS).until(TracingIntegrationTest::hasFlowCycleSpan);
    await().atMost(90, SECONDS).until(TracingIntegrationTest::hasPopulatedStatisticsStateTags);
    assertThat(hasFlowCycleSpan()).as("Jaeger API should show flow root span").isTrue();
    assertThat(hasOrderKindTagValue("main"))
        .as("Jaeger API should show WebClient span with order.kind=main")
        .isTrue();
    assertThat(hasOrderKindTagValue("helper"))
        .as("Jaeger API should show WebClient span with order.kind=helper")
        .isTrue();
    assertThat(hasClientUrlTag())
        .as("Jaeger API should show automatic HTTP URL tags on WebClient spans")
        .isTrue();
    assertThat(hasStatisticsStageSpan())
        .as("Jaeger API should show statistics span with polled and filtered order tags")
        .isTrue();
    assertThat(hasPopulatedStatisticsStateTags())
        .as("Jaeger API should show non-empty statistics state tags")
        .isTrue();
    assertThat(hasWebClientServerErrorTag())
        .as("Jaeger API should mark failed WebClient span with error=true and error.type")
        .isTrue();
    assertThat(hasSqsCommandSpans())
        .as("Jaeger API should show traced SQS start/stop command spans")
        .isTrue();
    assertThat(hasSqsPublishSpan()).as("Jaeger API should show traced SQS publish span").isTrue();
    assertThat(hasSqsPublishSpanWithSimplifiedOrderTakenTag())
        .as("Jaeger API should include simplified orderTaken payload on SQS publish span")
        .isTrue();
    assertThat(LAST_ORDER_TAKEN_EVENT.get())
        .as("integration test should capture published OrderTaken payload")
        .isNotNull();
    assertThat(LAST_ORDER_TAKEN_EVENT.get().getSavedOrders())
        .as("published OrderTaken payload should include saved orders")
        .isNotEmpty();
  }

  private static boolean isDockerComposeAvailable() {
    try {
      Process process =
          new ProcessBuilder("docker", "compose", "version").redirectErrorStream(true).start();
      int exitCode = process.waitFor();
      return exitCode == 0;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }

  private static void runDockerCompose(String... args) throws InterruptedException {
    try {
      String[] command = new String[args.length + 4];
      command[0] = "docker";
      command[1] = "compose";
      command[2] = "-f";
      command[3] = resolveComposeFile().toString();
      System.arraycopy(args, 0, command, 4, args.length);
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IllegalStateException(
            "docker compose command failed with exit code=" + exitCode + ", output=" + output);
      }
    } catch (IOException e) {
      throw new IllegalStateException("docker compose command failed", e);
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
    long deadlineNanos = System.nanoTime() + JAEGER_STARTUP_TIMEOUT.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      try {
        HttpResponse<Void> response =
            HTTP_CLIENT.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(JAEGER_UI_ENDPOINT))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 500) {
          return;
        }
      } catch (IOException ignored) {
        // Wait for Jaeger HTTP endpoint startup.
      }
      Thread.sleep(500);
    }
    throw new IllegalStateException("Jaeger did not become ready within " + JAEGER_STARTUP_TIMEOUT);
  }

  private static boolean hasFlowCycleSpan() {
    try {
      HttpResponse<String> response =
          HTTP_CLIENT.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(JAEGER_TRACES_ENDPOINT))
                  .timeout(Duration.ofSeconds(3))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        return false;
      }
      JsonNode root = OBJECT_MAPPER.readTree(response.body());
      for (JsonNode trace : root.path("data")) {
        for (JsonNode span : trace.path("spans")) {
          if (FLOW_CYCLE_SPAN_NAME.equals(span.path("operationName").asText(""))) {
            return true;
          }
        }
      }
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean hasOrderKindTagValue(String expectedValue) {
    try {
      HttpResponse<String> response =
          HTTP_CLIENT.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(JAEGER_TRACES_ENDPOINT))
                  .timeout(Duration.ofSeconds(3))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        return false;
      }
      JsonNode root = OBJECT_MAPPER.readTree(response.body());
      for (JsonNode trace : root.path("data")) {
        for (JsonNode span : trace.path("spans")) {
          if (hasTag(span, "order.kind", expectedValue)) {
            return true;
          }
        }
      }
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean hasClientUrlTag() {
    try {
      HttpResponse<String> response =
          HTTP_CLIENT.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(JAEGER_TRACES_ENDPOINT))
                  .timeout(Duration.ofSeconds(3))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        return false;
      }
      JsonNode root = OBJECT_MAPPER.readTree(response.body());
      for (JsonNode trace : root.path("data")) {
        for (JsonNode span : trace.path("spans")) {
          if (hasTagKeyContaining(span, "url")) {
            return true;
          }
        }
      }
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean hasTag(JsonNode span, String key, String value) {
    for (JsonNode tag : span.path("tags")) {
      if (key.equals(tag.path("key").asText("")) && value.equals(tag.path("value").asText(""))) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasTagKeyContaining(JsonNode span, String fragment) {
    for (JsonNode tag : span.path("tags")) {
      String key = tag.path("key").asText("");
      if (key.contains(fragment)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasStatisticsStageSpan() {
    try {
      HttpResponse<String> response =
          HTTP_CLIENT.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(JAEGER_TRACES_ENDPOINT))
                  .timeout(Duration.ofSeconds(3))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        return false;
      }
      JsonNode root = OBJECT_MAPPER.readTree(response.body());
      for (JsonNode trace : root.path("data")) {
        for (JsonNode span : trace.path("spans")) {
          if (!STATISTICS_SPAN_NAME.equals(span.path("operationName").asText(""))) {
            continue;
          }
          if (hasTagKeyContaining(span, "orders.main.polled")
              && hasTagKeyContaining(span, "orders.helpers.polled")
              && hasTagKeyContaining(span, "orders.main.filtered")
              && hasTagValueContaining(span, "orders.main.polled", "\"heads\":")
              && hasTagValueContaining(span, "orders.helpers.polled", "\"heads\":")
              && hasTagValueContaining(span, "orders.main.filtered", "\"heads\":")
              && hasTagKeyContaining(span, "state.headsToTake")
              && hasTagKeyContaining(span, "state.headsTaken")) {
            return true;
          }
        }
      }
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean hasTagValueContaining(JsonNode span, String key, String valueFragment) {
    for (JsonNode tag : span.path("tags")) {
      if (!key.equals(tag.path("key").asText(""))) {
        continue;
      }
      String tagValue = tag.path("value").asText("");
      if (tagValue.contains(valueFragment)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasWebClientServerErrorTag() {
    try {
      HttpResponse<String> response =
          HTTP_CLIENT.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(JAEGER_TRACES_ENDPOINT))
                  .timeout(Duration.ofSeconds(3))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        return false;
      }
      JsonNode root = OBJECT_MAPPER.readTree(response.body());
      for (JsonNode trace : root.path("data")) {
        for (JsonNode span : trace.path("spans")) {
          if (!"http patch".equals(span.path("operationName").asText(""))) {
            continue;
          }
          if (hasTag(span, "status", "500")
              && hasTag(span, "error", "true")
              && hasTag(span, "error.type", "500")) {
            return true;
          }
        }
      }
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean hasSqsCommandSpans() {
    try {
      HttpResponse<String> response =
          HTTP_CLIENT.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(JAEGER_TRACES_ENDPOINT))
                  .timeout(Duration.ofSeconds(3))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        return false;
      }
      boolean hasStart = false;
      boolean hasStop = false;
      JsonNode root = OBJECT_MAPPER.readTree(response.body());
      for (JsonNode trace : root.path("data")) {
        for (JsonNode span : trace.path("spans")) {
          String operationName = span.path("operationName").asText("");
          if (SQS_START_SPAN_NAME.equals(operationName)) {
            hasStart = true;
          } else if (SQS_STOP_SPAN_NAME.equals(operationName)) {
            hasStop = true;
          }
        }
      }
      return hasStart && hasStop;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean hasSqsPublishSpan() {
    try {
      HttpResponse<String> response =
          HTTP_CLIENT.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(JAEGER_TRACES_ENDPOINT))
                  .timeout(Duration.ofSeconds(3))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        return false;
      }
      JsonNode root = OBJECT_MAPPER.readTree(response.body());
      for (JsonNode trace : root.path("data")) {
        for (JsonNode span : trace.path("spans")) {
          if (SQS_PUBLISH_SPAN_NAME.equals(span.path("operationName").asText(""))) {
            return true;
          }
        }
      }
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean hasSqsPublishSpanWithSimplifiedOrderTakenTag() {
    try {
      HttpResponse<String> response =
          HTTP_CLIENT.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(JAEGER_TRACES_ENDPOINT))
                  .timeout(Duration.ofSeconds(3))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        return false;
      }
      JsonNode root = OBJECT_MAPPER.readTree(response.body());
      for (JsonNode trace : root.path("data")) {
        for (JsonNode span : trace.path("spans")) {
          if (!SQS_PUBLISH_SPAN_NAME.equals(span.path("operationName").asText(""))) {
            continue;
          }
          if (hasTagValueContaining(span, "orderTaken", "\"savedOrders\":[")
              && hasTagValueContaining(span, "orderTaken", "\"sid\"")
              && hasTagValueContaining(span, "orderTaken", "\"heads\"")
              && !hasTagValueContaining(span, "orderTaken", "\"artist\"")) {
            return true;
          }
        }
      }
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean hasPopulatedStatisticsStateTags() {
    try {
      HttpResponse<String> response =
          HTTP_CLIENT.send(
              HttpRequest.newBuilder()
                  .uri(URI.create(JAEGER_TRACES_ENDPOINT))
                  .timeout(Duration.ofSeconds(3))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        return false;
      }
      JsonNode root = OBJECT_MAPPER.readTree(response.body());
      for (JsonNode trace : root.path("data")) {
        for (JsonNode span : trace.path("spans")) {
          if (!STATISTICS_SPAN_NAME.equals(span.path("operationName").asText(""))) {
            continue;
          }
          if (hasTagValueNotEqual(span, "state.headsToTake", "0")
              && hasTagKeyContaining(span, "state.headsTaken")
              && hasTagKeyContaining(span, "state.savedOrderSidsCount")) {
            return true;
          }
        }
      }
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  private static boolean hasTagValueNotEqual(JsonNode span, String key, String excludedValue) {
    for (JsonNode tag : span.path("tags")) {
      if (!key.equals(tag.path("key").asText(""))) {
        continue;
      }
      return !excludedValue.equals(tag.path("value").asText(""));
    }
    return false;
  }

  private static FreeOrdersFixtureSelection selectFreeOrdersNormalMains(int headsToTake) {
    try {
      try (InputStream in =
          TracingIntegrationTest.class.getClassLoader().getResourceAsStream("freeOrders.json")) {
        if (in == null) {
          throw new IllegalStateException("Missing classpath resource: freeOrders.json");
        }
        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        OrdersResponse response = OBJECT_MAPPER.readValue(json, OrdersResponse.class);
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
    ClassLoader cl = TracingIntegrationTest.class.getClassLoader();
    try (InputStream in = cl.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing classpath resource: " + resource);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private record FreeOrdersFixtureSelection(
      List<String> filteredMainSids, ParsedOrders parsedOrders) {}

  @Configuration
  static class Config {

    /**
     * Avoids AWS in integration tests by recording outbound order-taken events in-memory.
     *
     * @return test publisher implementation
     */
    @Bean
    @Primary
    OrderTakenSqsPublisher recordingOrderTakenSqsPublisher() {
      return event -> {
        LAST_ORDER_TAKEN_EVENT.set(event);
        return Mono.empty();
      };
    }
  }
}
