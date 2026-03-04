import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache for HTTP responses, with optional file-backed persistence.
 *
 * <p>Cache keys are the request path (and query string where present). Entries are stored in a
 * {@link ConcurrentHashMap} and can be serialized to / deserialized from a file using Java
 * object serialization.
 */
public class CacheStore {
    private final ConcurrentHashMap<String, CachedResponse> cache;

    /** Creates an empty cache. */
    public CacheStore() {
        this.cache = new ConcurrentHashMap<>();
    }

    private CacheStore(ConcurrentHashMap<String, CachedResponse> cache) {
        this.cache = cache;
    }

    /**
     * Loads a {@code CacheStore} from a previously saved file.
     *
     * <p>If the file does not exist, an empty cache is returned rather than throwing.
     *
     * @param file path to the serialized cache file
     * @return a {@code CacheStore} populated with the file's contents, or an empty one if the
     *         file does not exist
     * @throws IOException if the file exists but cannot be read or is corrupted/incompatible
     */
    public static CacheStore loadFrom(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new CacheStore();
        }
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            // Safe: the file was written by save(), which always serializes a ConcurrentHashMap<String, CachedResponse>
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, CachedResponse> map = (ConcurrentHashMap<String, CachedResponse>) ois.readObject();
            return new CacheStore(map);
        } catch (ClassNotFoundException e) {
            throw new IOException("Cache file is incompatible or corrupted", e);
        }
    }

    /**
     * Serializes the current cache contents to a file, creating parent directories if needed.
     *
     * @param file path to write the cache to
     * @throws IOException if the file cannot be written
     */
    public void save(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            oos.writeObject(cache);
        }
    }

    /**
     * Returns the cached response for the given key, if present.
     *
     * @param key the cache key (request path, with query string if applicable)
     * @return an {@link Optional} containing the cached response, or empty if not cached
     */
    public Optional<CachedResponse> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    /**
     * Stores a response in the cache under the given key.
     *
     * @param key   the cache key (request path, with query string if applicable)
     * @param value the response to cache
     */
    public void put(String key, CachedResponse value) {
        cache.put(key, value);
    }

    /** Removes all entries from the cache. */
    public void clear() {
        cache.clear();
    }
}
