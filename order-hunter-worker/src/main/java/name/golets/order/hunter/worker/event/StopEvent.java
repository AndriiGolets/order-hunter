package name.golets.order.hunter.worker.event;

import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Inbound SQS command that deactivates polling for subsequent starter ticks. */
@Data
@NoArgsConstructor
public class StopEvent {

  private String eventVersion;
  private Instant producedAt;
}
