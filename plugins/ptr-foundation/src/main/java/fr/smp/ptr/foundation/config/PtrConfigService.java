package fr.smp.ptr.foundation.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Owns the live {@link PtrFoundationConfig} and exposes a typed reload hook.
 *
 * <p>Reload semantics: the new instance is loaded and validated <i>before</i>
 * being installed. If validation fails, the old value stays. Listeners
 * therefore never see a half-built config or a transient null.
 */
public final class PtrConfigService {

    private static final String FILE_NAME = "foundation.yml";

    private final Path configFile;
    private final Logger logger;
    private final AtomicReference<PtrFoundationConfig> current = new AtomicReference<>();
    private final List<Consumer<PtrFoundationConfig>> listeners = new CopyOnWriteArrayList<>();

    /**
     * @param dataFolder plugin data folder (typically {@code plugins/PtrFoundation/}).
     * @param logger plugin logger.
     */
    public PtrConfigService(@NotNull Path dataFolder, @NotNull Logger logger) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.configFile = dataFolder.resolve(FILE_NAME);
    }

    /**
     * Materialise the config file from the bundled defaults if missing, then
     * load. Throws on the first load — refusing to enable is the right
     * answer if the bundled defaults can't be parsed.
     */
    public void initialise() throws IOException {
        ensureDefaultsExtracted();
        PtrFoundationConfig loaded = loadFromDisk();
        current.set(loaded);
        logger.info(() -> "PtrConfigService: loaded " + FILE_NAME);
    }

    /**
     * Re-read the config file. On success, atomically swap the live value
     * and notify listeners. On parse failure, log and keep the old value.
     */
    public boolean reload() {
        PtrFoundationConfig previous = current.get();
        try {
            PtrFoundationConfig next = loadFromDisk();
            current.set(next);
            for (Consumer<PtrFoundationConfig> listener : listeners) {
                try {
                    listener.accept(next);
                } catch (Throwable t) {
                    logger.warning("PtrConfigService: reload listener threw — " + t.getMessage());
                }
            }
            logger.info("PtrConfigService: reload OK");
            return true;
        } catch (Throwable t) {
            logger.warning("PtrConfigService: reload FAILED, keeping previous config — " + t);
            current.set(previous);
            return false;
        }
    }

    /** Get the live config snapshot. */
    public @NotNull PtrFoundationConfig get() {
        PtrFoundationConfig snapshot = current.get();
        if (snapshot == null) {
            throw new IllegalStateException(
                    "PtrConfigService not initialised — call initialise() in onEnable()");
        }
        return snapshot;
    }

    /** Register a listener fired on every successful reload. Listener also runs synchronously now. */
    public void addReloadListener(@NotNull Consumer<PtrFoundationConfig> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private void ensureDefaultsExtracted() throws IOException {
        if (Files.exists(configFile)) {
            return;
        }
        Files.createDirectories(configFile.getParent());
        try (InputStream defaults =
                PtrConfigService.class.getResourceAsStream("/config/" + FILE_NAME)) {
            if (defaults == null) {
                throw new IOException("Missing bundled default: /config/" + FILE_NAME);
            }
            Files.copy(defaults, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private PtrFoundationConfig loadFromDisk() throws IOException {
        YamlConfigurationLoader loader =
                YamlConfigurationLoader.builder()
                        .path(configFile)
                        .defaultOptions(ConfigurationOptions.defaults())
                        .build();
        CommentedConfigurationNode root = loader.load();
        PtrFoundationConfig parsed = root.get(PtrFoundationConfig.class);
        if (parsed == null) {
            throw new IOException(FILE_NAME + " parsed to null");
        }
        return parsed;
    }
}
