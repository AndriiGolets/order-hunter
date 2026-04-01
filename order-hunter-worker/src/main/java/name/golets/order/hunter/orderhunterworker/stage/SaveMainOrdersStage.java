package name.golets.order.hunter.orderhunterworker.stage;

import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.orderhunterworker.flow.PollOrdersFlowContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Parallel PATCH saves for filtered main orders with bounded concurrency. */
@Component
public class SaveMainOrdersStage implements Stage<PollOrdersFlowContext> {

  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.empty();
  }
}
