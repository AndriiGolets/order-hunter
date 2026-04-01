package name.golets.order.hunter.orderhunterworker.integration.airportal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import name.golets.order.hunter.orderhunterworker.config.OrderHunterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/** Verifies URL and payload building rules for airportal PATCH save requests. */
class ObservedAirportalClientTest {

  /** Ensures save path always matches {@code /api/records/{orderSid}} shape. */
  @Test
  void buildSavePath_usesRecordsPrefixAndOrderSid() {
    OrderHunterProperties properties = new OrderHunterProperties();
    properties.setSaveArtistNamePath("/api/records/");

    ObservedAirportalClient client =
        new ObservedAirportalClient(
            mock(WebClient.class), mock(WebClient.class), ObservationRegistry.NOOP, properties);

    assertEquals("/api/records/orderSid123", client.buildSavePath("orderSid123"));
  }

  /** Ensures request body key is exactly {@code orders_v3_2__artist} with provided artist SID. */
  @Test
  void buildAssignArtistBody_containsExpectedKeyAndValue() {
    OrderHunterProperties properties = new OrderHunterProperties();
    properties.setSaveArtistNamePath("/api/records/");
    ObservedAirportalClient client =
        new ObservedAirportalClient(
            mock(WebClient.class), mock(WebClient.class), ObservationRegistry.NOOP, properties);

    Object payload = client.buildAssignArtistBody("artistSid456");

    assertInstanceOf(Map.class, payload);
    @SuppressWarnings("unchecked")
    Map<String, String> body = (Map<String, String>) payload;
    assertEquals("artistSid456", body.get("orders_v3_2__artist"));
  }
}
