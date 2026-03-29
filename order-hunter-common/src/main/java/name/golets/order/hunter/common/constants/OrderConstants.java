package name.golets.order.hunter.common.constants;

/** Shared constants used when parsing order records from external responses. */
public class OrderConstants {

  public static final String ARTIST_OBJECT_ID = "object.custom.artists";
  public static final String TITLE_OBJECT_ID = "object.custom.head_count_2";
  public static final String ORDER_OBJECT_ID = "object.custom.orders_v3_2";

  private static final String[] orderHelperTitles = {
    "Get a digital file",
    "Get your canvas",
    "Size upgrade",
    "Reshipment fee",
    "Fast track your order",
    "Special customization",
    //            "Template change",
    "Change your picture",
    "Price difference",
  };

  private static final String[] blackListTitles = {"Template change", "picture change"};

  public static String[] orderHelperTitles() {
    return orderHelperTitles.clone();
  }

  public static String[] blackListTitles() {
    return blackListTitles.clone();
  }
}
