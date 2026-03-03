import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CacheStore {
    private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();

    public Optional<CachedResponse> get(String key) {
        return Optional.ofNullable(cache.get(key));
    }

    public void put(String key, CachedResponse value) {
        cache.put(key, value);
    }

    public void clear() {
        cache.clear();
    }
}
