package name.golets.order.hunter.common.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Collection transformation helpers used by parsing and mapping code. */
public class TransformUtil {

  /**
   * Transforms the given Iterable of elements into a Map where the keys are extracted using the
   * provided function.
   *
   * @param repositoryFindAll the Iterable of elements
   * @param extractId a function to extract a unique key from an element
   * @param <T> the type of elements contained in the Iterable
   * @return a Map with keys as extracted IDs and values as the corresponding elements
   */
  public static <T> Map<String, T> transformToMap(
      Iterable<T> repositoryFindAll, Function<T, String> extractId) {
    Map<String, T> resultMap = new HashMap<>();
    for (T element : repositoryFindAll) {
      String key = extractId.apply(element);
      resultMap.put(key, element);
    }
    return resultMap;
  }
}
