package name.golets.order.hunter.worker.stage;

import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Publishes {@link name.golets.order.hunter.worker.event.OrderTaken} to the outbound queue. */
@Component
public class NotifySqsStage implements Stage<PollOrdersFlowContext> {

  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.empty();
  }
}
