package name.golets.order.hunter.orderhunterworker.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.Order;
import org.springframework.stereotype.Component;

/** In-memory implementation of {@link WorkerStateManager} (skeleton). */
@Component
public class DefaultWorkerStateManager implements WorkerStateManager {

  private final Object lock = new Object();
  private boolean started;
  private int headsToTake;
  private int headsTaken;
  private List<OrderType> orderTypes = new ArrayList<>();
  private final Set<String> takenOrderSids = new HashSet<>();
  private String sessionId = "";
  private String hunterId = "";

  @Override
  public boolean isStarted() {
    synchronized (lock) {
      return started;
    }
  }

  @Override
  public void setStarted(boolean started) {
    synchronized (lock) {
      this.started = started;
    }
  }

  @Override
  public int getHeadsToTake() {
    synchronized (lock) {
      return headsToTake;
    }
  }

  @Override
  public void setHeadsToTake(int headsToTake) {
    synchronized (lock) {
      this.headsToTake = headsToTake;
    }
  }

  @Override
  public int getHeadsTaken() {
    synchronized (lock) {
      return headsTaken;
    }
  }

  @Override
  public void setHeadsTaken(int headsTaken) {
    synchronized (lock) {
      this.headsTaken = headsTaken;
    }
  }

  @Override
  public List<OrderType> getOrderTypes() {
    synchronized (lock) {
      return List.copyOf(orderTypes);
    }
  }

  @Override
  public void setOrderTypes(List<OrderType> orderTypes) {
    synchronized (lock) {
      this.orderTypes = orderTypes != null ? new ArrayList<>(orderTypes) : new ArrayList<>();
    }
  }

  @Override
  public Set<String> getTakenOrderSids() {
    synchronized (lock) {
      return Collections.unmodifiableSet(new HashSet<>(takenOrderSids));
    }
  }

  @Override
  public void registerSuccessfulSave(Order order) {
    synchronized (lock) {
      if (order != null && order.getSid() != null) {
        takenOrderSids.add(order.getSid());
      }
      if (order != null) {
        headsTaken += order.getHeads();
      }
    }
  }

  @Override
  public String getSessionId() {
    synchronized (lock) {
      return sessionId;
    }
  }

  @Override
  public void setSessionId(String sessionId) {
    synchronized (lock) {
      this.sessionId = sessionId != null ? sessionId : "";
    }
  }

  @Override
  public String getHunterId() {
    synchronized (lock) {
      return hunterId;
    }
  }

  @Override
  public void setHunterId(String hunterId) {
    synchronized (lock) {
      this.hunterId = hunterId != null ? hunterId : "";
    }
  }

  @Override
  public WorkerStatusSnapshot toSnapshot() {
    synchronized (lock) {
      WorkerStatusSnapshot s = new WorkerStatusSnapshot();
      s.setStarted(started);
      s.setHeadsToTake(headsToTake);
      s.setHeadsTaken(headsTaken);
      s.setOrderTypes(List.copyOf(orderTypes));
      s.setSessionId(sessionId);
      s.setHunterId(hunterId);
      return s;
    }
  }
}
