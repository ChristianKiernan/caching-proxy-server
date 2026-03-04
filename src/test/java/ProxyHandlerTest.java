import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyHandlerTest {

    @Mock HttpClient mockClient;
    @Mock HttpExchange mockExchange;
    @Mock HttpResponse<byte[]> mockResponse;

    private ProxyConfig config = new ProxyConfig(
            3000, URI.create("http://origin.test"), 4, Duration.ofSeconds(10), null
    );

    @Test
    void cacheMissSetsXCacheMissHeader() throws Exception {
        Headers responseHeaders = new Headers();
        when(mockExchange.getRequestURI()).thenReturn(URI.create("/products"));
        when(mockExchange.getRequestMethod()).thenReturn("GET");
        when(mockExchange.getResponseHeaders()).thenReturn(responseHeaders);
        when(mockExchange.getResponseBody()).thenReturn(new ByteArrayOutputStream());
        doReturn(mockResponse).when(mockClient).send(any(), any());
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
        when(mockResponse.body()).thenReturn("hello".getBytes());

        new ProxyHandler(new CacheStore(), config, mockClient).handle(mockExchange);

        verify(mockClient).send(any(), any());
        assertEquals("MISS", responseHeaders.getFirst("X-Cache"));
    }

    @Test
    void cacheHitReturnsCachedResponseWithoutCallingOrigin() throws Exception {
        CacheStore cache = new CacheStore();
        cache.put("/products", new CachedResponse(200, Map.of(), "cached".getBytes()));

        Headers responseHeaders = new Headers();
        when(mockExchange.getRequestURI()).thenReturn(URI.create("/products"));
        when(mockExchange.getResponseHeaders()).thenReturn(responseHeaders);
        when(mockExchange.getResponseBody()).thenReturn(new ByteArrayOutputStream());

        new ProxyHandler(cache, config, mockClient).handle(mockExchange);

        verifyNoInteractions(mockClient);
        assertEquals("HIT", responseHeaders.getFirst("X-Cache"));
    }

    @Test
    void cacheMissForwardsQueryStringToOrigin() throws Exception {
        when(mockExchange.getRequestURI()).thenReturn(URI.create("/products?limit=10"));
        when(mockExchange.getRequestMethod()).thenReturn("GET");
        when(mockExchange.getResponseHeaders()).thenReturn(new Headers());
        when(mockExchange.getResponseBody()).thenReturn(new ByteArrayOutputStream());
        doReturn(mockResponse).when(mockClient).send(any(), any());
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true));
        when(mockResponse.body()).thenReturn(new byte[0]);

        new ProxyHandler(new CacheStore(), config, mockClient).handle(mockExchange);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockClient).send(captor.capture(), any());
        assertEquals("http://origin.test/products?limit=10", captor.getValue().uri().toString());
    }
}
