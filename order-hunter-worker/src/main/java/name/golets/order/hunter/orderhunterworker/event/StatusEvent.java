package name.golets.order.hunter.orderhunterworker.event;

import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Inbound SQS request for a read-only snapshot of worker state. */
@Data
@NoArgsConstructor
public class StatusEvent {

  private String eventVersion;
  private Instant producedAt;
}
