package name.golets.order.hunter.worker.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for structured WebClient error extraction rules. */
class WebClientErrorTest {

  /** Ensures explicit error fields are extracted from JSON response body. */
  @Test
  void fromResponse_readsStructuredFieldsFromBody() {
    String body =
        """
        {
          "errorCode":"AIR-400",
          "errorCause":"VALIDATION",
          "errorMessage":"Bad payload",
          "errorUrl":"/api/records/1"
        }
        """;

    WebClientError error = WebClientError.fromResponse(400, body, "/fallback");

    assertEquals("AIR-400", error.getErrorCode());
    assertEquals("VALIDATION", error.getErrorCause());
    assertEquals("Bad payload", error.getErrorMessage());
    assertEquals("/api/records/1", error.getErrorUrl());
  }

  /** Ensures default fields are produced when response body has no structured JSON. */
  @Test
  void fromResponse_usesDefaultsWhenBodyIsUnstructured() {
    WebClientError error = WebClientError.fromResponse(503, "upstream down", "/api/poll");

    assertEquals("HTTP_503", error.getErrorCode());
    assertEquals("HTTP_RESPONSE_ERROR", error.getErrorCause());
    assertEquals("upstream down", error.getErrorMessage());
    assertEquals("/api/poll", error.getErrorUrl());
  }
}
