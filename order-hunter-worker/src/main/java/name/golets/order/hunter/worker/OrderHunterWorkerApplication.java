package name.golets.order.hunter.worker;

import name.golets.order.hunter.worker.config.OrderHunterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Entry point for the Order Hunter worker service. */
@SpringBootApplication
@EnableConfigurationProperties(OrderHunterProperties.class)
public class OrderHunterWorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrderHunterWorkerApplication.class, args);
  }
}
