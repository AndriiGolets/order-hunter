package name.golets.order.hunter.orderhunterworker.stage;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.common.utils.OrderParsingUtil;
import name.golets.order.hunter.orderhunterworker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.orderhunterworker.stage.results.ParseOrdersStageResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Transforms poll records into {@link name.golets.order.hunter.common.model.Order} using parsing
 * rules.
 */
@Component
public class ParseOrdersStage implements Stage<PollOrdersFlowContext> {

  /**
   * Parses raw records from {@code PollRecordsStageResult} into domain orders and stores them in
   * flow context.
   *
   * <p>When {@code WorkerStateManager#getOrderTypes()} is empty, all parsed types are kept.
   *
   * @param context flow session context
   * @return completion after parsed result is written into context
   */
  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.fromRunnable(
        () -> {
          ParseOrdersStageResult result = new ParseOrdersStageResult();
          if (context.getPollRecordsResult() == null) {
            context.setParseOrdersResult(result);
            return;
          }

          OrdersResponse response = new OrdersResponse();
          response.setRecords(new ArrayList<>(context.getPollRecordsResult().getRecords()));
          ParsedOrders parsedOrders = OrderParsingUtil.parseOrders(response, null);

          Set<name.golets.order.hunter.common.enums.OrderType> allowedTypes =
              toAllowedTypes(context);

          for (Order order : parsedOrders.getOrdersMapBySid().values()) {
            if (isAllowed(order, allowedTypes)) {
              result.addOrder(order);
            }
          }

          for (Map<String, Order> helperMap : parsedOrders.getOrdersHelperMapByName().values()) {
            for (Order helperOrder : helperMap.values()) {
              if (isAllowed(helperOrder, allowedTypes)) {
                result.addOrder(helperOrder);
              }
            }
          }

          context.setParseOrdersResult(result);
        });
  }

  private static Set<name.golets.order.hunter.common.enums.OrderType> toAllowedTypes(
      PollOrdersFlowContext context) {
    if (context.getStateManager() == null || context.getStateManager().getOrderTypes().isEmpty()) {
      return EnumSet.allOf(name.golets.order.hunter.common.enums.OrderType.class);
    }
    return EnumSet.copyOf(context.getStateManager().getOrderTypes());
  }

  private static boolean isAllowed(
      Order order, Set<name.golets.order.hunter.common.enums.OrderType> allowedTypes) {
    return order != null && allowedTypes.contains(order.getOrderType());
  }
}
