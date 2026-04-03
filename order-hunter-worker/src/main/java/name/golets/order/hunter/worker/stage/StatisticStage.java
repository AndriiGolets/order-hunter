package name.golets.order.hunter.worker.stage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.worker.flow.FlowObservationContextKeys;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.state.WorkerStateManager;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Emits one end-of-run statistics observation with polled and filtered order snapshots.
 *
 * <p>The stage is a no-op when no main orders were parsed for the current flow run.
 */
@Component
public class StatisticStage implements Stage<PollOrdersFlowContext> {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String SPAN_NAME = "order-hunter.flow.statistics";

  private final ObservationRegistry observationRegistry;

  /**
   * Creates statistics stage dependencies.
   *
   * @param observationRegistry registry used to publish stage observation
   */
  public StatisticStage(ObservationRegistry observationRegistry) {
    this.observationRegistry = observationRegistry;
  }

  /**
   * Emits a dedicated statistics observation when parsed main orders are present in flow context.
   *
   * @param context flow session context
   * @return completion after statistics observation is emitted
   */
  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.deferContextual(
        contextView ->
            Mono.fromRunnable(
                () -> {
                  ParsedOrders parsedOrders =
                      context.getParseOrdersResult() != null
                          ? context.getParseOrdersResult().getParsedOrders()
                          : null;
                  if (parsedOrders == null || parsedOrders.getOrdersMapBySid().isEmpty()) {
                    return;
                  }
                  Observation observation =
                      Observation.createNotStarted(SPAN_NAME, observationRegistry)
                          .lowCardinalityKeyValue("stage", "statistics");
                  if (contextView.hasKey(FlowObservationContextKeys.FLOW_OBSERVATION)) {
                    Object parent = contextView.get(FlowObservationContextKeys.FLOW_OBSERVATION);
                    if (parent instanceof Observation parentObservation) {
                      observation.parentObservation(parentObservation);
                    }
                  }
                  observation.start();
                  try {
                    appendOrderTags(observation, parsedOrders, context);
                    appendStateTags(observation, context);
                  } finally {
                    observation.stop();
                  }
                }));
  }

  private void appendOrderTags(
      Observation observation, ParsedOrders parsedOrders, PollOrdersFlowContext context) {
    List<SimplifiedOrder> mainPolled =
        parsedOrders.getOrdersMapBySid().values().stream()
            .filter(Objects::nonNull)
            .map(this::toSimplifiedOrder)
            .sorted(SimplifiedOrder.COMPARATOR)
            .toList();
    List<SimplifiedOrder> helpersPolled =
        parsedOrders.getOrdersHelperMapByName().values().stream()
            .filter(Objects::nonNull)
            .map(Map::values)
            .flatMap(values -> values.stream())
            .filter(Objects::nonNull)
            .map(this::toSimplifiedOrder)
            .sorted(SimplifiedOrder.COMPARATOR)
            .toList();
    List<SimplifiedOrder> mainFiltered =
        filteredMainOrders(context).stream()
            .map(this::toSimplifiedOrder)
            .sorted(SimplifiedOrder.COMPARATOR)
            .toList();

    observation.highCardinalityKeyValue("orders.main.polled", toJson(mainPolled));
    observation.highCardinalityKeyValue("orders.helpers.polled", toJson(helpersPolled));
    observation.highCardinalityKeyValue("orders.main.filtered", toJson(mainFiltered));
  }

  private void appendStateTags(Observation observation, PollOrdersFlowContext context) {
    WorkerStateManager stateManager = context.getStateManager();
    if (stateManager == null) {
      return;
    }
    observation.lowCardinalityKeyValue("state.started", Boolean.toString(stateManager.isStarted()));
    observation.lowCardinalityKeyValue(
        "state.headsToTake", Integer.toString(stateManager.getHeadsToTake()));
    observation.lowCardinalityKeyValue(
        "state.headsTaken", Integer.toString(stateManager.getHeadsTaken()));
    observation.lowCardinalityKeyValue(
        "state.savedOrderSidsCount", Integer.toString(stateManager.getSavedOrderSids().size()));
    observation.highCardinalityKeyValue(
        "state.orderTypes", stateManager.getOrderTypes().toString());
    observation.highCardinalityKeyValue(
        "state.takenOrderSids", new TreeSet<>(stateManager.getSavedOrderSids()).toString());
    observation.highCardinalityKeyValue(
        "state.sessionId", defaultText(stateManager.getSessionId()));
    observation.highCardinalityKeyValue("state.hunterId", defaultText(stateManager.getHunterId()));
    observation.highCardinalityKeyValue("state.flowRunId", defaultText(context.getFlowRunId()));
  }

  private static List<Order> filteredMainOrders(PollOrdersFlowContext context) {
    FilterRecordsStageResult filterRecordsResult = context.getFilterRecordsResult();
    if (filterRecordsResult == null) {
      return List.of();
    }
    return filterRecordsResult.getFilteredOrders();
  }

  private SimplifiedOrder toSimplifiedOrder(Order order) {
    return new SimplifiedOrder(
        defaultText(order.getSid()),
        defaultText(order.getName()),
        defaultText(order.getProductTitle()),
        Math.max(0, order.getHeads()));
  }

  private static String defaultText(String value) {
    return value != null ? value : "";
  }

  private static String toJson(Object value) {
    try {
      return OBJECT_MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }

  private record SimplifiedOrder(String sid, String name, String productTitle, int heads) {
    private static final Comparator<SimplifiedOrder> COMPARATOR =
        Comparator.comparing(SimplifiedOrder::sid)
            .thenComparing(SimplifiedOrder::name)
            .thenComparing(SimplifiedOrder::productTitle)
            .thenComparingInt(SimplifiedOrder::heads);
  }
}
