package site.golets.ordersearch.common.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.golets.ordersearch.common.constants.OrderConstants;
import site.golets.ordersearch.common.model.Order;
import site.golets.ordersearch.common.model.OrderType;
import site.golets.ordersearch.common.model.OrdersResponse;
import site.golets.ordersearch.common.model.ParsedOrders;
import site.golets.ordersearch.common.model.Record;

public class OrderParsingUtil {

  private static final Logger log = LoggerFactory.getLogger(OrderParsingUtil.class);
  private static final String YES = "Yes";
  private static final String[] ORDER_HELPER_PREFIXES = OrderConstants.orderHelperTitles();
  private static final String[] BLACKLIST_TITLES_LOWER =
      Arrays.stream(OrderConstants.blackListTitles())
          .map(title -> title.toLowerCase(Locale.ROOT))
          .toArray(String[]::new);

  public static ParsedOrders parseOrders(OrdersResponse response, OrderType orderType) {
    List<Record> records = response != null ? response.getRecords() : null;
    if (records == null || records.isEmpty()) {
      return new ParsedOrders();
    }

    int expectedSize = records.size();
    Map<String, Order> orderMapBySid = new HashMap<>(expectedSize);
    Map<String, Map<String, Order>> orderHelperMapByName = new HashMap<>(expectedSize);
    List<Record> orderRecords = new ArrayList<>(expectedSize);
    Map<String, Record> titleRecordMap = new HashMap<>(expectedSize);
    Map<String, Record> artistRecordMap = new HashMap<>(expectedSize);

    for (Record record : records) {
      if (record == null) {
        continue;
      }
      String objectId = record.getObjectId();
      if (OrderConstants.TITLE_OBJECT_ID.equals(objectId)) {
        titleRecordMap.put(record.getSid(), record);
      } else if (OrderConstants.ARTIST_OBJECT_ID.equals(objectId)) {
        artistRecordMap.put(record.getSid(), record);
      } else if (OrderConstants.ORDER_OBJECT_ID.equals(objectId)) {
        orderRecords.add(record);
      } else {
        log.warn("Unknown record: {}", record);
      }
    }

    for (Record orderRecord : orderRecords) {
      Record titleRecord = titleRecordMap.get(orderRecord.getOrderProductTitle());
      String productTitle = titleRecord != null ? titleRecord.getPrimary() : null;
      if (productTitle == null) {
        log.warn("Product title is null for order: {}", orderRecord);
        continue;
      }

      Record artistRecord = artistRecordMap.get(orderRecord.getArtist());
      String artistName = artistRecord != null ? artistRecord.getPrimary() : null;
      OrderType currentOrderType = orderType != null ? orderType : identifyOrderType(orderRecord);
      long now = System.currentTimeMillis();
      boolean recordHelper = isOrderHelper(productTitle);

      Order order =
          new Order()
              .setSid(orderRecord.getSid())
              .setDate(orderRecord.getOrderDate())
              .setName(orderRecord.getOrderName())
              .setProductTitle(productTitle)
              .setCreated(now)
              .setOrderType(currentOrderType)
              .setArtist(orderRecord.getArtist())
              .setArtistName(artistName)
              .setDesignStatus(orderRecord.getDesignStatus())
              .setRecordHelper(recordHelper)
              .setAdditionalPet(YES.equalsIgnoreCase(orderRecord.getAdditionalPet()));

      boolean validOrder = isValidOrder(order);
      if (!validOrder) {
        continue;
      }

      if (recordHelper) {
        Map<String, Order> orderHelperMapBySid =
            orderHelperMapByName.computeIfAbsent(order.getName(), key -> new HashMap<>());
        orderHelperMapBySid.put(order.getSid(), order);
      } else {
        orderMapBySid.put(order.getSid(), order);
      }
    }
    return new ParsedOrders()
        .setOrdersMapBySid(orderMapBySid)
        .setOrdersHelperMapByName(orderHelperMapByName);
  }

  private static OrderType identifyOrderType(Record orderRecord) {
    String fastTrack = orderRecord.getSourceFastTrackOrder();
    if (YES.equalsIgnoreCase(fastTrack)) {
      return OrderType.FAST;
    }

    String shipping = orderRecord.getShippingTitle();
    if (shipping != null) {
      String shippingLower = shipping.toLowerCase(Locale.ROOT);
      if (shippingLower.contains("free") || shippingLower.contains("standard")) {
        return OrderType.NORMAL;
      }
      return OrderType.PRIORITY;
    }

    return OrderType.NORMAL;
  }

  private static boolean isValidOrder(Order order) {
    boolean isValidOrder =
        !isInBlackList(order.getProductTitle())
            && !isTemplate(order.getName())
            && isValidDesignStatus(order.getDesignStatus());
    log.debug("isValidOrder check: {}", isValidOrder);
    return isValidOrder;
  }

  private static boolean isValidDesignStatus(String designStatus) {
    log.debug("Design Status check: {}", designStatus);
    return designStatus == null || designStatus.length() < 4;
  }

  private static boolean isOrderHelper(String productTitle) {
    for (String helperPrefix : ORDER_HELPER_PREFIXES) {
      if (productTitle.startsWith(helperPrefix)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isInBlackList(String productTitle) {
    String normalizedProductTitle = productTitle.toLowerCase(Locale.ROOT);
    for (String blackListTitle : BLACKLIST_TITLES_LOWER) {
      if (normalizedProductTitle.contains(blackListTitle)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isTemplate(String name) {
    return name.endsWith("_T");
  }
}
