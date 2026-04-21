package fr.smp.core.auth;

import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolve Minecraft usernames against Mojang's profile endpoint.
 *
 * Used by AuthBridge to decide whether a connecting username corresponds to a
 * registered premium account. Premium → force online-mode handshake. Unknown
 * (404) or unreachable Mojang → treat as cracked.
 *
 * Results are cached for 10 minutes to avoid pounding the API on reconnects.
 * Negative results have a shorter TTL because a name can be claimed at any time.
 */
public final class MojangApi {

    private static final URI BASE = URI.create("https://api.mojang.com/users/profiles/minecraft/");
    private static final Duration POSITIVE_TTL = Duration.ofMinutes(10);
    private static final Duration NEGATIVE_TTL = Duration.ofMinutes(2);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(4);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Logger logger;

    public MojangApi(Logger logger) {
        this.logger = logger;
    }

    /**
     * Result of a Mojang lookup. PREMIUM → name is registered to a premium
     * account; CRACKED → 404; UNKNOWN → API timeout / 5xx (caller decides
     * fallback policy).
     */
    public enum Status { PREMIUM, CRACKED, UNKNOWN }

    public CompletableFuture<Status> lookup(String username) {
        if (username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(Status.UNKNOWN);
        }
        String key = username.toLowerCase();
        CacheEntry hit = cache.get(key);
        long now = System.currentTimeMillis();
        if (hit != null && now < hit.expiresAt) {
            return CompletableFuture.completedFuture(hit.status);
        }

        HttpRequest req = HttpRequest.newBuilder(BASE.resolve(username))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "SMP-Auth/1.0")
                .GET()
                .build();

        return http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .thenApply(resp -> {
                    Status s;
                    int code = resp.statusCode();
                    if (code == 200) s = Status.PREMIUM;
                    else if (code == 204 || code == 404) s = Status.CRACKED;
                    else {
                        logger.warn("Mojang returned HTTP {} for '{}', treating as UNKNOWN", code, username);
                        s = Status.UNKNOWN;
                    }
                    putCache(key, s);
                    return s;
                })
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof java.util.concurrent.CompletionException && ex.getCause() != null ? ex.getCause() : ex;
                    logger.warn("Mojang lookup failed for '{}': {}", username, cause.getClass().getSimpleName());
                    return Status.UNKNOWN;
                });
    }

    private void putCache(String key, Status status) {
        long ttl = status == Status.PREMIUM ? POSITIVE_TTL.toMillis() : NEGATIVE_TTL.toMillis();
        if (status == Status.UNKNOWN) return; // never cache failures
        cache.put(key, new CacheEntry(status, System.currentTimeMillis() + ttl));
    }

    private record CacheEntry(Status status, long expiresAt) {}
}
