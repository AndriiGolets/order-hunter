package name.golets.order.hunter.orderhunterworker.stage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.orderhunterworker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.orderhunterworker.stage.results.FilterRecordsStageResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Selects a minimum subset of main orders to reach the head budget, preferring higher head counts
 * and excluding already-taken {@code sid} values.
 */
@Component
public class FilterRecordsStage implements Stage<PollOrdersFlowContext> {

  /**
   * Selects main orders up to required remaining heads with descending-head preference and excludes
   * already processed SIDs.
   *
   * <p>Helpers are collected only for selected mains by matching {@code order.name}.
   *
   * @param context flow session context
   * @return completion after filtered result is written into context
   */
  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.fromRunnable(
        () -> {
          FilterRecordsStageResult result = new FilterRecordsStageResult();
          if (context.getParseOrdersResult() == null || context.getStateManager() == null) {
            context.setFilterRecordsResult(result);
            return;
          }

          Set<String> takenSids = context.getStateManager().getTakenOrderSids();
          int remainingHeads =
              Math.max(
                  0,
                  context.getStateManager().getHeadsToTake()
                      - context.getStateManager().getHeadsTaken());

          List<Order> mainOrders = new ArrayList<>();
          Map<String, List<Order>> helpersByName = new HashMap<>();
          for (Order order : context.getParseOrdersResult().getOrders()) {
            if (order == null || order.getSid() == null || takenSids.contains(order.getSid())) {
              continue;
            }
            if (order.isRecordHelper()) {
              helpersByName.computeIfAbsent(order.getName(), key -> new ArrayList<>()).add(order);
            } else {
              mainOrders.add(order);
            }
          }

          mainOrders.sort(
              Comparator.comparingInt(Order::getHeads)
                  .reversed()
                  .thenComparing(Order::getSid, Comparator.nullsLast(String::compareTo)));

          int selectedHeads = 0;
          for (Order mainOrder : mainOrders) {
            if (remainingHeads == 0 || selectedHeads >= remainingHeads) {
              break;
            }
            result.addMainOrder(mainOrder);
            selectedHeads += Math.max(0, mainOrder.getHeads());
          }

          for (Order selectedMain : result.getMainOrders()) {
            List<Order> helpers = helpersByName.getOrDefault(selectedMain.getName(), List.of());
            for (Order helper : helpers) {
              result.addHelperOrder(helper);
            }
          }

          context.setFilterRecordsResult(result);
        });
  }
}
