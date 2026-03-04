# Caching Proxy Server

A lightweight HTTP caching proxy written in Java 21. It sits between a client and an origin server, caching GET responses 
so that repeated requests are served locally rather than hitting the origin again. Every response includes an `X-Cache` 
header indicating whether it was a cache `HIT` or `MISS`.

## Features

- **GET caching**: responses are cached in memory and served on subsequent requests
- **Method-aware cache semantics**: PUT, DELETE, and PATCH evict the cached entry for the target URL; 
POST bypasses the cache entirely
- **Request forwarding**: request body and headers are forwarded upstream; hop-by-hop headers are filtered
- **Optional file persistence**: cache survives restarts when `--cache-file` is supplied
- **Max cache size**: configurable entry cap prevents unbounded memory growth
- **502 Bad Gateway**: returns a proper error response when the origin is unreachable
- **Graceful shutdown**: in-flight requests are given up to 5 seconds to complete on SIGTERM
- **Virtual threads**: each request is handled on its own Java 21 virtual thread

## Requirements

- Java 21+
- Maven 3.6+

## Build

```bash
git clone https://github.com/ChristianKiernan/caching-proxy-server.git
cd caching-proxy-server
mvn package
```

This produces a self-contained JAR at `target/caching-proxy-1.0-SNAPSHOT.jar` with all dependencies bundled.

## Usage

### Start the proxy

```bash
java -jar target/caching-proxy-1.0-SNAPSHOT.jar \
  --port 3000 \
  --origin https://dummyjson.com
```

Requests to `http://localhost:3000/products` will be forwarded to `https://dummyjson.com/products`.

<img width="972" height="82" alt="image" src="https://github.com/user-attachments/assets/34361848-1793-4ff2-9249-3a41199e3cb7" />

### All options

| Option | Description | Default |
|---|---|---|
| `--port <port>` | Port to listen on | *(required)* |
| `--origin <url>` | Origin base URL | *(required)* |
| `--cache-file <path>` | File path for cache persistence | *(disabled)* |
| `--max-cache-size <n>` | Maximum number of cached responses | `1000` |
| `--timeout <seconds>` | Origin request timeout in seconds | `10` |
| `--clear-cache` | Delete the cache file and exit | — |
| `--help` | Show usage information | — |

### With cache persistence

```bash
java -jar target/caching-proxy-1.0-SNAPSHOT.jar \
  --port 3000 \
  --origin https://dummyjson.com \
  --cache-file /tmp/proxy-cache.dat
```

The cache is loaded from the file on startup and saved after each new entry.

### Clear the cache

```bash
java -jar target/caching-proxy-1.0-SNAPSHOT.jar \
  --clear-cache \
  --cache-file /tmp/proxy-cache.dat
```

### Example request

```bash
# First request — fetched from origin
curl -i http://localhost:3000/products/1
# X-Cache: MISS
```
<img width="717" height="490" alt="Screenshot 2026-03-04 140217" src="https://github.com/user-attachments/assets/76ea0618-0664-49c8-b98f-3fae07d41cf0" />

```bash
# Second request — served from cache
curl -i http://localhost:3000/products/1
# X-Cache: HIT
```
<img width="673" height="481" alt="Screenshot 2026-03-04 140659" src="https://github.com/user-attachments/assets/9b1ae3ec-650c-4605-8ebf-42795cc64f55" />

## Project structure

```
src/
├── main/java/com/christiankiernan/cachingproxy/
│   ├── Main.java                    # Entry point
│   ├── ProxyCliCommand.java         # CLI options and startup logic (Picocli)
│   ├── ProxyConfig.java             # Immutable configuration record
│   ├── ProxyServer.java             # HTTP server lifecycle wrapper
│   ├── ProxyHandler.java            # Request handler: cache lookup, forwarding, eviction
│   └── cache/
│       ├── CacheStore.java          # Thread-safe in-memory cache with file persistence
│       └── CachedResponse.java      # Serializable response record
└── test/java/com/christiankiernan/cachingproxy/
    ├── ProxyHandlerTest.java
    ├── ProxyCliCommandTest.java
    └── cache/
        └── CacheStoreTest.java
```

## Architecture notes

- Uses `com.sun.net.httpserver.HttpServer` (no external server dependency)
- Uses `java.net.http.HttpClient` for upstream requests
- Cache is a `ConcurrentHashMap`, which is safe for concurrent access across virtual threads
- Cache persistence uses Java object serialization with atomic file writes (temporary file + rename)
