package site.golets.ordersearch.common.model;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ParsedOrders {

  /** Main orders map by sid. Have to be saved in the first place. */
  private Map<String, Order> ordersMapBySid = new HashMap<>();

  /**
   * Helper orders map. Each main order can have several helper orders. Map of helper orders by sid
   * grouped by main order sid.
   */
  private Map<String, Map<String, Order>> ordersHelperMapByName = new HashMap<>();

  public ParsedOrders merge(ParsedOrders po) {
    if (po == null) return this;
    ordersMapBySid.putAll(po.getOrdersMapBySid());
    po.getOrdersHelperMapByName()
        .forEach(
            (name, helpers) -> {
              ordersHelperMapByName.merge(
                  name,
                  helpers,
                  (existing, provided) -> {
                    existing.putAll(provided);
                    return existing;
                  });
            });
    return this;
  }

  public ParsedOrders merge(ParsedOrders po1, ParsedOrders po2) {

    ordersMapBySid.putAll(po1.getOrdersMapBySid());
    ordersHelperMapByName.putAll(po1.getOrdersHelperMapByName());
    ordersMapBySid.putAll(po2.getOrdersMapBySid());
    ordersHelperMapByName.putAll(po2.getOrdersHelperMapByName());
    return this;
  }
}
