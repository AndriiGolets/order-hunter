package name.golets.order.hunter.orderhunterworker.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Startup wiring: merged poll URI and shared {@link WebClient} for the airportal host (poll/save).
 */
@Configuration
public class OrderHunterInfrastructureConfiguration {
  public static final String AIRPORTAL_POLL_WEB_CLIENT = "airportalPollWebClient";
  public static final String AIRPORTAL_SAVE_WEB_CLIENT = "airportalSaveWebClient";

  /**
   * Spring Boot 4 does not always register a {@link WebClient.Builder} bean; this module exposes
   * one for custom clients and tests.
   *
   * @return prototype-style builder for customizing base URL and connectors per client bean
   */
  @Bean
  public WebClient.Builder webClientBuilder() {
    return WebClient.builder();
  }

  /**
   * Single poll URL covering free, priority, and fast-track source paths from configuration.
   *
   * @param properties bound {@code order-hunter.*} settings
   * @return merged relative path for use with the airportal base URL
   */
  @Bean
  public CombinedOrdersPollPath combinedOrdersPollPath(OrderHunterProperties properties) {
    return new CombinedOrdersPollPath(
        OrderUrlUtil.combinePaths(
            properties.getFreeOrdersPath(),
            properties.getPriorityOrdersPath(),
            properties.getFastTrackOrdersPath()));
  }

  /**
   * WebClient tuned for poll requests.
   *
   * @param webClientBuilder injected Spring Boot builder (do not use {@link WebClient#builder()})
   * @param properties bound {@code order-hunter.*} settings
   * @return poll client scoped to {@link OrderHunterProperties#getAirportalHost()}
   */
  @Bean
  @Qualifier(AIRPORTAL_POLL_WEB_CLIENT)
  public WebClient airportalPollWebClient(
      WebClient.Builder webClientBuilder, OrderHunterProperties properties) {
    return createAirportalWebClient(webClientBuilder, properties, properties.getPollingTimeout());
  }

  /**
   * WebClient tuned for save (PATCH) requests.
   *
   * @param webClientBuilder injected Spring Boot builder
   * @param properties bound {@code order-hunter.*} settings
   * @return save client scoped to {@link OrderHunterProperties#getAirportalHost()}
   */
  @Bean
  @Qualifier(AIRPORTAL_SAVE_WEB_CLIENT)
  public WebClient airportalSaveWebClient(
      WebClient.Builder webClientBuilder, OrderHunterProperties properties) {
    return createAirportalWebClient(webClientBuilder, properties, properties.getSavingTimeout());
  }

  private WebClient createAirportalWebClient(
      WebClient.Builder webClientBuilder, OrderHunterProperties properties, int timeoutMillis) {
    String host = properties.getAirportalHost();
    String baseUrl =
        host.startsWith("http://") || host.startsWith("https://") ? host : "https://" + host;
    HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofMillis(timeoutMillis));
    return webClientBuilder
        .clone()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .baseUrl(baseUrl)
        .defaultHeader("xApiToken", properties.getApiToken())
        .defaultHeader("xStackId", properties.getStackId())
        .build();
  }
}
