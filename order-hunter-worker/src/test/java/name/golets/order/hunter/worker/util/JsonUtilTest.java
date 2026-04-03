package name.golets.order.hunter.worker.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.event.OrderTaken;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.worker.stage.results.PollRecordsStageResult;
import name.golets.order.hunter.worker.stage.results.SaveHelpersStageResult;
import name.golets.order.hunter.worker.stage.results.SaveMainOrdersStageResult;
import org.junit.jupiter.api.Test;

/** Verifies stable JSON payload generation used by observation tags. */
class JsonUtilTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  /**
   * Ensures order-taken JSON uses simplified saved-order fields and deterministic ordering.
   *
   * @throws JsonProcessingException when JSON parsing fails
   */
  @Test
  void toOrderTakenObservationJson_serializesSimplifiedSavedOrders()
      throws JsonProcessingException {
    OrderTaken event = new OrderTaken();
    event.setEventVersion("1.0");
    event.setProducedAt(Instant.parse("2026-04-03T10:15:30Z"));
    event.setCompleted(true);
    event.setSavedOrders(
        List.of(
            new SimplifiedOrder("sid-2", "Name B", "Product B", 5),
            new SimplifiedOrder("sid-1", "Name A", "Product A", 0)));

    String json = JsonUtil.toOrderTakenObservationJson(event);
    JsonNode root = OBJECT_MAPPER.readTree(json);
    JsonNode savedOrders = root.path("savedOrders");

    assertThat(root.path("eventVersion").asText()).isEqualTo("1.0");
    assertThat(root.path("producedAt").asText()).isEqualTo("2026-04-03T10:15:30Z");
    assertThat(root.path("completed").asBoolean()).isTrue();
    assertThat(savedOrders).hasSize(2);
    assertThat(savedOrders.get(0).path("sid").asText()).isEqualTo("sid-1");
    assertThat(savedOrders.get(0).path("heads").asInt()).isZero();
    assertThat(savedOrders.get(1).path("sid").asText()).isEqualTo("sid-2");
    assertThat(savedOrders.get(1).path("heads").asInt()).isEqualTo(5);

    Set<String> fields = fieldNames(savedOrders.get(0));
    assertThat(fields).containsExactlyInAnyOrder("sid", "name", "productTitle", "heads");
  }

  /**
   * Ensures null inputs still produce valid empty JSON array payload.
   *
   * @throws JsonProcessingException when JSON parsing fails
   */
  @Test
  void toSimplifiedOrdersJson_nullInputReturnsEmptyJsonArray() throws JsonProcessingException {
    String json = JsonUtil.toSimplifiedOrdersJson(null);
    JsonNode root = OBJECT_MAPPER.readTree(json);

    assertThat(root.isArray()).isTrue();
    assertThat(root).isEmpty();
  }

  /**
   * Ensures generic one-line JSON conversion returns compact JSON on a single line.
   *
   * @throws JsonProcessingException when JSON parsing fails
   */
  @Test
  void toOneLineJson_returnsCompactSingleLineJson() throws JsonProcessingException {
    String json = JsonUtil.toOneLineJson(new Payload("value", 7));

    assertThat(json).doesNotContain("\n");
    JsonNode root = OBJECT_MAPPER.readTree(json);
    assertThat(root.path("name").asText()).isEqualTo("value");
    assertThat(root.path("count").asInt()).isEqualTo(7);
  }

  /**
   * Ensures stage result toString implementations return valid single-line JSON payloads.
   *
   * @throws JsonProcessingException when JSON parsing fails
   */
  @Test
  void stageResultToString_outputsValidJson() throws JsonProcessingException {
    PollRecordsStageResult poll = new PollRecordsStageResult();
    poll.getOrdersResponse()
        .setRecords(List.of(new name.golets.order.hunter.common.model.Record().setSid("sid-1")));

    ParseOrdersStageResult parse = new ParseOrdersStageResult();
    parse
        .getParsedOrders()
        .getOrdersMapBySid()
        .put("sid-1", new Order().setSid("sid-1").setHeads(2));

    FilterRecordsStageResult filter = new FilterRecordsStageResult();
    filter.addFilteredOrder(new Order().setSid("sid-1").setHeads(2));

    SaveMainOrdersStageResult saveMain = new SaveMainOrdersStageResult();
    saveMain.addSavedOrder(new Order().setSid("sid-1").setHeads(2));

    SaveHelpersStageResult saveHelpers = new SaveHelpersStageResult();
    saveHelpers.addSavedOrder(new Order().setSid("helper-1").setHeads(1));

    assertSingleLineJson(poll.toString(), "recordsCount");
    assertSingleLineJson(parse.toString(), "mainCount");
    assertSingleLineJson(filter.toString(), "filteredCount");
    assertSingleLineJson(saveMain.toString(), "savedMainCount");
    assertSingleLineJson(saveHelpers.toString(), "savedHelpersCount");
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new HashSet<>();
    Iterator<String> iterator = node.fieldNames();
    iterator.forEachRemaining(names::add);
    return names;
  }

  private static void assertSingleLineJson(String json, String expectedField)
      throws JsonProcessingException {
    assertThat(json).doesNotContain("\n");
    JsonNode root = OBJECT_MAPPER.readTree(json);
    assertThat(root.has(expectedField)).isTrue();
  }

  private record Payload(String name, int count) {}
}
