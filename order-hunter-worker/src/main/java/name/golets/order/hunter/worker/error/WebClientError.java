package name.golets.order.hunter.worker.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/** Structured transport error produced for failed Airportal WebClient requests. */
@Getter
public final class WebClientError extends RuntimeException {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final String errorCode;
  private final String errorCause;
  private final String errorMessage;
  private final String errorUrl;

  private WebClientError(
      String errorCode, String errorCause, String errorMessage, String errorUrl, Throwable cause) {
    super(errorMessage, cause);
    this.errorCode = errorCode;
    this.errorCause = errorCause;
    this.errorMessage = errorMessage;
    this.errorUrl = errorUrl;
  }

  /**
   * Builds error details from an HTTP error response body and status code.
   *
   * @param statusCode HTTP response status code
   * @param responseBody response body text
   * @param errorUrl relative endpoint used in request
   * @return structured WebClient error instance
   */
  public static WebClientError fromResponse(int statusCode, String responseBody, String errorUrl) {
    String defaultCode = "HTTP_" + statusCode;
    String defaultCause = "HTTP_RESPONSE_ERROR";
    String defaultMessage =
        responseBody == null || responseBody.isBlank() ? "HTTP request failed" : responseBody;

    String extractedCode = extractValue(responseBody, "errorCode", defaultCode);
    String extractedCause = extractValue(responseBody, "errorCause", defaultCause);
    String extractedMessage = extractValue(responseBody, "errorMessage", defaultMessage);
    String extractedUrl = extractValue(responseBody, "errorUrl", errorUrl);

    return new WebClientError(extractedCode, extractedCause, extractedMessage, extractedUrl, null);
  }

  /**
   * Builds error details from an HTTP transport/request failure with no server response.
   *
   * @param exception source request exception
   * @param errorUrl relative endpoint used in request
   * @return structured WebClient error instance
   */
  public static WebClientError fromRequestException(
      WebClientRequestException exception, String errorUrl) {
    String code = "HTTP_REQUEST_ERROR";
    String cause = exception.getClass().getSimpleName();
    String message = exception.getMessage() != null ? exception.getMessage() : "Request failed";
    return new WebClientError(code, cause, message, errorUrl, exception);
  }

  /**
   * Builds error details from an arbitrary throwable already carrying HTTP context.
   *
   * @param throwable source throwable to wrap
   * @param errorUrl fallback request URL for logging
   * @return structured WebClient error
   */
  public static WebClientError fromThrowable(Throwable throwable, String errorUrl) {
    if (throwable instanceof WebClientError webClientError) {
      return webClientError;
    }
    if (throwable instanceof WebClientRequestException requestException) {
      return fromRequestException(requestException, errorUrl);
    }
    String message = throwable.getMessage() != null ? throwable.getMessage() : "Request failed";
    return new WebClientError(
        "HTTP_UNKNOWN_ERROR", throwable.getClass().getSimpleName(), message, errorUrl, throwable);
  }

  private static String extractValue(String responseBody, String key, String defaultValue) {
    if (responseBody == null || responseBody.isBlank()) {
      return defaultValue;
    }
    try {
      JsonNode node = OBJECT_MAPPER.readTree(responseBody).path(key);
      if (!node.isMissingNode() && !node.isNull()) {
        String value = node.asText();
        if (value != null && !value.isBlank()) {
          return value;
        }
      }
    } catch (JsonProcessingException ignored) {
      // Keep defaults when body is not JSON or has different shape.
    }
    return defaultValue;
  }
}
