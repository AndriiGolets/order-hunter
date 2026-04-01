package name.golets.order.hunter.orderhunterworker.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds a single poll URL whose results cover free, priority, and fast-track cohorts.
 *
 * <p>Query parameters must be present in <strong>all</strong> three configured URLs with compatible
 * values, except {@code orders_v3_2__date___gt} (broadened to the earliest threshold) and {@code
 * _includeFields} (union of JSON array entries). Conflicting parameters are omitted so the API
 * returns a superset; downstream stages filter by {@link
 * name.golets.order.hunter.common.enums.OrderType}.
 */
public final class OrderUrlUtil {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String DATE_GT_PARAM = "orders_v3_2__date___gt";

  private static final String INCLUDE_FIELDS_PARAM = "_includeFields";

  private OrderUrlUtil() {}

  /**
   * Merges three order-list paths into one path + query string (relative, starting with {@code /}).
   *
   * @param freePath configured free-orders path with query
   * @param priorityPath configured priority path with query
   * @param fastTrackPath configured fast-track path with query
   * @return combined relative URI string
   */
  public static String combinePaths(String freePath, String priorityPath, String fastTrackPath) {
    MultiValueMap<String, String> freeParams = extractParams(freePath);
    MultiValueMap<String, String> priorityParams = extractParams(priorityPath);
    MultiValueMap<String, String> fastParams = extractParams(fastTrackPath);

    String basePath = freePath.split("\\?", 2)[0];

    Set<String> commonKeys = new HashSet<>(freeParams.keySet());
    commonKeys.retainAll(priorityParams.keySet());
    commonKeys.retainAll(fastParams.keySet());

    UriComponentsBuilder builder = UriComponentsBuilder.fromPath(basePath);

    for (String key : new TreeSet<>(commonKeys)) {
      if (!hasNonEmptyValues(freeParams, key)
          || !hasNonEmptyValues(priorityParams, key)
          || !hasNonEmptyValues(fastParams, key)) {
        continue;
      }
      if (INCLUDE_FIELDS_PARAM.equals(key)) {
        String merged =
            mergeIncludeFields(
                firstValue(freeParams, key),
                firstValue(priorityParams, key),
                firstValue(fastParams, key));
        builder.queryParam(key, merged);
        continue;
      }
      if (DATE_GT_PARAM.equals(key)) {
        builder.queryParam(
            key,
            minDate(
                firstValue(freeParams, key),
                firstValue(priorityParams, key),
                firstValue(fastParams, key)));
        continue;
      }
      if (sameNormalizedValues(freeParams, priorityParams, fastParams, key)) {
        for (String v : Objects.requireNonNull(freeParams.get(key))) {
          builder.queryParam(key, v);
        }
      }
    }

    return builder.build(false).encode().toUriString();
  }

  static MultiValueMap<String, String> extractParams(String pathWithQuery) {
    String withHost = "https://order-hunter.placeholder" + normalizeLeadingSlash(pathWithQuery);
    UriComponents components = UriComponentsBuilder.fromUriString(withHost).build();
    MultiValueMap<String, String> raw = components.getQueryParams();
    LinkedMultiValueMap<String, String> decoded = new LinkedMultiValueMap<>();
    for (Map.Entry<String, List<String>> e : raw.entrySet()) {
      String key = e.getKey();
      List<String> values = e.getValue();
      if (values == null) {
        continue;
      }
      for (String value : values) {
        if (value != null) {
          decoded.add(key, value);
        }
      }
    }
    return decoded;
  }

  private static String normalizeLeadingSlash(String path) {
    if (path == null || path.isEmpty()) {
      return "/";
    }
    return path.startsWith("/") ? path : "/" + path;
  }

  private static boolean hasNonEmptyValues(MultiValueMap<String, String> map, String key) {
    List<String> list = map.get(key);
    return list != null
        && !list.isEmpty()
        && list.stream().allMatch(v -> v != null && !v.isEmpty());
  }

  private static String firstValue(MultiValueMap<String, String> map, String key) {
    return Objects.requireNonNull(map.getFirst(key));
  }

  private static boolean sameNormalizedValues(
      MultiValueMap<String, String> a,
      MultiValueMap<String, String> b,
      MultiValueMap<String, String> c,
      String key) {
    return normalizedList(a, key).equals(normalizedList(b, key))
        && normalizedList(b, key).equals(normalizedList(c, key));
  }

  private static List<String> normalizedList(MultiValueMap<String, String> map, String key) {
    List<String> list = new ArrayList<>(Objects.requireNonNullElse(map.get(key), List.of()));
    list.sort(String::compareTo);
    return list;
  }

  private static String minDate(String a, String b, String c) {
    return minLex(List.of(a, b, c));
  }

  private static String minLex(Collection<String> values) {
    return values.stream().filter(Objects::nonNull).min(String::compareTo).orElse("");
  }

  private static String mergeIncludeFields(
      String freeValue, String priorityValue, String fastValue) {
    Set<String> union = new TreeSet<>();
    union.addAll(parseIncludeFieldTokens(freeValue));
    union.addAll(parseIncludeFieldTokens(priorityValue));
    union.addAll(parseIncludeFieldTokens(fastValue));
    ArrayNode array = OBJECT_MAPPER.createArrayNode();
    for (String field : union) {
      array.add(field);
    }
    return array.toString();
  }

  private static List<String> parseIncludeFieldTokens(String queryValue) {
    try {
      return parseIncludeFieldArray(OBJECT_MAPPER.readTree(queryValue));
    } catch (JsonProcessingException first) {
      try {
        String decoded = URLDecoder.decode(queryValue, StandardCharsets.UTF_8);
        return parseIncludeFieldArray(OBJECT_MAPPER.readTree(decoded));
      } catch (JsonProcessingException second) {
        return List.of();
      }
    }
  }

  private static List<String> parseIncludeFieldArray(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (JsonNode el : node) {
      if (el.isTextual()) {
        out.add(el.asText());
      }
    }
    return out;
  }
}
