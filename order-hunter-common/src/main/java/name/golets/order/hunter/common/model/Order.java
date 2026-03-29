package name.golets.order.hunter.common.model;

import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

/** Parsed order domain model shared between manager and worker modules. */
@Data
@Accessors(chain = true)
public class Order implements Comparable<Order>, Serializable {

  private static final long serialVersionUID = 1L;

  private String sid;
  private String name;
  private String artist;
  private String artistName;
  private String productTitle;
  private String date;
  private String productVariant;
  private String photoNotOk;
  private String designStatus;
  private boolean additionalPet;
  private int heads = 1;
  private boolean recordHelper;
  private long created;

  private OrderType orderType = OrderType.NORMAL;

  @Override
  public int compareTo(Order o) {
    return Integer.compare(o.getHeads(), heads);
  }
}
