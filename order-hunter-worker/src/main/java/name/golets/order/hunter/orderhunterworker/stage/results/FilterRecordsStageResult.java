package name.golets.order.hunter.orderhunterworker.stage.results;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import name.golets.order.hunter.common.flow.StageResult;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.orderhunterworker.stage.FilterRecordsStage;

/** Filtered main orders and helper references for save stages. */
@Getter
public class FilterRecordsStageResult implements StageResult<FilterRecordsStage> {

  @Getter(AccessLevel.NONE)
  private final List<Order> mainOrders = new ArrayList<>();

  @Getter(AccessLevel.NONE)
  private final List<Order> helperOrders = new ArrayList<>();

  @Override
  public Class<FilterRecordsStage> stageType() {
    return FilterRecordsStage.class;
  }

  public List<Order> getMainOrders() {
    return List.copyOf(mainOrders);
  }

  public List<Order> getHelperOrders() {
    return List.copyOf(helperOrders);
  }

  /** Adds a main order selected for saving. */
  public void addMainOrder(Order order) {
    if (order != null) {
      mainOrders.add(order);
    }
  }

  /** Adds a helper order linked to selected mains. */
  public void addHelperOrder(Order order) {
    if (order != null) {
      helperOrders.add(order);
    }
  }
}
