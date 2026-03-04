import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CacheStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void getOnEmptyCacheReturnsEmpty() {
        CacheStore store = new CacheStore();
        assertEquals(Optional.empty(), store.get("/foo"));
    }

    @Test
    void putAndGet() {
        CacheStore store = new CacheStore();
        CachedResponse response = new CachedResponse(200, Map.of(), new byte[]{1, 2, 3});
        store.put("/foo", response);
        assertEquals(Optional.of(response), store.get("/foo"));
    }

    @Test
    void clearRemovesAllEntries() {
        CacheStore store = new CacheStore();
        store.put("/foo", new CachedResponse(200, Map.of(), new byte[0]));
        store.clear();
        assertEquals(Optional.empty(), store.get("/foo"));
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        Path file = tempDir.resolve("cache.dat");
        CacheStore store = new CacheStore();
        CachedResponse response = new CachedResponse(200, Map.of("Content-Type", List.of("text/plain")), "hello".getBytes());
        store.put("/foo", response);
        store.save(file);

        CacheStore loaded = CacheStore.loadFrom(file);
        Optional<CachedResponse> result = loaded.get("/foo");
        assertTrue(result.isPresent());
        assertEquals(200, result.get().statusCode());
        assertArrayEquals("hello".getBytes(), result.get().body());
    }

    @Test
    void loadFromNonExistentFileReturnsEmptyStore() throws IOException {
        Path file = tempDir.resolve("missing.dat");
        CacheStore store = CacheStore.loadFrom(file);
        assertEquals(Optional.empty(), store.get("/foo"));
    }

    @Test
    void loadFromCorruptedFileThrowsIOException() throws IOException {
        Path file = tempDir.resolve("corrupt.dat");
        Files.writeString(file, "not valid serialized data");
        assertThrows(IOException.class, () -> CacheStore.loadFrom(file));
    }

    @Test
    void evictRemovesSingleEntry() {
        CacheStore store = new CacheStore();
        store.put("/foo", new CachedResponse(200, Map.of(), new byte[0]));
        store.put("/bar", new CachedResponse(200, Map.of(), new byte[0]));
        store.evict("/foo");
        assertEquals(Optional.empty(), store.get("/foo"));
        assertTrue(store.get("/bar").isPresent());
    }

    @Test
    void putDoesNotExceedMaxSize() {
        CacheStore store = new CacheStore(2);
        store.put("/a", new CachedResponse(200, Map.of(), new byte[0]));
        store.put("/b", new CachedResponse(200, Map.of(), new byte[0]));
        store.put("/c", new CachedResponse(200, Map.of(), new byte[0])); // should be dropped
        assertTrue(store.get("/a").isPresent());
        assertTrue(store.get("/b").isPresent());
        assertEquals(Optional.empty(), store.get("/c"));
    }
}
