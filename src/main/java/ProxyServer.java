import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.Executors;

/**
 * Thin wrapper around {@link HttpServer} that wires together the proxy's request handler and
 * exposes a simple {@link #start()} lifecycle method.
 */
public class ProxyServer {
    private final HttpServer server;

    /**
     * Creates and configures the HTTP server but does not start it.
     *
     * @param config proxy configuration (port, origin URI, etc.)
     * @param cache  the cache shared between the server and its handler
     * @throws IOException if the server socket cannot be bound to the configured port
     */
    public ProxyServer(ProxyConfig config, CacheStore cache) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", new ProxyHandler(cache, config, HttpClient.newHttpClient()));
    }

    /**
     * Starts accepting incoming connections and logs the listening port.
     */
    public void start() {
        server.start();
        System.out.println("Proxy server listening on port " + server.getAddress().getPort());
    }
}