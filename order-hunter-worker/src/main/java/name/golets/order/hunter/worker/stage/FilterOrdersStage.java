package name.golets.order.hunter.worker.stage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.inputs.FilterOrdersStageInput;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.stage.results.ParseOrdersStageResult;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import org.slf4j.Marker;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Filters parsed main orders by business rules and selects minimum subset to satisfy head target.
 */
@Component
public class FilterOrdersStage
    extends AbstractStage<PollOrdersFlowContext, FilterOrdersStageInput, FilterRecordsStageResult> {

  /**
   * Filters only {@code parsedOrders.ordersMapBySid} entries by allowed order types and already
   * saved SIDs, then selects orders by descending heads until required target is reached.
   *
   * @param context flow session context
   * @return completion after filtered result is written into context
   */
  @Override
  protected FilterOrdersStageInput prepareInput(PollOrdersFlowContext context) {
    ParseOrdersStageResult parseResult = context.getParseOrdersResult();
    return new FilterOrdersStageInput(
        context.getStateManager(), parseResult != null ? parseResult.getParsedOrders() : null);
  }

  @Override
  protected Mono<FilterRecordsStageResult> process(FilterOrdersStageInput input) {
    return Mono.fromSupplier(
        () -> {
          FilterRecordsStageResult result = new FilterRecordsStageResult();
          if (!isContextReady(input)) {
            return result;
          }

          List<Order> candidateOrders = collectCandidates(input);
          int targetHeads = resolveTargetHeads(input.getStateManager());
          List<Order> selectedOrders = selectOrdersForTarget(candidateOrders, targetHeads);
          selectedOrders.forEach(result::addFilteredOrder);
          return result;
        });
  }

  @Override
  protected void storeResult(PollOrdersFlowContext context, FilterRecordsStageResult result) {
    context.setFilterRecordsResult(result);
  }

  @Override
  protected Marker marker(PollOrdersFlowContext context) {
    return context.getSessionMarker();
  }

  @Override
  protected String stageName() {
    return "filterOrdersStage";
  }

  private static boolean isContextReady(FilterOrdersStageInput input) {
    return input.getStateManager() != null && input.getParsedOrders() != null;
  }

  private static List<Order> collectCandidates(FilterOrdersStageInput input) {
    Set<String> savedSids = input.getStateManager().getSavedOrderSids();
    Set<OrderType> allowedTypes = resolveAllowedTypes(input.getStateManager());
    Comparator<Order> byHeadsDescThenSid =
        Comparator.comparingInt(Order::getHeads)
            .reversed()
            .thenComparing(Order::getSid, Comparator.nullsLast(String::compareTo));

    return input.getParsedOrders().getOrdersMapBySid().values().stream()
        .filter(order -> order != null && order.getSid() != null)
        .filter(order -> !savedSids.contains(order.getSid()))
        .filter(order -> allowedTypes.contains(order.getOrderType()))
        .sorted(byHeadsDescThenSid)
        .collect(Collectors.toList());
  }

  private static int resolveTargetHeads(WorkerStateManager stateManager) {
    return Math.max(0, stateManager.getHeadsToTake());
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

  private static Set<OrderType> resolveAllowedTypes(WorkerStateManager stateManager) {
    if (stateManager.getOrderTypes().isEmpty()) {
      return EnumSet.allOf(OrderType.class);
    }
    return EnumSet.copyOf(stateManager.getOrderTypes());
  }
}
