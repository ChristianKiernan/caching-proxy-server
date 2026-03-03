import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

@Command(
        name = "caching-proxy",
        mixinStandardHelpOptions = true, // adds --help and --version
        description = "Lightweight HTTP caching proxy (MVP)."
)
public class ProxyCliCommand implements Runnable {

    @Option(names = "--port", description = "Port to listen on (e.g., 3000).")
    private Integer port;

    @Option(names = "--origin", description = "Origin base URL (e.g., http://dummyjson.com).")
    private URI origin;

    @Option(names = "--clear-cache", description = "Clear the cache and exit.")
    private boolean clearCache;

    @Override
    public void run() {
        // 1) Clear-cache mode
        if (clearCache) {
            // Later: cacheStore.clear();
            System.out.println("Cache cleared.");
            return;
        }

        // 2) Run mode validation
        if (port == null || origin == null) {
            System.err.println("Error: --port and --origin are required unless --clear-cache is used.");
            System.err.println("Try: caching-proxy --help");
            return;
        }

        // 3) Start server
        ProxyConfig config = new ProxyConfig(this.port, this.origin, 4, Duration.ofSeconds(10));
        try {
            CacheStore cacheStore = new CacheStore();
            ProxyServer server = new ProxyServer(config, cacheStore);
            server.start();
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }
}