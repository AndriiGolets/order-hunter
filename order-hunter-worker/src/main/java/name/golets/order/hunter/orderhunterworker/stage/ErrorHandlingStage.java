package name.golets.order.hunter.orderhunterworker.stage;

import name.golets.order.hunter.common.flow.Stage;
import name.golets.order.hunter.orderhunterworker.flow.PollOrdersFlowContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Central place for mapping technical failures to structured outcomes; composed alongside parallel
 * save branches in the full flow.
 */
@Component
public class ErrorHandlingStage implements Stage<PollOrdersFlowContext> {

  @Override
  public Mono<Void> execute(PollOrdersFlowContext context) {
    return Mono.empty();
  }
}
