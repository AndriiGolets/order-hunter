package name.golets.order.hunter.worker.stage;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.worker.flow.FlowObservationContextKeys;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import name.golets.order.hunter.worker.stage.inputs.StatisticStageInput;
import name.golets.order.hunter.worker.stage.results.FilterRecordsStageResult;
import name.golets.order.hunter.worker.stage.results.SaveHelpersStageResult;
import name.golets.order.hunter.worker.stage.results.SaveMainOrdersStageResult;
import name.golets.order.hunter.worker.util.JsonUtil;
import org.slf4j.Marker;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Emits one end-of-run statistics observation with polled and filtered order snapshots.
 *
 * <p>The stage is a no-op when no main orders were parsed for the current flow run.
 */
@Component
public class StatisticStage
    extends AbstractStage<
        PollOrdersFlowContext, StatisticStageInput, StatisticStage.StatisticStageResult> {
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

  @Override
  protected StatisticStageInput prepareInput(PollOrdersFlowContext context) {
    return new StatisticStageInput(
        context,
        context.getParseOrdersResult() != null
            ? context.getParseOrdersResult().getParsedOrders()
            : null);
  }

  @Override
  protected Mono<StatisticStageResult> process(StatisticStageInput input) {
    return Mono.deferContextual(
        contextView ->
            Mono.fromSupplier(
                () -> {
                  ParsedOrders parsedOrders = input.getParsedOrders();
                  if (parsedOrders == null || parsedOrders.getOrdersMapBySid().isEmpty()) {
                    return new StatisticStageResult(false);
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
                    appendOrderTags(observation, parsedOrders, input.getContext());
                    appendStateTags(observation, input.getContext());
                  } finally {
                    observation.stop();
                  }
                  return new StatisticStageResult(true);
                }));
  }

  @Override
  protected void storeResult(PollOrdersFlowContext context, StatisticStageResult result) {
    // No flow context write is needed for statistics stage.
  }

  @Override
  protected Marker marker(PollOrdersFlowContext context) {
    return context.getSessionMarker();
  }

  @Override
  protected String stageName() {
    return "statisticStage";
  }

  private void appendOrderTags(
      Observation observation, ParsedOrders parsedOrders, PollOrdersFlowContext context) {
    List<Order> mainPolled =
        parsedOrders.getOrdersMapBySid().values().stream().filter(Objects::nonNull).toList();
    List<Order> helpersPolled =
        parsedOrders.getOrdersHelperMapByName().values().stream()
            .filter(Objects::nonNull)
            .map(Map::values)
            .flatMap(values -> values.stream())
            .filter(Objects::nonNull)
            .toList();
    List<Order> mainFiltered = filteredMainOrders(context);

    observation.highCardinalityKeyValue(
        "orders.main.polled", JsonUtil.toSimplifiedOrdersJson(mainPolled));
    observation.highCardinalityKeyValue(
        "orders.helpers.polled", JsonUtil.toSimplifiedOrdersJson(helpersPolled));
    observation.highCardinalityKeyValue(
        "orders.main.filtered", JsonUtil.toSimplifiedOrdersJson(mainFiltered));
  }

  private void appendStateTags(Observation observation, PollOrdersFlowContext context) {
    observation.lowCardinalityKeyValue(
        "state.started", Boolean.toString(context.isStartedAtFlowStart()));
    observation.lowCardinalityKeyValue(
        "state.headsToTake", Integer.toString(Math.max(0, context.getHeadsToTakeAtFlowStart())));
    observation.lowCardinalityKeyValue("state.headsTaken", Integer.toString(savedHeads(context)));
    observation.lowCardinalityKeyValue(
        "state.savedOrderSidsCount", Integer.toString(savedOrderSidsCount(context)));
    observation.highCardinalityKeyValue(
        "state.orderTypes", context.getOrderTypesAtFlowStart().toString());
    observation.highCardinalityKeyValue(
        "state.takenOrderSids", new TreeSet<>(savedSids(context)).toString());
    observation.highCardinalityKeyValue(
        "state.sessionId", defaultText(context.getSessionIdAtFlowStart()));
    observation.highCardinalityKeyValue(
        "state.hunterId", defaultText(context.getHunterIdAtFlowStart()));
    observation.highCardinalityKeyValue("state.flowRunId", defaultText(context.getFlowRunId()));
  }

  private static List<Order> filteredMainOrders(PollOrdersFlowContext context) {
    FilterRecordsStageResult filterRecordsResult = context.getFilterRecordsResult();
    if (filterRecordsResult == null) {
      return List.of();
    }
    return filterRecordsResult.getFilteredOrders();
  }

  private static int savedHeads(PollOrdersFlowContext context) {
    return savedOrders(context).stream().mapToInt(order -> Math.max(0, order.getHeads())).sum();
  }

  private static int savedOrderSidsCount(PollOrdersFlowContext context) {
    return savedSids(context).size();
  }

  private static HashSet<String> savedSids(PollOrdersFlowContext context) {
    HashSet<String> sids = new HashSet<>();
    savedOrders(context).stream().map(Order::getSid).filter(Objects::nonNull).forEach(sids::add);
    return sids;
  }

  private static List<Order> savedOrders(PollOrdersFlowContext context) {
    SaveMainOrdersStageResult mainResult = context.getSaveMainOrdersResult();
    SaveHelpersStageResult helperResult = context.getSaveHelpersResult();
    List<Order> main = mainResult != null ? mainResult.getSavedOrders() : List.of();
    List<Order> helpers = helperResult != null ? helperResult.getSavedOrders() : List.of();
    return java.util.stream.Stream.concat(main.stream(), helpers.stream()).toList();
  }

  private static String defaultText(String value) {
    return value != null ? value : "";
  }

  record StatisticStageResult(boolean emitted) {
    @Override
    public String toString() {
      return JsonUtil.toOneLineJson(this);
    }
  }
}
