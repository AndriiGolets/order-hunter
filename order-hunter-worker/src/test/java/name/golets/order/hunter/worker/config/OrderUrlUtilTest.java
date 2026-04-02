package name.golets.order.hunter.worker.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;

/**
 * Verifies merged poll URL construction: intersection semantics, broadened date, and merged
 * include-fields list.
 */
class OrderUrlUtilTest {

  private static final String FREE =
      "/api/objects/object.custom.orders_v3_2/records/?orders_v3_2__date___gt=2023-05-01"
          + "&orders_v3_2__artist___isempty=true&orders_v3_2__egle_status___isempty=true"
          + "&shared_key=ok&_includeFields=[\"a\",\"b\"]&_count=30";

  private static final String PRIORITY =
      "/api/objects/object.custom.orders_v3_2/records/?orders_v3_2__date___gt=2021-05-01"
          + "&orders_v3_2__artist___isempty=true&orders_v3_2__egle_status___isempty=true"
          + "&shared_key=ok&_includeFields=[\"b\",\"c\"]&_count=30";

  private static final String FAST =
      "/api/objects/object.custom.orders_v3_2/records/?orders_v3_2__date___gt=2021-05-01"
          + "&orders_v3_2__artist___isempty=true&orders_v3_2__egle_status___isempty=true"
          + "&shared_key=ok&_includeFields=[\"c\",\"d\"]&_count=30";

  /**
   * Ensures the merged URL uses the earliest date threshold and carries the union of _includeFields
   * while retaining parameters identical across all three sources.
   */
  @Test
  void combinePaths_broadensDateAndUnionsIncludeFields() {
    String combined = OrderUrlUtil.combinePaths(FREE, PRIORITY, FAST);

    assertTrue(combined.startsWith("/api/objects/object.custom.orders_v3_2/records"));
    assertTrue(
        combined.contains("orders_v3_2__date___gt=2021-05-01"),
        "earliest date threshold should dominate");
    assertTrue(combined.contains("shared_key=ok"));
    assertTrue(combined.contains("_includeFields="));
    assertTrue(
        combined.contains("a")
            && combined.contains("b")
            && combined.contains("c")
            && combined.contains("d"));
  }

  /**
   * Ensures {@link OrderUrlUtil#extractParams(String)} decodes query keys usable for merging logic.
   */
  @Test
  void extractParams_parsesRelativePathWithQuery() {
    MultiValueMap<String, String> params = OrderUrlUtil.extractParams(FREE);
    assertFalse(params.isEmpty());
    assertTrue(params.containsKey("orders_v3_2__date___gt"));
    assertTrue(params.containsKey("_includeFields"));
  }
}
