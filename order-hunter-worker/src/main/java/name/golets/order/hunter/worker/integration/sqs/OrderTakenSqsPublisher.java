package name.golets.order.hunter.worker.integration.sqs;

import name.golets.order.hunter.worker.event.OrderTaken;
import reactor.core.publisher.Mono;

/** Outbound port for publishing {@link OrderTaken} events to SQS. */
public interface OrderTakenSqsPublisher {

  /**
   * Publishes one {@link OrderTaken} event to outbound SQS.
   *
   * @param event event payload to publish
   * @return completion when send operation succeeds
   */
  Mono<Void> publish(OrderTaken event);
}
