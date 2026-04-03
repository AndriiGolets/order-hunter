package name.golets.order.hunter.worker.util;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import name.golets.order.hunter.common.model.Order;

/** Maps domain orders to deterministic simplified projections for logging. */
public final class SimplifiedOrdersMapper {
  private static final Comparator<SimplifiedOrder> COMPARATOR =
      Comparator.comparing(SimplifiedOrder::getSid)
          .thenComparing(SimplifiedOrder::getName)
          .thenComparing(SimplifiedOrder::getProductTitle)
          .thenComparingInt(SimplifiedOrder::getHeads);

  private SimplifiedOrdersMapper() {}

  /**
   * Maps domain orders to simplified orders with stable ordering.
   *
   * @param orders domain orders
   * @return sorted simplified orders list
   */
  public static List<SimplifiedOrder> map(List<Order> orders) {
    if (orders == null) {
      return List.of();
    }
    return orders.stream()
        .filter(Objects::nonNull)
        .map(SimplifiedOrdersMapper::toSimplifiedOrder)
        .sorted(COMPARATOR)
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
}
