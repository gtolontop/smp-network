package fr.smp.ptr.foundation;

import fr.smp.ptr.foundation.boss.Telegraph;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

/** Looks up {@link Telegraph}s by id. Foundation-provided telegraphs only. */
public final class TelegraphCatalogue {

    private final Map<NamespacedKey, Telegraph> entries;

    public TelegraphCatalogue(@NotNull List<Telegraph> telegraphs) {
        Objects.requireNonNull(telegraphs, "telegraphs");
        Map<NamespacedKey, Telegraph> mutable = new LinkedHashMap<>();
        for (Telegraph t : telegraphs) {
            mutable.put(t.id(), t);
        }
        this.entries = Map.copyOf(mutable);
    }

    public @NotNull Optional<Telegraph> get(@NotNull NamespacedKey id) {
        return Optional.ofNullable(entries.get(Objects.requireNonNull(id, "id")));
    }

    public @NotNull Map<NamespacedKey, Telegraph> view() {
        return entries;
    }

    public int size() {
        return entries.size();
    }
}
