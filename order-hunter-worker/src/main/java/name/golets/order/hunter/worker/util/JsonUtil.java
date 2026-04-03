package name.golets.order.hunter.worker.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.event.OrderTaken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared JSON conversion helpers for worker observability payloads. */
public final class JsonUtil {
  private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  private JsonUtil() {}

  /**
   * Serializes outbound {@link OrderTaken} to a compact observability payload.
   *
   * <p>Saved orders are reduced to a stable subset of fields to avoid serialization issues from
   * full domain objects and to keep observation tags readable.
   *
   * @param event outbound event
   * @return JSON string of simplified event payload; "{}" on serialization failure
   */
  public static String toOrderTakenObservationJson(OrderTaken event) {
    if (event == null) {
      return "{}";
    }
    OrderTakenObservation payload =
        new OrderTakenObservation(
            defaultText(event.getEventVersion()),
            event.getProducedAt() != null ? event.getProducedAt().toString() : "",
            event.isCompleted(),
            toSimplifiedOrders(event.getSavedOrders()));
    try {
      return OBJECT_MAPPER.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize OrderTaken observation payload", e);
      return "{}";
    }
  }

  /**
   * Serializes orders as simplified records for observation tags.
   *
   * @param orders domain orders
   * @return JSON array with stable order subset; "[]" on serialization failure
   */
  public static String toSimplifiedOrdersJson(List<Order> orders) {
    try {
      return OBJECT_MAPPER.writeValueAsString(toSimplifiedOrders(orders));
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize simplified orders payload", e);
      return "[]";
    }
  }

  private static List<SimplifiedOrder> toSimplifiedOrders(List<Order> orders) {
    if (orders == null) {
      return List.of();
    }
    return orders.stream()
        .filter(Objects::nonNull)
        .map(JsonUtil::toSimplifiedOrder)
        .sorted(SimplifiedOrder.COMPARATOR)
        .toList();
  }

  private static SimplifiedOrder toSimplifiedOrder(Order order) {
    return new SimplifiedOrder(
        defaultText(order.getSid()),
        defaultText(order.getName()),
        defaultText(order.getProductTitle()),
        Math.max(0, order.getHeads()));
  }

  private static String defaultText(String value) {
    return value != null ? value : "";
  }

  private record OrderTakenObservation(
      String eventVersion,
      String producedAt,
      boolean completed,
      List<SimplifiedOrder> savedOrders) {}

  private record SimplifiedOrder(String sid, String name, String productTitle, int heads) {
    private static final Comparator<SimplifiedOrder> COMPARATOR =
        Comparator.comparing(SimplifiedOrder::sid)
            .thenComparing(SimplifiedOrder::name)
            .thenComparing(SimplifiedOrder::productTitle)
            .thenComparingInt(SimplifiedOrder::heads);
  }
}
