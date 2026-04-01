package name.golets.order.hunter.orderhunterworker.stage.results;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import name.golets.order.hunter.common.flow.StageResult;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.orderhunterworker.stage.SaveMainOrdersStage;

/** Main orders successfully persisted via PATCH in {@link SaveMainOrdersStage}. */
@Getter
public class SaveMainOrdersStageResult implements StageResult<SaveMainOrdersStage> {

  @Getter(AccessLevel.NONE)
  private final List<Order> savedOrders = new ArrayList<>();

  @Override
  public Class<SaveMainOrdersStage> stageType() {
    return SaveMainOrdersStage.class;
  }

  public List<Order> getSavedOrders() {
    return List.copyOf(savedOrders);
  }

  /** Records a main order that was saved successfully. */
  public void addSavedOrder(Order order) {
    if (order != null) {
      savedOrders.add(order);
    }
  }
}
