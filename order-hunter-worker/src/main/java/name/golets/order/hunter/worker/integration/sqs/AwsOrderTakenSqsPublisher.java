package name.golets.order.hunter.worker.integration.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import name.golets.order.hunter.worker.config.OrderHunterProperties;
import name.golets.order.hunter.worker.event.OrderTaken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** AWS SDK based publisher of {@link OrderTaken} events to SQS. */
@Component
public class AwsOrderTakenSqsPublisher implements OrderTakenSqsPublisher {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final SqsAsyncClient sqsAsyncClient;
  private final ObservationRegistry observationRegistry;
  private final String eventsQueue;
  private final AtomicReference<String> resolvedQueueUrl = new AtomicReference<>();

  /**
   * Creates SQS publisher with worker queue configuration.
   *
   * @param sqsAsyncClient async AWS SQS client
   * @param properties worker properties with events queue value
   */
  public AwsOrderTakenSqsPublisher(
      SqsAsyncClient sqsAsyncClient,
      OrderHunterProperties properties,
      ObservationRegistry observationRegistry) {
    this.sqsAsyncClient = sqsAsyncClient;
    this.observationRegistry = observationRegistry;
    this.eventsQueue = properties.getEventsQueue();
  }

  @Override
  public Mono<Void> publish(OrderTaken event) {
    return Mono.defer(
        () -> {
          Observation observation =
              Observation.createNotStarted(
                      "order-hunter.sqs.publish.orderTaken", observationRegistry)
                  .start();
          return resolveQueueUrl()
              .flatMap(
                  queueUrl ->
                      Mono.fromFuture(
                              sqsAsyncClient.sendMessage(
                                  SendMessageRequest.builder()
                                      .queueUrl(queueUrl)
                                      .messageBody(toJson(event))
                                      .build()))
                          .then())
              .doOnError(observation::error)
              .doFinally(signalType -> observation.stop());
        });
  }

  private Mono<String> resolveQueueUrl() {
    String cached = resolvedQueueUrl.get();
    if (cached != null && !cached.isBlank()) {
      return Mono.just(cached);
    }
    if (eventsQueue != null && eventsQueue.startsWith("http")) {
      resolvedQueueUrl.compareAndSet(null, eventsQueue);
      return Mono.just(eventsQueue);
    }
    return Mono.fromFuture(
            sqsAsyncClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(eventsQueue).build()))
        .map(response -> Objects.requireNonNull(response.queueUrl()))
        .doOnNext(url -> resolvedQueueUrl.compareAndSet(null, url));
  }

  private static String toJson(OrderTaken event) {
    try {
      return OBJECT_MAPPER.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize OrderTaken event", e);
    }
  }
}
