import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of a cached HTTP response.
 *
 * <p>Implements {@link Serializable} so instances can be persisted to and restored from a
 * file-backed cache via {@link CacheStore}.
 *
 * @param statusCode the HTTP status code of the response (e.g. 200, 404)
 * @param headers    the response headers, keyed by header name with a list of values per name
 * @param body       the raw response body bytes
 */
public record CachedResponse(
    int statusCode,
    Map<String, List<String>> headers,
    byte[] body
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}