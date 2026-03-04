import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Picocli command that drives the caching proxy.
 *
 * <p>Supports two modes of operation:
 * <ul>
 *   <li><b>Clear-cache mode</b> ({@code --clear-cache}): deletes the cache file and exits.</li>
 *   <li><b>Proxy mode</b> ({@code --port} + {@code --origin}): starts the proxy server,
 *       optionally loading a pre-existing cache from {@code --cache-file}.</li>
 * </ul>
 */
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

    @Option(names = "--cache-file", description = "Path to the cache file for persistence.")
    private Path cacheFile;

    @Option(names = "--clear-cache", description = "Clear the cache and exit.")
    private boolean clearCache;

    /**
     * Executes the command.
     *
     * <p>In clear-cache mode, deletes the cache file pointed to by {@code --cache-file} and
     * returns. In proxy mode, validates required options, optionally restores a persisted cache,
     * and starts the HTTP server (blocking until the process is terminated).
     */
    @Override
    public void run() {
        if (clearCache) {
            runClearCache();
            return;
        }
        if (port == null || origin == null) {
            System.err.println("Error: --port and --origin are required unless --clear-cache is used.");
            System.err.println("Try: caching-proxy --help");
            return;
        }
        ProxyConfig config = new ProxyConfig(this.port, this.origin, 4, Duration.ofSeconds(10), cacheFile);
        try {
            ProxyServer server = new ProxyServer(config, loadCacheStore());
            server.start();
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    private void runClearCache() {
        if (cacheFile == null) {
            System.err.println("Error: --cache-file is required to use --clear-cache.");
            return;
        }
        if (!Files.exists(cacheFile)) {
            System.out.println("Cache already empty.");
            return;
        }
        try {
            Files.delete(cacheFile);
            System.out.println("Cache cleared.");
        } catch (IOException e) {
            System.err.println("Error clearing cache: " + e.getMessage());
        }
    }

    private CacheStore loadCacheStore() {
        CacheStore cacheStore;
        if (cacheFile != null) {
            try {
                cacheStore = CacheStore.loadFrom(cacheFile);
            } catch (IOException e) {
                System.err.println("Warning: could not load cache from file: " + e.getMessage());
                cacheStore = new CacheStore();
            }
        } else {
            cacheStore = new CacheStore();
        }
        return cacheStore;
    }
}