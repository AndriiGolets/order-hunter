package name.golets.order.hunter.worker.stage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Filters parsed main orders by business rules and selects minimum subset to satisfy head target.
 */
@Component
public class FilterRecordsStage implements Stage<PollOrdersFlowContext> {
  private static final Logger log = LoggerFactory.getLogger(FilterRecordsStage.class);

  /**
   * Filters only {@code parsedOrders.ordersMapBySid} entries by allowed order types and already
   * saved SIDs, then selects orders by descending heads until required target is reached.
   *
   * @param context flow session context
   * @return completion after filtered result is written into context
   */
  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.fromRunnable(
        () -> {
          FilterRecordsStageResult result = new FilterRecordsStageResult();
          if (!isContextReady(context)) {
            context.setFilterRecordsResult(result);
            return;
          }

          List<Order> candidateOrders = collectCandidates(context);
          int targetHeads = resolveTargetHeads(context);
          List<Order> selectedOrders = selectOrdersForTarget(candidateOrders, targetHeads);
          selectedOrders.forEach(result::addFilteredOrder);
          int collectedHeads = countHeads(selectedOrders);

          context.setFilterRecordsResult(result);
          log.debug(
              context.getSessionMarker(),
              "filterRecordsStage result stored for flowRunId={} selectedCount={} selectedHeads={}",
              context.getFlowRunId(),
              result.getFilteredOrders().size(),
              collectedHeads);
        });
  }

  private static boolean isContextReady(PollOrdersFlowContext context) {
    return context.getStateManager() != null
        && context.getParseOrdersResult() != null
        && context.getParseOrdersResult().getParsedOrders() != null;
  }

  private static List<Order> collectCandidates(PollOrdersFlowContext context) {
    ParsedOrders parsedOrders = context.getParseOrdersResult().getParsedOrders();
    Set<String> savedSids = context.getStateManager().getSavedOrderSids();
    Set<OrderType> allowedTypes = resolveAllowedTypes(context);
    Comparator<Order> byHeadsDescThenSid =
        Comparator.comparingInt(Order::getHeads)
            .reversed()
            .thenComparing(Order::getSid, Comparator.nullsLast(String::compareTo));

    return parsedOrders.getOrdersMapBySid().values().stream()
        .filter(order -> order != null && order.getSid() != null)
        .filter(order -> !savedSids.contains(order.getSid()))
        .filter(order -> allowedTypes.contains(order.getOrderType()))
        .sorted(byHeadsDescThenSid)
        .collect(Collectors.toList());
  }

  private static int resolveTargetHeads(PollOrdersFlowContext context) {
    return Math.max(0, context.getStateManager().getHeadsToTake());
  }

  private static List<Order> selectOrdersForTarget(List<Order> candidateOrders, int targetHeads) {
    if (targetHeads == 0) {
      return List.copyOf(candidateOrders);
    }
    List<Order> selectedOrders = new ArrayList<>();
    int collectedHeads = 0;
    for (Order order : candidateOrders) {
      if (collectedHeads >= targetHeads) {
        break;
      }
      selectedOrders.add(order);
      collectedHeads += Math.max(0, order.getHeads());
    }
    return selectedOrders;
  }

  private static int countHeads(List<Order> selectedOrders) {
    return selectedOrders.stream().mapToInt(order -> Math.max(0, order.getHeads())).sum();
  }

  private static Set<OrderType> resolveAllowedTypes(PollOrdersFlowContext context) {
    if (context.getStateManager().getOrderTypes().isEmpty()) {
      return EnumSet.allOf(OrderType.class);
    }
    return EnumSet.copyOf(context.getStateManager().getOrderTypes());
  }
}
