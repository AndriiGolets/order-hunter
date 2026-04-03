package name.golets.order.hunter.worker.state;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.Order;
import org.springframework.stereotype.Component;

/**
 * In-memory implementation of {@link WorkerStateManager} backed by an atomic active task session.
 *
 * <p>This class keeps the current interface contract while routing state reads and writes through
 * {@link TaskSession}.
 */
@Component
public class DefaultWorkerStateManager implements WorkerStateManager {

  private static final String EMPTY_VALUE = "";

  private final AtomicReference<TaskSession> activeSession = new AtomicReference<>();
  private final AtomicReference<String> configuredHunterId = new AtomicReference<>(EMPTY_VALUE);

  @Override
  public boolean isStarted() {
    return activeSession.get() != null;
  }

  @Override
  public void setStarted(boolean started) {
    if (!started) {
      activeSession.set(null);
      return;
    }
    ensureActiveSession();
  }

  @Override
  public int getHeadsToTake() {
    TaskSession session = activeSession.get();
    return session != null ? session.getHeadsToTake() : 0;
  }

  @Override
  public void setHeadsToTake(int headsToTake) {
    updateSession(session -> session.withHeadsToTake(headsToTake));
  }

  @Override
  public int getHeadsTaken() {
    TaskSession session = activeSession.get();
    return session != null ? session.getHeadsTaken() : 0;
  }

  @Override
  public void setHeadsTaken(int headsTaken) {
    TaskSession session = activeSession.get();
    if (session == null) {
      session = ensureActiveSession();
    }
    session.setHeadsTaken(headsTaken);
  }

  @Override
  public List<OrderType> getOrderTypes() {
    TaskSession session = activeSession.get();
    return session != null ? session.getOrderTypes() : List.of();
  }

  @Override
  public void setOrderTypes(List<OrderType> orderTypes) {
    updateSession(session -> session.withOrderTypes(orderTypes));
  }

  @Override
  public Set<String> getSavedOrderSids() {
    TaskSession session = activeSession.get();
    return session != null ? session.getSavedOrderSids() : Set.of();
  }

  @Override
  public void registerSuccessfulSave(Order order) {
    TaskSession session = activeSession.get();
    if (session != null) {
      session.registerSuccessfulSave(order);
    }
  }

  @Override
  public String getSessionId() {
    TaskSession session = activeSession.get();
    return session != null ? session.getSessionId() : EMPTY_VALUE;
  }

  @Override
  public void setSessionId(String sessionId) {
    updateSession(session -> session.withSessionId(sessionId));
  }

  @Override
  public String getHunterId() {
    TaskSession session = activeSession.get();
    return session != null ? session.getHunterId() : configuredHunterId.get();
  }

  @Override
  public void setHunterId(String hunterId) {
    String safeValue = hunterId != null ? hunterId : EMPTY_VALUE;
    configuredHunterId.set(safeValue);
    updateSession(session -> session.withHunterId(safeValue));
  }

  @Override
  public WorkerStatusSnapshot toSnapshot() {
    WorkerStatusSnapshot snapshot = new WorkerStatusSnapshot();
    TaskSession session = activeSession.get();
    snapshot.setStarted(session != null);
    snapshot.setHunterId(configuredHunterId.get());
    if (session == null) {
      snapshot.setHeadsToTake(0);
      snapshot.setHeadsTaken(0);
      snapshot.setOrderTypes(List.of());
      snapshot.setSessionId(EMPTY_VALUE);
      return snapshot;
    }
    snapshot.setHeadsToTake(session.getHeadsToTake());
    snapshot.setHeadsTaken(session.getHeadsTaken());
    snapshot.setOrderTypes(session.getOrderTypes());
    snapshot.setSessionId(session.getSessionId());
    snapshot.setHunterId(session.getHunterId());
    snapshot.setLastFlowStartedAt(session.getStartedAt());
    return snapshot;
  }

  private TaskSession ensureActiveSession() {
    TaskSession existing = activeSession.get();
    if (existing != null) {
      return existing;
    }
    TaskSession created =
        new TaskSession(EMPTY_VALUE, configuredHunterId.get(), 0, List.of(), Instant.now());
    if (activeSession.compareAndSet(null, created)) {
      return created;
    }
    return activeSession.get();
  }

  private void updateSession(UnaryOperator<TaskSession> mutator) {
    while (true) {
      TaskSession current = activeSession.get();
      TaskSession base = current != null ? current : ensureActiveSession();
      TaskSession updated = mutator.apply(base);
      if (activeSession.compareAndSet(base, updated)) {
        return;
      }
    }
  }
}
