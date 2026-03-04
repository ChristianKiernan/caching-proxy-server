import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link HttpHandler} that serves requests from cache when possible, and forwards cache misses
 * to the configured origin server.
 *
 * <p>Every response is tagged with an {@code X-Cache} header: {@code HIT} when served from
 * cache, {@code MISS} when fetched from the origin. Cache misses are stored in the
 * {@link CacheStore} and, if a cache file path is configured, persisted to disk immediately.
 */
public class ProxyHandler implements HttpHandler {

    private final CacheStore cacheStore;
    private final ProxyConfig config;
    private final HttpClient client;

    /**
     * Creates a handler with a default {@link HttpClient}.
     *
     * @param cacheStore the cache to read from and write to
     * @param config     proxy configuration (origin URI, cache file path, etc.)
     */
    public ProxyHandler(CacheStore cacheStore, ProxyConfig config) {
        this(cacheStore, config, HttpClient.newHttpClient());
    }

    /**
     * Creates a handler with an explicit {@link HttpClient}; intended for testing.
     *
     * @param cacheStore the cache to read from and write to
     * @param config     proxy configuration (origin URI, cache file path, etc.)
     * @param client     the HTTP client used to forward requests to the origin
     */
    ProxyHandler(CacheStore cacheStore, ProxyConfig config, HttpClient client) {
        this.cacheStore = cacheStore;
        this.config = config;
        this.client = client;
    }

    /**
     * Handles an incoming HTTP request.
     *
     * <p>Checks the cache for the request's path (plus query string). On a HIT the cached
     * response is returned directly. On a MISS the request is forwarded to the origin, the
     * response is cached (and optionally persisted), and then returned to the client.
     *
     * @param exchange the HTTP exchange representing the incoming request and outgoing response
     * @throws IOException if reading the request or writing the response fails
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Build cache key from the path and the query string
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        String query = uri.getQuery();
        String cacheKey = query != null ? path + "?" + query : path;

        Optional<CachedResponse> cached = cacheStore.get(cacheKey);
        if (cached.isPresent()) {
            // HIT: return cached response
            CachedResponse cachedResponse = cached.get();
            exchange.getResponseHeaders().putAll(cachedResponse.headers());
            exchange.getResponseHeaders().set("X-Cache", "HIT");
            exchange.sendResponseHeaders(cachedResponse.statusCode(), cachedResponse.body().length);
            exchange.getResponseBody().write(cachedResponse.body());
            exchange.close();
        } else {
            // MISS: forward to origin, cache result, return to client
            String relativePath = query != null ? path.substring(1) + "?" + query : path.substring(1);
            URI targetUri = config.originBaseUri().resolve(relativePath);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(targetUri)
                    .method(exchange.getRequestMethod(), HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<byte[]> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request to origin interrupted", e);
            }

            // Filter out transfer-encoding, as it can conflict with how we send the response
            Map<String, List<String>> headers = new java.util.HashMap<>(response.headers().map());
            headers.remove("transfer-encoding");

            CachedResponse cachedResponse = new CachedResponse(response.statusCode(), headers, response.body());
            cacheStore.put(cacheKey, cachedResponse);
            if (config.cacheFilePath() != null) {
                try {
                    cacheStore.save(config.cacheFilePath());
                } catch (IOException e) {
                    System.err.println("Warning: failed to persist cache: " + e.getMessage());
                }
            }

            exchange.getResponseHeaders().putAll(headers);
            exchange.getResponseHeaders().set("X-Cache", "MISS");
            exchange.sendResponseHeaders(cachedResponse.statusCode(), cachedResponse.body().length);
            exchange.getResponseBody().write(cachedResponse.body());
            exchange.close();
        }
    }
}
