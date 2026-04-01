package name.golets.order.hunter.orderhunterworker.integration.airportal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.common.model.ResultInfo;
import name.golets.order.hunter.orderhunterworker.config.OrderHunterInfrastructureConfiguration;
import name.golets.order.hunter.orderhunterworker.config.OrderHunterProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient-based airportal client with Micrometer observations around poll and save boundaries.
 */
@Component
public class ObservedAirportalClient implements AirportalClient {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final WebClient pollWebClient;
  private final WebClient saveWebClient;
  private final ObservationRegistry observationRegistry;
  private final String saveArtistNamePath;

  /**
   * Creates an observed outbound airportal client.
   *
   * @param pollWebClient client configured with polling timeout
   * @param saveWebClient client configured with saving timeout
   * @param observationRegistry registry used to record operation observations
   * @param properties worker configuration values
   */
  public ObservedAirportalClient(
      @Qualifier(OrderHunterInfrastructureConfiguration.AIRPORTAL_POLL_WEB_CLIENT)
          WebClient pollWebClient,
      @Qualifier(OrderHunterInfrastructureConfiguration.AIRPORTAL_SAVE_WEB_CLIENT)
          WebClient saveWebClient,
      ObservationRegistry observationRegistry,
      OrderHunterProperties properties) {
    this.pollWebClient = pollWebClient;
    this.saveWebClient = saveWebClient;
    this.observationRegistry = observationRegistry;
    this.saveArtistNamePath = normalizeSavePathPrefix(properties.getSaveArtistNamePath());
  }

  @Override
  public Mono<OrdersResponse> pollOrders(String pathAndQuery) {
    return Mono.defer(
        () -> {
          Observation observation =
              Observation.createNotStarted("order-hunter.airportal.poll", observationRegistry)
                  .lowCardinalityKeyValue("operation", "poll")
                  .start();

          return pollWebClient
              .get()
              .uri(pathAndQuery)
              .retrieve()
              .bodyToMono(OrdersResponse.class)
              .defaultIfEmpty(new OrdersResponse())
              .doOnNext(
                  response ->
                      observation.lowCardinalityKeyValue(
                          "resultInfo.totalResults", extractTotalResults(response)))
              .doOnError(observation::error)
              .doFinally(signalType -> observation.stop());
        });
  }

  @Override
  public Mono<String> patchOrder(String orderSid, String artistSid) {
    String path = buildSavePath(orderSid);
    Object requestBody = buildAssignArtistBody(artistSid);
    return Mono.defer(
        () -> {
          Observation observation =
              Observation.createNotStarted("order-hunter.airportal.save", observationRegistry)
                  .lowCardinalityKeyValue("operation", "patchSave")
                  .highCardinalityKeyValue("request.body", toJson(requestBody))
                  .start();

          return saveWebClient
              .patch()
              .uri(path)
              .bodyValue(requestBody)
              .retrieve()
              .bodyToMono(String.class)
              .defaultIfEmpty("")
              .doOnNext(
                  responseBody ->
                      observation.highCardinalityKeyValue("response.body", responseBody))
              .doOnError(observation::error)
              .doFinally(signalType -> observation.stop());
        });
  }

  String buildSavePath(String orderSid) {
    return saveArtistNamePath + orderSid;
  }

  Object buildAssignArtistBody(String artistSid) {
    return Map.of("orders_v3_2__artist", artistSid);
  }

  private static String extractTotalResults(OrdersResponse response) {
    if (response == null) {
      return "0";
    }
    ResultInfo info = response.getResultInfo();
    if (info == null || info.getTotalResults() == null) {
      return "0";
    }
    return String.valueOf(info.getTotalResults());
  }

  private String toJson(Object body) {
    try {
      return OBJECT_MAPPER.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      return String.valueOf(body);
    }
  }

  private static String normalizeSavePathPrefix(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      return "/api/records/";
    }
    String normalized = rawPath.startsWith("/") ? rawPath : "/" + rawPath;
    if (!normalized.endsWith("/")) {
      normalized = normalized + "/";
    }
    return normalized;
  }
}
