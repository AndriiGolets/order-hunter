package name.golets.order.hunter.worker.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime configuration for the worker (poll URLs, auth headers, queues, jitter, timeouts). */
@ConfigurationProperties(prefix = "order-hunter")
@Getter
@Setter
@ToString
public class OrderHunterProperties {

  private String airportalHost = "";
  private String saveArtistNamePath = "";
  private String freeOrdersPath = "";
  private String priorityOrdersPath = "";
  private String fastTrackOrdersPath = "";
  private String artistId = "";

  /** Value for the {@code xApiToken} HTTP header on outbound API calls. */
  @ToString.Exclude private String apiToken = "";

  /** Value for the {@code xStackId} HTTP header on outbound API calls. */
  @ToString.Exclude private String stackId = "";

  private String commandsQueue = "";
  private String eventsQueue = "";
  private String awsRegion = "";
  private String hunterId = "";
  private int maxParallelOrdersToSaveThreads = 1;
  private int beforeOrderSavingJitterMax = 0;
  private int betweenPollsJitterMax = 0;
  private boolean disableJitterRandomize = false;

  /**
   * When false, {@link name.golets.order.hunter.worker.starter.WorkerStarter} does not schedule
   * ticks until {@link
   * name.golets.order.hunter.worker.starter.WorkerStarter#ensureTickLoopRunning()} is invoked
   * (integration tests).
   */
  private boolean workerAutoStart = true;

  /** HTTP client read/response timeout for poll calls, in milliseconds. */
  private int pollingTimeout = 5000;

  /** HTTP client read/response timeout for save calls, in milliseconds. */
  private int savingTimeout = 5000;
}
