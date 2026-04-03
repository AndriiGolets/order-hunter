package name.golets.order.hunter.worker.integration.airportal;

import java.util.Map;
import name.golets.order.hunter.common.model.OrdersResponse;
import name.golets.order.hunter.worker.config.OrderHunterInfrastructureConfiguration;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.error.WebClientError;
import name.golets.order.hunter.worker.flow.FlowObservationContextKeys;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * WebClient-based airportal client with Micrometer observations around poll and save boundaries.
 */
@Component
public class ObservedAirportalClient implements AirportalClient {
  private final WebClient pollWebClient;
  private final WebClient saveWebClient;
  private final String saveArtistNamePath;

  /**
   * Creates an observed outbound airportal client.
   *
   * @param pollWebClient client configured with polling timeout
   * @param saveWebClient client configured with saving timeout
   * @param properties worker configuration values
   */
  public ObservedAirportalClient(
      @Qualifier(OrderHunterInfrastructureConfiguration.AIRPORTAL_POLL_WEB_CLIENT)
          WebClient pollWebClient,
      @Qualifier(OrderHunterInfrastructureConfiguration.AIRPORTAL_SAVE_WEB_CLIENT)
          WebClient saveWebClient,
      OrderHunterProperties properties) {
    this.pollWebClient = pollWebClient;
    this.saveWebClient = saveWebClient;
    this.saveArtistNamePath = normalizeSavePathPrefix(properties.getSaveArtistNamePath());
  }

  @Override
  public Mono<OrdersResponse> pollOrders(String pathAndQuery) {
    return pollWebClient
        .get()
        .uri(pathAndQuery)
        .retrieve()
        .onStatus(HttpStatusCode::isError, response -> mapErrorResponse(response, pathAndQuery))
        .bodyToMono(OrdersResponse.class)
        .defaultIfEmpty(new OrdersResponse())
        .onErrorMap(
            WebClientRequestException.class,
            error -> WebClientError.fromRequestException(error, pathAndQuery));
  }

  @Override
  public Mono<String> patchOrder(String orderSid, String artistSid) {
    String path = buildSavePath(orderSid);
    Object requestBody = buildAssignArtistBody(artistSid);
    return Mono.deferContextual(
        contextView -> {
          String orderKind =
              readContextOrDefault(
                  contextView, FlowObservationContextKeys.SAVE_ORDER_KIND, "unknown");
          String productTitle =
              readContextOrDefault(
                  contextView, FlowObservationContextKeys.SAVE_PRODUCT_TITLE, "unknown");
          String beforeSavesDelay =
              readContextOrDefault(
                  contextView, FlowObservationContextKeys.SAVE_BEFORE_SAVES_DELAY, "0");
          return saveWebClient
              .patch()
              .uri(path)
              .attribute(FlowObservationContextKeys.SAVE_ORDER_KIND, orderKind)
              .attribute(FlowObservationContextKeys.SAVE_PRODUCT_TITLE, productTitle)
              .attribute(FlowObservationContextKeys.SAVE_BEFORE_SAVES_DELAY, beforeSavesDelay)
              .bodyValue(requestBody)
              .retrieve()
              .onStatus(HttpStatusCode::isError, response -> mapErrorResponse(response, path))
              .bodyToMono(String.class)
              .defaultIfEmpty("")
              .onErrorMap(
                  WebClientRequestException.class,
                  error -> WebClientError.fromRequestException(error, path));
        });
  }

  String buildSavePath(String orderSid) {
    return saveArtistNamePath + orderSid;
  }

  Object buildAssignArtistBody(String artistSid) {
    return Map.of("orders_v3_2__artist", artistSid);
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

  private Mono<? extends Throwable> mapErrorResponse(ClientResponse response, String errorUrl) {
    return response
        .bodyToMono(String.class)
        .defaultIfEmpty("")
        .map(body -> WebClientError.fromResponse(response.statusCode().value(), body, errorUrl));
  }

  private String readContextOrDefault(ContextView contextView, String key, String defaultValue) {
    if (!contextView.hasKey(key)) {
      return defaultValue;
    }
    Object raw = contextView.get(key);
    if (raw instanceof String text && !text.isBlank()) {
      return text;
    }
    return defaultValue;
  }
}
