package name.golets.order.hunter.orderhunterworker;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import name.golets.order.hunter.orderhunterworker.config.CombinedOrdersPollPath;
import name.golets.order.hunter.orderhunterworker.integration.airportal.AirportalClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Smoke test that the worker Spring context starts with the poll-orders flow skeleton beans. */
@SpringBootTest
class OrderHunterWorkerApplicationTests {

  @Autowired private ApplicationContext applicationContext;

  @Autowired private CombinedOrdersPollPath combinedOrdersPollPath;

  @Autowired private AirportalClient airportalClient;

  @Test
  void contextLoads() {
    assertNotNull(applicationContext);
    assertNotNull(airportalClient);
  }

  /**
   * Confirms startup merges the three configured poll paths into a non-empty relative URI for the
   * poll stage.
   */
  @Test
  void combinedOrdersPollPathBeanIsPopulated() {
    assertNotNull(combinedOrdersPollPath);
    assertTrue(combinedOrdersPollPath.pathAndQuery().startsWith("/api/"));
  }
}
