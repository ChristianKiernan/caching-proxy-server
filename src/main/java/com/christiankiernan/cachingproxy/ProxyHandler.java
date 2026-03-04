package com.christiankiernan.cachingproxy;

import com.christiankiernan.cachingproxy.cache.CachedResponse;
import com.christiankiernan.cachingproxy.cache.CacheStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@link HttpHandler} that serves requests from cache when possible, and forwards cache misses
 * to the configured origin server.
 *
 * <p>Every response is tagged with an {@code X-Cache} header: {@code HIT} when served from
 * cache, {@code MISS} when fetched from the origin. Caching is method-aware:
 * <ul>
 *   <li>GET responses are cached; subsequent identical requests are served as HITs.</li>
 *   <li>PUT, DELETE, and PATCH requests evict the cached entry for the target URL.</li>
 *   <li>POST and other methods are forwarded without reading or writing the cache.</li>
 * </ul>
 */
public class ProxyHandler implements HttpHandler {

    // Headers that are specific to the client-to-proxy connection and must not be forwarded upstream.
    // Also includes headers that java.net.http.HttpRequest restricts from being set.
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "content-length", "expect", "host", "keep-alive",
            "proxy-authenticate", "proxy-authorization", "te", "trailers",
            "transfer-encoding", "upgrade"
    );

    private final CacheStore cacheStore;
    private final ProxyConfig config;
    private final HttpClient client;

    /**
     * Creates a handler with the given {@link HttpClient}.
     *
     * @param cacheStore the cache to read from and write to
     * @param config     proxy configuration (origin URI, cache file path, etc.)
     * @param client     the HTTP client used to forward requests to the origin
     */
    public ProxyHandler(CacheStore cacheStore, ProxyConfig config, HttpClient client) {
        this.cacheStore = cacheStore;
        this.config = config;
        this.client = client;
    }

    /**
     * Handles an incoming HTTP request.
     *
     * <p>For GET requests, checks the cache first and returns a HIT immediately if found.
     * Otherwise, forwards the request to the origin and caches the response. For PUT, DELETE,
     * and PATCH requests, evicts any cached entry for the URL before forwarding. POST and
     * other methods are forwarded without interacting with the cache.
     *
     * @param exchange the HTTP exchange representing the incoming request and outgoing response
     * @throws IOException if reading the request or writing the response fails
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String method = exchange.getRequestMethod();
        String cacheKey = buildCacheKey(uri.getPath(), uri.getQuery());

        // Serve GET hits from cache before reading the (typically empty) request body
        if ("GET".equalsIgnoreCase(method)) {
            Optional<CachedResponse> cached = cacheStore.get(cacheKey);
            if (cached.isPresent()) {
                writeResponse(exchange, cached.get(), "HIT");
                return;
            }
        }

        byte[] requestBody = exchange.getRequestBody().readAllBytes();

        // Mutating methods invalidate any cached entry for this URL
        if ("PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            cacheStore.evict(cacheKey);
        }

        CachedResponse response;
        try {
            response = fetchFromOrigin(uri.getPath(), uri.getQuery(), method, requestBody, exchange.getRequestHeaders());
        } catch (IOException e) {
            byte[] body = "502 Bad Gateway".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("X-Cache", "MISS");
            exchange.sendResponseHeaders(502, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }

        // Only cache GET responses
        if ("GET".equalsIgnoreCase(method)) {
            cacheStore.put(cacheKey, response);
            persistIfEnabled();
        }

        writeResponse(exchange, response, "MISS");
    }

    private String buildCacheKey(String path, String query) {
        return query != null ? path + "?" + query : path;
    }

    private CachedResponse fetchFromOrigin(String path, String query, String method,
                                           byte[] requestBody, Map<String, List<String>> requestHeaders) throws IOException {
        String relativePath = query != null ? path.substring(1) + "?" + query : path.substring(1);
        URI targetUri = config.originBaseUri().resolve(relativePath);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(config.originTimeOut())
                .method(method, requestBody.length > 0
                        ? HttpRequest.BodyPublishers.ofByteArray(requestBody)
                        : HttpRequest.BodyPublishers.noBody());

        // Forward request headers, skipping hop-by-hop and connection-specific headers
        requestHeaders.forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                values.forEach(value -> builder.header(name, value));
            }
        });

        HttpResponse<byte[]> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request to origin interrupted", e);
        }

        // Filter out transfer-encoding, as it can conflict with how we send the response
        Map<String, List<String>> headers = new java.util.HashMap<>(response.headers().map());
        headers.remove("transfer-encoding");

        return new CachedResponse(response.statusCode(), headers, response.body());
    }

    private void persistIfEnabled() {
        if (config.cacheFilePath() != null) {
            try {
                cacheStore.save(config.cacheFilePath());
            } catch (IOException e) {
                System.err.println("Warning: failed to persist cache: " + e.getMessage());
            }
        }
    }

    private void writeResponse(HttpExchange exchange, CachedResponse response, String xCacheValue) throws IOException {
        exchange.getResponseHeaders().putAll(response.headers());
        exchange.getResponseHeaders().set("X-Cache", xCacheValue);
        exchange.sendResponseHeaders(response.statusCode(), response.body().length);
        exchange.getResponseBody().write(response.body());
        exchange.close();
    }
}
