package name.golets.order.hunter.worker.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.event.OrderTaken;

/** Shared JSON conversion helpers for worker observability payloads. */
public final class JsonUtil {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  private JsonUtil() {}

  /**
   * Serializes outbound {@link OrderTaken} to a compact observability payload.
   *
   * <p>Saved orders are reduced to a stable subset of fields to avoid serialization issues from
   * full domain objects and to keep observation tags readable.
   *
   * @param event outbound event
   * @return JSON string of simplified event payload
   */
  public static String toOrderTakenObservationJson(OrderTaken event) {
    if (event == null) {
      return toOneLineJson(new OrderTakenObservation("", "", false, List.of()));
    }
    OrderTakenObservation payload =
        new OrderTakenObservation(
            defaultText(event.getEventVersion()),
            event.getProducedAt() != null ? event.getProducedAt().toString() : "",
            event.isCompleted(),
            SimplifiedOrdersMapper.map(event.getSavedOrders()));
    return toOneLineJson(payload);
  }

  /**
   * Serializes orders as simplified records for observation tags.
   *
   * @param orders domain orders
   * @return JSON array with stable order subset
   */
  public static String toSimplifiedOrdersJson(List<Order> orders) {
    return toOneLineJson(SimplifiedOrdersMapper.map(orders));
  }

  private static String defaultText(String value) {
    return value != null ? value : "";
  }

  private record OrderTakenObservation(
      String eventVersion,
      String producedAt,
      boolean completed,
      List<SimplifiedOrder> savedOrders) {}

  /**
   * Converts object to one-line JSON.
   *
   * <p>Serialization failures are treated as application errors and are not swallowed.
   *
   * @param value source object
   * @return one-line JSON representation
   */
  public static String toOneLineJson(Object value) {
    try {
      return OBJECT_MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize object to JSON", e);
    }
  }
}
