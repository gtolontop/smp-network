package fr.smp.ptr.foundation.storage;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Typed read/write helpers around {@link PersistentDataContainer}.
 *
 * <p>The Bukkit PDC API is untyped at the call site (you specify the
 * {@link PersistentDataType} every time). These helpers fix the {@link
 * NamespacedKey} → type mapping in one place and add Optional-returning
 * reads so callers stop needing nullable annotations everywhere.
 *
 * @param <T> Java type read/written
 */
public abstract class PtrPdcCodec<T> {

    public abstract @NotNull T read(@NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key);

    public abstract void write(
            @NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key, @NotNull T value);

    /** Optional read; returns {@link Optional#empty()} when the key is absent. */
    public final @NotNull Optional<T> readOpt(
            @NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key) {
        return has(pdc, key) ? Optional.of(read(pdc, key)) : Optional.empty();
    }

    public final boolean has(
            @NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key) {
        return Objects.requireNonNull(pdc, "pdc").has(Objects.requireNonNull(key, "key"));
    }

    public final void remove(
            @NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key) {
        Objects.requireNonNull(pdc, "pdc").remove(Objects.requireNonNull(key, "key"));
    }

    /** {@code String} codec. */
    public static final PtrPdcCodec<String> STRING =
            new PtrPdcCodec<>() {
                @Override
                public @NotNull String read(
                        @NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key) {
                    String value = pdc.get(key, PersistentDataType.STRING);
                    if (value == null) {
                        throw new IllegalStateException("Missing PDC entry: " + key);
                    }
                    return value;
                }

                @Override
                public void write(
                        @NotNull PersistentDataContainer pdc,
                        @NotNull NamespacedKey key,
                        @NotNull String value) {
                    pdc.set(key, PersistentDataType.STRING, value);
                }
            };

    /** {@code int} codec. */
    public static final PtrPdcCodec<Integer> INTEGER =
            new PtrPdcCodec<>() {
                @Override
                public @NotNull Integer read(
                        @NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key) {
                    Integer value = pdc.get(key, PersistentDataType.INTEGER);
                    if (value == null) {
                        throw new IllegalStateException("Missing PDC entry: " + key);
                    }
                    return value;
                }

                @Override
                public void write(
                        @NotNull PersistentDataContainer pdc,
                        @NotNull NamespacedKey key,
                        @NotNull Integer value) {
                    pdc.set(key, PersistentDataType.INTEGER, value);
                }
            };

    /** {@code UUID} codec — stored as the canonical 36-char string. */
    public static final PtrPdcCodec<UUID> UUID =
            new PtrPdcCodec<>() {
                @Override
                public @NotNull UUID read(
                        @NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key) {
                    return java.util.UUID.fromString(STRING.read(pdc, key));
                }

                @Override
                public void write(
                        @NotNull PersistentDataContainer pdc,
                        @NotNull NamespacedKey key,
                        @NotNull UUID value) {
                    STRING.write(pdc, key, value.toString());
                }
            };

    /** {@code NamespacedKey} codec — stored as {@code namespace:path}. */
    public static final PtrPdcCodec<NamespacedKey> NAMESPACED_KEY =
            new PtrPdcCodec<>() {
                @Override
                public @NotNull NamespacedKey read(
                        @NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key) {
                    NamespacedKey parsed = NamespacedKey.fromString(STRING.read(pdc, key));
                    if (parsed == null) {
                        throw new IllegalStateException("Invalid namespaced key under " + key);
                    }
                    return parsed;
                }

                @Override
                public void write(
                        @NotNull PersistentDataContainer pdc,
                        @NotNull NamespacedKey key,
                        @NotNull NamespacedKey value) {
                    STRING.write(pdc, key, value.toString());
                }
            };

    /** {@code byte[]} codec. */
    public static final PtrPdcCodec<byte[]> BYTES =
            new PtrPdcCodec<>() {
                @Override
                public byte @NotNull [] read(
                        @NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key) {
                    byte[] value = pdc.get(key, PersistentDataType.BYTE_ARRAY);
                    if (value == null) {
                        throw new IllegalStateException("Missing PDC entry: " + key);
                    }
                    return value;
                }

                @Override
                public void write(
                        @NotNull PersistentDataContainer pdc,
                        @NotNull NamespacedKey key,
                        byte @NotNull [] value) {
                    pdc.set(key, PersistentDataType.BYTE_ARRAY, value);
                }
            };

    /** {@link Component} codec — stored as Gson-serialised JSON. */
    public static final PtrPdcCodec<Component> COMPONENT =
            new PtrPdcCodec<>() {
                @Override
                public @NotNull Component read(
                        @NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key) {
                    return GsonComponentSerializer.gson().deserialize(STRING.read(pdc, key));
                }

                @Override
                public void write(
                        @NotNull PersistentDataContainer pdc,
                        @NotNull NamespacedKey key,
                        @NotNull Component value) {
                    STRING.write(pdc, key, GsonComponentSerializer.gson().serialize(value));
                }
            };

    /**
     * Helper for callers that want a nullable read without {@link Optional}
     * — most useful at the boundary between foundation and content code.
     */
    public final @Nullable T readOrNull(
            @NotNull PersistentDataContainer pdc, @NotNull NamespacedKey key) {
        return has(pdc, key) ? read(pdc, key) : null;
    }

    /** Tag-stable hint: total bytes used by {@code value} under {@code key}. */
    public final int bytesOf(@NotNull String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
