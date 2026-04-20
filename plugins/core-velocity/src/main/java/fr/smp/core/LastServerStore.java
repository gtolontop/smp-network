package fr.smp.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LastServerStore {

    private final Path file;
    private final Map<UUID, String> map = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public LastServerStore(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
                try {
                    map.put(UUID.fromString(e.getKey()), e.getValue().getAsString());
                } catch (Exception ignored) {}
            }
        } catch (IOException ignored) {}
    }

    private synchronized void save() {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            JsonObject obj = new JsonObject();
            map.forEach((k, v) -> obj.addProperty(k.toString(), v));
            try (Writer w = Files.newBufferedWriter(file)) {
                gson.toJson(obj, w);
            }
        } catch (IOException ignored) {}
    }

    public Optional<String> get(UUID uuid) {
        return Optional.ofNullable(map.get(uuid));
    }

    public void put(UUID uuid, String server) {
        String prev = map.put(uuid, server);
        if (!Objects.equals(prev, server)) save();
    }
}
