package name.golets.order.hunter.worker.stage;

import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.worker.flow.PollOrdersFlowContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Parallel PATCH saves for helpers tied to successfully saved main orders. */
@Component
public class SaveHelpersStage implements Stage<PollOrdersFlowContext> {

  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.empty();
  }
}
