import java.net.URI;
import java.time.Duration;

public record ProxyConfig(
    int port,
    URI originBaseUri,
    int threadPoolSize,
    Duration originTimeOut
    ) {}
