import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class ProxyServer {
    private final HttpServer server;
    private final CacheStore cache;

    public ProxyServer(ProxyConfig config, CacheStore cache) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        this.cache = cache;
        server.createContext("/", new ProxyHandler(cache, config));
    }

    public void start() {
        server.start();
        System.out.println("Proxy server listening on port " + server.getAddress().getPort());
    }
}