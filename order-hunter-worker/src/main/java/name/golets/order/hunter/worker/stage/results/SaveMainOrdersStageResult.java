package name.golets.order.hunter.worker.stage.results;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import name.golets.order.hunter.common.flow.StageResult;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.worker.stage.SaveMainOrdersStage;
import name.golets.order.hunter.worker.util.JsonUtil;
import name.golets.order.hunter.worker.util.SimplifiedOrder;
import name.golets.order.hunter.worker.util.SimplifiedOrdersMapper;

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

  @Override
  public String toString() {
    return JsonUtil.toOneLineJson(
        new ResultLogPayload(savedOrders.size(), SimplifiedOrdersMapper.map(savedOrders)));
  }

  private record ResultLogPayload(int savedMainCount, List<SimplifiedOrder> savedOrders) {}
}
