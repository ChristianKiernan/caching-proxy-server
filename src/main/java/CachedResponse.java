import java.util.List;
import java.util.Map;

public record CachedResponse(
    int statusCode,
    Map<String, List<String>> headers,
    byte[] body
    ) {}