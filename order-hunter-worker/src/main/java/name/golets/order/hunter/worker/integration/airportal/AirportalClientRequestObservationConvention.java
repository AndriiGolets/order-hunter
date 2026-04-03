package name.golets.order.hunter.worker.integration.airportal;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import name.golets.order.hunter.worker.flow.FlowObservationContextKeys;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.DefaultClientRequestObservationConvention;

/**
 * Adds order metadata tags to auto-generated WebClient HTTP spans.
 *
 * <p>The values are propagated as WebClient request attributes by save stages and then mapped to
 * tags here so we keep framework HTTP span naming while enriching traces with business context.
 */
public class AirportalClientRequestObservationConvention
    extends DefaultClientRequestObservationConvention {

  @Override
  public KeyValues getLowCardinalityKeyValues(ClientRequestObservationContext context) {
    KeyValues keyValues = super.getLowCardinalityKeyValues(context);
    String orderKind = readRequestAttribute(context, FlowObservationContextKeys.SAVE_ORDER_KIND);
    if (orderKind != null) {
      keyValues = keyValues.and(KeyValue.of("order.kind", orderKind));
    }
    if (isServerError(context)) {
      // Jaeger highlights spans as errors based on this conventional tag.
      keyValues = keyValues.and(KeyValue.of("error", "true"));
    }
    return keyValues;
  }

  @Override
  public KeyValues getHighCardinalityKeyValues(ClientRequestObservationContext context) {
    KeyValues keyValues = super.getHighCardinalityKeyValues(context);
    String productTitle =
        readRequestAttribute(context, FlowObservationContextKeys.SAVE_PRODUCT_TITLE);
    if (productTitle != null) {
      keyValues = keyValues.and(KeyValue.of("order.productTitle", productTitle));
    }
    HttpStatusCode statusCode = responseStatus(context);
    if (statusCode != null && statusCode.is5xxServerError()) {
      // Keep explicit failure reason for easier filtering in Jaeger.
      keyValues = keyValues.and(KeyValue.of("error.type", Integer.toString(statusCode.value())));
    }
    return keyValues;
  }

  private static String readRequestAttribute(ClientRequestObservationContext context, String key) {
    ClientRequest request = context.getRequest();
    if (request == null) {
      return null;
    }
    return request
        .attribute(key)
        .map(Object::toString)
        .filter(text -> !text.isBlank())
        .orElse(null);
  }

  private static boolean isServerError(ClientRequestObservationContext context) {
    HttpStatusCode statusCode = responseStatus(context);
    return statusCode != null && statusCode.is5xxServerError();
  }

  private static HttpStatusCode responseStatus(ClientRequestObservationContext context) {
    ClientResponse response = context.getResponse();
    if (response == null) {
      return null;
    }
    return response.statusCode();
  }
}
