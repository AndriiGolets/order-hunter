package name.golets.order.hunter.order_hunter_worker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.reactive.function.client.WebClient;

/** Verifies architecture constraints for the worker module based on the flow plan. */
@AnalyzeClasses(packagesOf = OrderHunterWorkerApplication.class)
class WorkerArchitectureRulesTest {

  private static final String BASE_PACKAGE = "name.golets.order.hunter.order_hunter_worker";

  /**
   * Ensures controller code stays state-focused and does not orchestrate flow or stage execution.
   */
  @ArchTest
  static final ArchRule controllersMustNotDependOnFlowOrStage =
      noClasses()
          .that()
          .resideInAPackage(BASE_PACKAGE + "..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              BASE_PACKAGE + "..flow..",
              BASE_PACKAGE + "..stage..",
              "name.golets.order.hunter.common.flow..")
          .because("controllers are state entry points and must not run flows");

  /**
   * Prevents ad-hoc outbound client creation so observability instrumentation remains centralized.
   */
  @ArchTest
  static final ArchRule appLayerMustNotUseWebClientCreate =
      noClasses()
          .that()
          .resideInAPackage(BASE_PACKAGE + "..")
          .should()
          .callMethod(WebClient.class, "create")
          .allowEmptyShould(true)
          .because(
              "outbound clients must be created from injected auto-configured WebClient.Builder");

  /** Prevents ad-hoc outbound client creation with explicit base URL string. */
  @ArchTest
  static final ArchRule appLayerMustNotUseWebClientCreateWithUrl =
      noClasses()
          .that()
          .resideInAPackage(BASE_PACKAGE + "..")
          .should()
          .callMethod(WebClient.class, "create", String.class)
          .allowEmptyShould(true)
          .because(
              "outbound clients must be created from injected auto-configured WebClient.Builder");

  /** Prevents ad-hoc builder creation and enforces injected builder usage. */
  @ArchTest
  static final ArchRule appLayerMustNotUseWebClientBuilder =
      noClasses()
          .that()
          .resideInAPackage(BASE_PACKAGE + "..")
          .should()
          .callMethod(WebClient.class, "builder")
          .allowEmptyShould(true)
          .because(
              "ad-hoc WebClient.builder() bypasses shared observability and client configuration");

  /** Disallows manual tracing APIs in application layer code. */
  @ArchTest
  static final ArchRule appLayerMustNotDependOnTracerApis =
      noClasses()
          .that()
          .resideInAnyPackage(
              BASE_PACKAGE + "..controller..",
              BASE_PACKAGE + "..starter..",
              BASE_PACKAGE + "..flow..",
              BASE_PACKAGE + "..stage..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("io.micrometer.tracing.Tracer")
          .allowEmptyShould(true)
          .because("manual span creation in business code is forbidden by architecture");
}
