package com.christiankiernan.cachingproxy.cache;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache for HTTP responses, with optional file-backed persistence.
 *
 * <p>Cache keys are the request path (and query string where present). Entries are stored in a
 * {@link ConcurrentHashMap} and can be serialized to / deserialized from a file using Java
 * object serialization. A configurable {@code maxSize} prevents unbounded growth; once the
 * limit is reached, new entries are silently dropped until space is freed by eviction or clear.
 */
public class CacheStore {
    private final ConcurrentHashMap<String, CachedResponse> cache;
    private final int maxSize;

    /** Creates an empty cache with no size limit. */
    public CacheStore() {
        this(Integer.MAX_VALUE);
    }

    /**
     * Creates an empty cache that holds at most {@code maxSize} entries.
     *
     * @param maxSize maximum number of entries to retain; must be positive
     */
    public CacheStore(int maxSize) {
        this.cache = new ConcurrentHashMap<>();
        this.maxSize = maxSize;
    }

    private CacheStore(ConcurrentHashMap<String, CachedResponse> cache, int maxSize) {
        this.cache = cache;
        this.maxSize = maxSize;
    }

    /**
     * Loads a {@code CacheStore} from a previously saved file.
     *
     * <p>If the file does not exist, an empty cache is returned rather than throwing.
     *
     * @param file    path to the serialized cache file
     * @param maxSize maximum number of entries the loaded cache may hold
     * @return a {@code CacheStore} populated with the file's contents, or an empty one if the
     *         file does not exist
     * @throws IOException if the file exists but cannot be read or is corrupted/incompatible
     */
    public static CacheStore loadFrom(Path file, int maxSize) throws IOException {
        if (!Files.exists(file)) {
            return new CacheStore(maxSize);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            // Safe: the file was written by save(), which always serializes a ConcurrentHashMap<String, CachedResponse>
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, CachedResponse> map = (ConcurrentHashMap<String, CachedResponse>) ois.readObject();
            return new CacheStore(map, maxSize);
        } catch (ClassNotFoundException e) {
            throw new IOException("Cache file is incompatible or corrupted", e);
        }
    }

    /**
     * Loads a {@code CacheStore} from a previously saved file with no size limit.
     *
     * <p>If the file does not exist, an empty cache is returned rather than throwing.
     *
     * @param file path to the serialized cache file
     * @return a {@code CacheStore} populated with the file's contents, or an empty one if the
     *         file does not exist
     * @throws IOException if the file exists but cannot be read or is corrupted/incompatible
     */
    public static CacheStore loadFrom(Path file) throws IOException {
        return loadFrom(file, Integer.MAX_VALUE);
    }

    /**
     * Serializes the current cache contents to a file, creating parent directories if needed.
     *
     * <p>Writes to a temporary file in the same directory first, then atomically renames it
     * to the target path to prevent a partially written file if the process is interrupted.
     *
     * @param file path to write the cache to
     * @throws IOException if the file cannot be written
     */
    public void save(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempDir = parent != null ? parent : Path.of(".");
        Path temp = Files.createTempFile(tempDir, "cache-", ".tmp");
        try {
            try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                oos.writeObject(cache);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Fall back to non-atomic replace if the filesystem does not support atomic moves
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
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
     * Stores a response in the cache under the given key, if the cache has not reached its
     * maximum size. If the cache is full, the entry is silently dropped.
     *
     * @param key   the cache key (request path, with query string if applicable)
     * @param value the response to cache
     */
    public void put(String key, CachedResponse value) {
        if (cache.size() < maxSize) {
            cache.put(key, value);
        }
    }

    /**
     * Removes the entry for the given key, if present.
     *
     * @param key the cache key to evict
     */
    public void evict(String key) {
        cache.remove(key);
    }

    /** Removes all entries from the cache. */
    public void clear() {
        cache.clear();
    }
}
