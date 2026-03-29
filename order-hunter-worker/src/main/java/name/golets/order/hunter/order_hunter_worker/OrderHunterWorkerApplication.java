package name.golets.order.hunter.orderhunterworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the Order Hunter worker service. */
@SpringBootApplication
public class OrderHunterWorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(OrderHunterWorkerApplication.class, args);
  }
}
