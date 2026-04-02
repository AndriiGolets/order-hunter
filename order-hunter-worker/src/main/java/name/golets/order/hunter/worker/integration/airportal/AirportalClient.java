package name.golets.order.hunter.worker.integration.airportal;

import name.golets.order.hunter.common.model.OrdersResponse;
import reactor.core.publisher.Mono;

/** Outbound API port for airportal poll/save operations. */
public interface AirportalClient {

  /**
   * Fetches candidate orders from a combined poll path.
   *
   * @param pathAndQuery relative poll path including query
   * @return decoded orders payload
   */
  Mono<OrdersResponse> pollOrders(String pathAndQuery);

  /**
   * Sends a PATCH request to assign an artist to one order.
   *
   * <p>URL must resolve to {@code /api/records/{orderSid}} and body must be {@code
   * {"orders_v3_2__artist":"{artistSid}"}}.
   *
   * @param orderSid order record SID used in URL path
   * @param artistSid artist SID written to {@code orders_v3_2__artist}
   * @return raw response body for audit/debug mapping
   */
  Mono<String> patchOrder(String orderSid, String artistSid);
}
