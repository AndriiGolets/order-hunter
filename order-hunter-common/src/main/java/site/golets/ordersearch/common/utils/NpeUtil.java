package site.golets.ordersearch.common.utils;

import java.util.Map;
import java.util.function.Function;

public class NpeUtil {

  /**
   * Safely retrieves a value from a map and applies a property accessor function to it. Returns
   * {@code null} if the key is not found in the map or if the accessed property is {@code null}.
   *
   * @param map The map to retrieve the value from.
   * @param key The key to query the map with.
   * @param extractor A function to access a property from the retrieved value.
   * @param <K> Type of the key in the map.
   * @param <V> Type of values in the map.
   * @param <R> Type of the property being accessed from the value.
   * @return The accessed property value or {@code null} if any part of the chain is {@code null}.
   */
  public static <K, V, R> R safeGet(Map<K, V> map, K key, Function<V, R> extractor) {
    if (map == null || key == null) {
      return null;
    }
    V value = map.get(key);
    if (value == null) {
      return null;
    }
    return extractor.apply(value);
  }
}
