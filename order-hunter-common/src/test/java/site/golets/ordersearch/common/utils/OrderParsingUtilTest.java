package site.golets.ordersearch.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.golets.ordersearch.common.constants.OrderConstants;
import site.golets.ordersearch.common.model.Order;
import site.golets.ordersearch.common.model.OrderType;
import site.golets.ordersearch.common.model.OrdersResponse;
import site.golets.ordersearch.common.model.ParsedOrders;
import site.golets.ordersearch.common.model.Record;

class OrderParsingUtilTest {

  /**
   * Each orderRecord in OrderResponse can have fields orderProductTitle and artist and they are
   * mapped by sid to the corresponding Record objects: titleRecord and artistRecord. There are two
   * types of orderRecord: root order and order helper.
   *
   * <p>Order field productTitle is set from linked titleRecord.primary field. Order field artist is
   * set by linked artist SID.
   */
  OrdersResponse response = new OrdersResponse();

  Record titleRecord =
      new Record()
          .setSid("sidProductTitle")
          .setObjectId(OrderConstants.TITLE_OBJECT_ID)
          .setPrimary("ProductTitle1");

  Record titleRecordForHelper =
      new Record()
          .setSid("sidProductTitleHelper")
          .setObjectId(OrderConstants.TITLE_OBJECT_ID)
          .setPrimary(OrderConstants.orderHelperTitles()[0]);

  Record artistRecord =
      new Record()
          .setSid("sidArtist")
          .setObjectId(OrderConstants.ARTIST_OBJECT_ID)
          .setArtist("Artist1");

  Record orderRecord =
      new Record()
          .setSid("sidOrderRecord")
          .setObjectId(OrderConstants.ORDER_OBJECT_ID)
          .setOrderProductTitle("sidProductTitle")
          .setOrderName("#292249")
          .setArtist("sidArtist");

  Record orderRecordHelper =
      new Record()
          .setSid("sidOrderRecordHelper")
          .setObjectId(OrderConstants.ORDER_OBJECT_ID)
          .setOrderProductTitle("sidProductTitleHelper")
          .setOrderName("#292249")
          .setArtist("sidArtist");

  @BeforeEach
  void setUp() {
    response.setRecords(
        Arrays.asList(
            titleRecord, titleRecordForHelper, artistRecord, orderRecord, orderRecordHelper));
  }

  @Test
  void testParseOrdersProperParsing() {
    ParsedOrders parsedOrders = OrderParsingUtil.parseOrders(response, OrderType.PRIORITY);

    assertNotNull(parsedOrders);

    Map<String, Order> ordersMapBySid = parsedOrders.getOrdersMapBySid();
    assertNotNull(ordersMapBySid);
    assertEquals(1, ordersMapBySid.size());

    Order rootOrder = ordersMapBySid.get("sidOrderRecord");
    assertNotNull(rootOrder);
    assertEquals("sidOrderRecord", rootOrder.getSid());
    assertEquals("#292249", rootOrder.getName());
    assertEquals("ProductTitle1", rootOrder.getProductTitle());
    assertEquals("sidArtist", rootOrder.getArtist());
    assertEquals(OrderType.PRIORITY, rootOrder.getOrderType());
    assertFalse(rootOrder.isRecordHelper());

    Map<String, Map<String, Order>> ordersHelperMapByName = parsedOrders.getOrdersHelperMapByName();
    assertNotNull(ordersHelperMapByName);
    assertEquals(1, ordersHelperMapByName.size());

    Map<String, Order> helperOrders = ordersHelperMapByName.get("#292249");
    assertNotNull(helperOrders);
    assertEquals(1, helperOrders.size());

    Order helperOrder = helperOrders.get("sidOrderRecordHelper");
    assertNotNull(helperOrder);
    assertEquals("sidOrderRecordHelper", helperOrder.getSid());
    assertEquals("#292249", helperOrder.getName());
    assertEquals(OrderConstants.orderHelperTitles()[0], helperOrder.getProductTitle());
    assertEquals("sidArtist", helperOrder.getArtist());
    assertEquals(OrderType.PRIORITY, helperOrder.getOrderType());
    assertTrue(helperOrder.isRecordHelper());
  }

  @Test
  void testParseOrdersBlacklistedOrder() {
    response.getRecords().stream()
        .filter(record -> "sidProductTitle".equals(record.getSid()))
        .findFirst()
        .ifPresent(record -> record.setPrimary(OrderConstants.blackListTitles()[0]));

    ParsedOrders parsedOrders = OrderParsingUtil.parseOrders(response, OrderType.PRIORITY);

    assertNotNull(parsedOrders);

    Map<String, Order> ordersMapBySid = parsedOrders.getOrdersMapBySid();
    assertNotNull(ordersMapBySid);
    assertEquals(0, ordersMapBySid.size());
  }

  @Test
  void testParseOrdersNullArtistField() {
    response.getRecords().stream()
        .filter(record -> "sidOrderRecord".equals(record.getSid()))
        .findFirst()
        .ifPresent(record -> record.setArtist(null));

    ParsedOrders parsedOrders = OrderParsingUtil.parseOrders(response, OrderType.PRIORITY);

    assertNotNull(parsedOrders);

    Map<String, Order> ordersMapBySid = parsedOrders.getOrdersMapBySid();
    assertNotNull(ordersMapBySid);
    assertEquals(1, ordersMapBySid.size());

    Order parsedOrder = ordersMapBySid.get("sidOrderRecord");
    assertNotNull(parsedOrder);
    assertEquals("sidOrderRecord", parsedOrder.getSid());
    assertEquals("#292249", parsedOrder.getName());
    assertEquals("ProductTitle1", parsedOrder.getProductTitle());
    assertNull(parsedOrder.getArtist());
    assertEquals(OrderType.PRIORITY, parsedOrder.getOrderType());
  }

  @Test
  void testParseOrdersMissingOrderProductTitleOrTitleRecord() {
    response.getRecords().stream()
        .filter(record -> "sidOrderRecord".equals(record.getSid()))
        .findFirst()
        .ifPresent(record -> record.setOrderProductTitle(null));

    ParsedOrders parsedOrders = OrderParsingUtil.parseOrders(response, OrderType.PRIORITY);

    assertNotNull(parsedOrders);

    Map<String, Order> ordersMapBySid = parsedOrders.getOrdersMapBySid();
    assertNotNull(ordersMapBySid);
    assertEquals(0, ordersMapBySid.size());
  }
}
