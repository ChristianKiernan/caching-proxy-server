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

public class ProxyHandler implements HttpHandler {

    private final CacheStore cacheStore;
    private final ProxyConfig config;
    private final HttpClient client = HttpClient.newHttpClient();

    public ProxyHandler(CacheStore cacheStore, ProxyConfig config) {
        this.cacheStore = cacheStore;
        this.config = config;
    }

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
            URI targetUri = config.originBaseUri().resolve(path.substring(1));
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

            exchange.getResponseHeaders().putAll(headers);
            exchange.getResponseHeaders().set("X-Cache", "MISS");
            exchange.sendResponseHeaders(cachedResponse.statusCode(), cachedResponse.body().length);
            exchange.getResponseBody().write(cachedResponse.body());
            exchange.close();
        }
    }
}
