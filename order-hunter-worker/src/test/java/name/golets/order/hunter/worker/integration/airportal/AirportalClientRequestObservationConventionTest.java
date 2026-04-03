package name.golets.order.hunter.worker.integration.airportal;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.common.KeyValues;
import java.net.URI;
import name.golets.order.hunter.worker.flow.FlowObservationContextKeys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;
import org.springframework.web.reactive.function.client.ClientResponse;

/** Verifies custom order tags are added to WebClient auto-observation key values. */
class AirportalClientRequestObservationConventionTest {

  /** Ensures save order kind is exported as a low-cardinality tag on HTTP client spans. */
  @Test
  void getLowCardinalityKeyValues_includesOrderKindWhenPresent() {
    AirportalClientRequestObservationConvention convention =
        new AirportalClientRequestObservationConvention();
    ClientRequest request =
        ClientRequest.create(HttpMethod.PATCH, URI.create("http://localhost/api/records/id"))
            .attribute(FlowObservationContextKeys.SAVE_ORDER_KIND, "main")
            .build();
    ClientRequestObservationContext context =
        new ClientRequestObservationContext(
            ClientRequest.create(HttpMethod.PATCH, URI.create("http://localhost/api/records/id")));
    context.setRequest(request);

    KeyValues keyValues = convention.getLowCardinalityKeyValues(context);

    assertThat(keyValues.stream())
        .anyMatch(
            keyValue ->
                "order.kind".equals(keyValue.getKey()) && "main".equals(keyValue.getValue()));
  }

  /** Ensures per-order before-save delay is exported on HTTP client spans. */
  @Test
  void getLowCardinalityKeyValues_includesBeforeSavesDelayWhenPresent() {
    AirportalClientRequestObservationConvention convention =
        new AirportalClientRequestObservationConvention();
    ClientRequest request =
        ClientRequest.create(HttpMethod.PATCH, URI.create("http://localhost/api/records/id"))
            .attribute(FlowObservationContextKeys.SAVE_BEFORE_SAVES_DELAY, "17")
            .build();
    ClientRequestObservationContext context =
        new ClientRequestObservationContext(
            ClientRequest.create(HttpMethod.PATCH, URI.create("http://localhost/api/records/id")));
    context.setRequest(request);

    KeyValues keyValues = convention.getLowCardinalityKeyValues(context);

    assertThat(keyValues.stream())
        .anyMatch(
            keyValue ->
                "before.saves.delay".equals(keyValue.getKey()) && "17".equals(keyValue.getValue()));
  }

  /** Ensures product title is exported as a high-cardinality tag on HTTP client spans. */
  @Test
  void getHighCardinalityKeyValues_includesProductTitleWhenPresent() {
    AirportalClientRequestObservationConvention convention =
        new AirportalClientRequestObservationConvention();
    ClientRequest request =
        ClientRequest.create(HttpMethod.PATCH, URI.create("http://localhost/api/records/id"))
            .attribute(FlowObservationContextKeys.SAVE_PRODUCT_TITLE, "My Product")
            .build();
    ClientRequestObservationContext context =
        new ClientRequestObservationContext(
            ClientRequest.create(HttpMethod.PATCH, URI.create("http://localhost/api/records/id")));
    context.setRequest(request);

    KeyValues keyValues = convention.getHighCardinalityKeyValues(context);

    assertThat(keyValues.stream())
        .anyMatch(
            keyValue ->
                "order.productTitle".equals(keyValue.getKey())
                    && "My Product".equals(keyValue.getValue()));
  }

  /** Ensures failed HTTP client spans carry Jaeger-compatible error marker tag. */
  @Test
  void getLowCardinalityKeyValues_includesErrorTagOnServerErrorResponse() {
    AirportalClientRequestObservationConvention convention =
        new AirportalClientRequestObservationConvention();
    ClientRequestObservationContext context =
        new ClientRequestObservationContext(
            ClientRequest.create(HttpMethod.PATCH, URI.create("http://localhost/api/records/id")));
    context.setResponse(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());

    KeyValues keyValues = convention.getLowCardinalityKeyValues(context);

    assertThat(keyValues.stream())
        .anyMatch(
            keyValue -> "error".equals(keyValue.getKey()) && "true".equals(keyValue.getValue()));
  }

  /** Ensures failed HTTP client spans carry explicit error type for 5xx response codes. */
  @Test
  void getHighCardinalityKeyValues_includesErrorTypeOnServerErrorResponse() {
    AirportalClientRequestObservationConvention convention =
        new AirportalClientRequestObservationConvention();
    ClientRequestObservationContext context =
        new ClientRequestObservationContext(
            ClientRequest.create(HttpMethod.PATCH, URI.create("http://localhost/api/records/id")));
    context.setResponse(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());

    KeyValues keyValues = convention.getHighCardinalityKeyValues(context);

    assertThat(keyValues.stream())
        .anyMatch(
            keyValue ->
                "error.type".equals(keyValue.getKey()) && "500".equals(keyValue.getValue()));
  }
}
