package name.golets.order.hunter.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import name.golets.order.hunter.common.constants.OrderConstants;
import name.golets.order.hunter.common.enums.OrderType;
import name.golets.order.hunter.common.model.Order;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.common.model.ParsedOrders;
import name.golets.order.hunter.common.model.Record;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies parsing rules that transform raw records into normalized order objects. */
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
          .setPrimary("The Royal Bros");

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

  /**
   * Verifies parser splits root/helper orders and populates common fields.
   *
   * <p>This scenario matters because downstream workflow depends on proper grouping and metadata.
   */
  @Test
  void testParseOrdersProperParsing() {
    ParsedOrders parsedOrders =
        name.golets.order.hunter.common.utils.OrderParsingUtil.parseOrders(
            response, OrderType.PRIORITY);

    assertNotNull(parsedOrders);

    Map<String, Order> ordersMapBySid = parsedOrders.getOrdersMapBySid();
    assertNotNull(ordersMapBySid);
    assertEquals(1, ordersMapBySid.size());

    Order rootOrder = ordersMapBySid.get("sidOrderRecord");
    assertNotNull(rootOrder);
    assertEquals("sidOrderRecord", rootOrder.getSid());
    assertEquals("#292249", rootOrder.getName());
    assertEquals("The Royal Bros", rootOrder.getProductTitle());
    assertEquals("sidArtist", rootOrder.getArtist());
    assertEquals(OrderType.PRIORITY, rootOrder.getOrderType());
    assertEquals(3, rootOrder.getHeads());
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
    assertEquals(1, helperOrder.getHeads());
    assertTrue(helperOrder.isRecordHelper());
  }

  /**
   * Verifies blacklisted titles are filtered out from parsed root orders.
   *
   * <p>This protects business flow from processing unsupported order types.
   */
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

  /**
   * Verifies parser still creates orders when artist reference is missing.
   *
   * <p>This edge case matters because upstream data may omit artist links.
   */
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
    assertEquals("The Royal Bros", parsedOrder.getProductTitle());
    assertNull(parsedOrder.getArtist());
    assertEquals(OrderType.PRIORITY, parsedOrder.getOrderType());
  }

  /**
   * Verifies parser skips order records that cannot resolve to a product title.
   *
   * <p>This prevents partially linked records from becoming invalid domain orders.
   */
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

  /**
   * Verifies parser increments heads when additional pet flag is set.
   *
   * <p>The extra pet consumes one additional head slot beyond the title baseline.
   */
  @Test
  void testParseOrdersAddsHeadForAdditionalPet() {
    response.getRecords().stream()
        .filter(record -> "sidOrderRecord".equals(record.getSid()))
        .findFirst()
        .ifPresent(record -> record.setAdditionalPet("Yes"));

    ParsedOrders parsedOrders = OrderParsingUtil.parseOrders(response, OrderType.PRIORITY);

    Order parsedOrder = parsedOrders.getOrdersMapBySid().get("sidOrderRecord");
    assertNotNull(parsedOrder);
    assertTrue(parsedOrder.isAdditionalPet());
    assertEquals(4, parsedOrder.getHeads());
  }

  /**
   * Verifies unknown product titles keep default one head.
   *
   * <p>This scenario ensures parser remains resilient for titles not yet present in enum catalog.
   */
  @Test
  void testParseOrdersUsesDefaultHeadsForUnknownTitle() {
    response.getRecords().stream()
        .filter(record -> "sidProductTitle".equals(record.getSid()))
        .findFirst()
        .ifPresent(record -> record.setPrimary("Unknown Product"));

    ParsedOrders parsedOrders = OrderParsingUtil.parseOrders(response, OrderType.PRIORITY);

    Order parsedOrder = parsedOrders.getOrdersMapBySid().get("sidOrderRecord");
    assertNotNull(parsedOrder);
    assertEquals(1, parsedOrder.getHeads());
  }
}
