package name.golets.order.hunter.worker.util;

/** Lightweight order projection used in logs and observation payloads. */
public final class SimplifiedOrder {
  private final String sid;
  private final String name;
  private final String productTitle;
  private final int heads;

  /**
   * Creates simplified order projection.
   *
   * @param sid order sid
   * @param name order name
   * @param productTitle order product title
   * @param heads normalized non-negative heads value
   */
  public SimplifiedOrder(String sid, String name, String productTitle, int heads) {
    this.sid = sid;
    this.name = name;
    this.productTitle = productTitle;
    this.heads = heads;
  }

  public String getSid() {
    return sid;
  }

  public String getName() {
    return name;
  }

  public String getProductTitle() {
    return productTitle;
  }

  public int getHeads() {
    return heads;
  }
}
