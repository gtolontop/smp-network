package fr.smp.core.npc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Récupère un skin Mojang à partir d'un pseudo via les API officielles.
 * Les résultats sont mis en cache en mémoire (pas besoin de persister —
 * le skin est stocké dans la DB une fois le NPC créé).
 */
public final class SkinFetcher {

    public record Skin(String ownerName, String value, String signature) {}

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .executor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "SMPCore-SkinFetcher");
                t.setDaemon(true);
                return t;
            }))
            .build();

    private static final Map<String, Skin> CACHE = new HashMap<>();

    private SkinFetcher() {}

    public static CompletableFuture<Skin> fetch(String name) {
        if (name == null || name.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String key = name.toLowerCase();
        synchronized (CACHE) {
            Skin cached = CACHE.get(key);
            if (cached != null) return CompletableFuture.completedFuture(cached);
        }

        String uuidUrl = "https://api.mojang.com/users/profiles/minecraft/" + name;
        HttpRequest req1 = HttpRequest.newBuilder(URI.create(uuidUrl))
                .timeout(Duration.ofSeconds(5))
                .GET().build();

        return CLIENT.sendAsync(req1, HttpResponse.BodyHandlers.ofString())
                .thenCompose(resp -> {
                    if (resp.statusCode() != 200) return CompletableFuture.completedFuture(null);
                    String body = resp.body();
                    String uuid = extractJsonString(body, "id");
                    String realName = extractJsonString(body, "name");
                    if (uuid == null) return CompletableFuture.completedFuture(null);
                    String formatted = uuid.replaceFirst(
                            "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                            "$1-$2-$3-$4-$5");
                    String profUrl = "https://sessionserver.mojang.com/session/minecraft/profile/"
                            + formatted + "?unsigned=false";
                    HttpRequest req2 = HttpRequest.newBuilder(URI.create(profUrl))
                            .timeout(Duration.ofSeconds(5))
                            .GET().build();
                    return CLIENT.sendAsync(req2, HttpResponse.BodyHandlers.ofString())
                            .thenApply(resp2 -> {
                                if (resp2.statusCode() != 200) return null;
                                String b = resp2.body();
                                String value = extractProperty(b, "value");
                                String signature = extractProperty(b, "signature");
                                if (value == null) return null;
                                Skin s = new Skin(realName != null ? realName : name, value, signature);
                                synchronized (CACHE) { CACHE.put(key, s); }
                                return s;
                            });
                })
                .exceptionally(ex -> null);
    }

    private static String extractJsonString(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        int c = json.indexOf(':', i);
        if (c < 0) return null;
        int q1 = json.indexOf('"', c);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    /**
     * Les propriétés sont dans un tableau; on extrait via une recherche textuelle
     * du champ (value/signature) — assez pour ce cas d'usage sans dépendance JSON.
     */
    private static String extractProperty(String json, String field) {
        int i = json.indexOf("\"" + field + "\"");
        if (i < 0) return null;
        int c = json.indexOf(':', i);
        if (c < 0) return null;
        int q1 = json.indexOf('"', c);
        if (q1 < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int k = q1 + 1; k < json.length(); k++) {
            char ch = json.charAt(k);
            if (ch == '\\' && k + 1 < json.length()) {
                char n = json.charAt(k + 1);
                if (n == '"' || n == '\\' || n == '/') { sb.append(n); k++; }
                else sb.append(ch);
            } else if (ch == '"') {
                return sb.toString();
            } else {
                sb.append(ch);
            }
        }
        return null;
    }
}
