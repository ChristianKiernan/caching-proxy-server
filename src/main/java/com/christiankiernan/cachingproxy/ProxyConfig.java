package com.christiankiernan.cachingproxy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Immutable configuration for the caching proxy server.
 *
 * @param port           the local port the proxy listens on
 * @param originBaseUri  the base URI of the upstream origin server (e.g. {@code http://dummyjson.com})
 * @param originTimeOut  maximum time to wait for a response from the origin
 * @param cacheFilePath  path to the file used for cache persistence, or {@code null} to disable persistence
 */
public record ProxyConfig(
    int port,
    URI originBaseUri,
    Duration originTimeOut,
    Path cacheFilePath
) {}
