package name.golets.order.hunter.worker.config;

/**
 * Holds the relative poll path (path + query) produced at startup from the three configured order
 * URLs.
 *
 * @param pathAndQuery path starting with {@code /}, including query string
 */
public record CombinedOrdersPollPath(String pathAndQuery) {}
