package name.golets.order.hunter.worker.error;

/** Marker contract for intentional stage failures that carry structured logging fields. */
public interface StageError {

  /**
   * Returns stable code for programmatic grouping.
   *
   * @return error code identifier
   */
  String getErrorCode();

  /**
   * Returns high-level failure cause category.
   *
   * @return short cause category
   */
  String getErrorCause();

  /**
   * Returns human-readable failure message.
   *
   * @return error message text
   */
  String getErrorMessage();

  /**
   * Returns URL or stage location related to the failure.
   *
   * @return endpoint or stage identifier
   */
  String getErrorUrl();
}
