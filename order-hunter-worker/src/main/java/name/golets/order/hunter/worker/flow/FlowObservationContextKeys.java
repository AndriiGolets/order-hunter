package name.golets.order.hunter.worker.flow;

/** Reactor-context keys used to propagate flow observation metadata across stages. */
public final class FlowObservationContextKeys {

  public static final String FLOW_OBSERVATION = "order-hunter.flow.observation";
  public static final String SAVE_ORDER_KIND = "order-hunter.save.order.kind";
  public static final String SAVE_PRODUCT_TITLE = "order-hunter.save.product.title";
  public static final String SAVE_BEFORE_SAVES_DELAY = "order-hunter.save.before.saves.delay";

  private FlowObservationContextKeys() {}
}
