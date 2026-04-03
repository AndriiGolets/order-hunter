package name.golets.order.hunter.worker.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import name.golets.order.hunter.common.flow.StageInput;
import name.golets.order.hunter.worker.util.JsonUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Verifies shared AbstractStage execution contract and error propagation behavior. */
class AbstractStageTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  /**
   * Ensures abstract wrapper runs prepare, process, and store in order for successful execution.
   */
  @Test
  void execute_success_preparesProcessesAndStoresResult() {
    AtomicReference<Integer> storedResult = new AtomicReference<>(0);
    TestStage stage = new TestStage(storedResult);

    StepVerifier.create(stage.execute(5)).verifyComplete();

    assertEquals(10, storedResult.get());
  }

  /** Ensures process failures are propagated and never swallowed by wrapper orchestration. */
  @Test
  void execute_whenProcessFails_propagatesError() {
    FailingStage stage = new FailingStage();

    StepVerifier.create(stage.execute(5))
        .expectErrorSatisfies(error -> assertEquals("boom", error.getMessage()))
        .verify();
  }

  /** Ensures sample stage input/result toString methods produce valid one-line JSON output. */
  @Test
  void inputAndResultToString_areOneLineJson() throws JsonProcessingException {
    String inputJson = new TestInput("a-1", 5).toString();
    String resultJson = new TestResult(2).toString();

    assertTrue(inputJson.indexOf('\n') < 0);
    assertTrue(resultJson.indexOf('\n') < 0);
    assertEquals("a-1", OBJECT_MAPPER.readTree(inputJson).path("sid").asText());
    assertEquals(2, OBJECT_MAPPER.readTree(resultJson).path("saved").asInt());
  }

  private static final class TestStage extends AbstractStage<Integer, TestInput, Integer> {
    private final AtomicReference<Integer> storedResult;

    private TestStage(AtomicReference<Integer> storedResult) {
      this.storedResult = storedResult;
    }

    @Override
    protected TestInput prepareInput(Integer context) {
      return new TestInput("sid-test", context);
    }

    @Override
    protected Mono<Integer> process(TestInput input) {
      return Mono.just(input.heads() * 2);
    }

    @Override
    protected void storeResult(Integer context, Integer result) {
      storedResult.set(result);
    }

    @Override
    protected Marker marker(Integer context) {
      return MarkerFactory.getMarker("test");
    }
  }

  private static final class FailingStage extends AbstractStage<Integer, TestInput, Integer> {
    @Override
    protected TestInput prepareInput(Integer context) {
      return new TestInput("sid-test", context);
    }

    @Override
    protected Mono<Integer> process(TestInput input) {
      return Mono.error(new IllegalStateException("boom"));
    }

    @Override
    protected void storeResult(Integer context, Integer result) {
      // not used
    }

    @Override
    protected Marker marker(Integer context) {
      return MarkerFactory.getMarker("test");
    }
  }

  private static final class TestInput implements StageInput {
    private final String sid;
    private final int heads;

    private TestInput(String sid, int heads) {
      this.sid = sid;
      this.heads = heads;
    }

    String sid() {
      return sid;
    }

    int heads() {
      return heads;
    }

    public String getSid() {
      return sid;
    }

    public int getHeads() {
      return heads;
    }

    @Override
    public String toString() {
      return JsonUtil.toOneLineJson(this);
    }
  }

  private record TestResult(int saved) {
    @Override
    public String toString() {
      return JsonUtil.toOneLineJson(this);
    }
  }
}
