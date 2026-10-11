package by.likebebras.lilconfig;

import by.likebebras.lilconfig.internal.mapping.ConfigMapper;
import by.likebebras.lilconfig.internal.tree.EncodedConfig;
import by.likebebras.lilconfig.internal.yaml.YamlDocument;
import by.likebebras.lilconfig.internal.yaml.YamlFormatter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/** A typed file handle. IO is synchronous; mutable model access is caller-managed. */
public final class Config<T> {
    private final Path file;
    private final Class<T> type;
    private final Supplier<? extends T> defaults;
    private final ConfigMapper mapper;
    private volatile T value;

    Config(Path file, Class<T> type, Supplier<? extends T> defaults, ConfigMapper mapper) {
        this.file = file;
        this.type = type;
        this.defaults = defaults;
        this.mapper = mapper;
        reload();
    }

    /** Current model. After reload, obtain it again; old references are not updated. */
    public T get() { return value; }

    public Path path() { return file; }

    /** Replaces the in-memory model after validation. Call save() to persist it. */
    public synchronized Config<T> set(T value) {
        Objects.requireNonNull(value, "value");
        try {
            encode(value);
            this.value = value;
            return this;
        } catch (RuntimeException e) {
            throw failure("replace", e);
        }
    }

    /** Loads a fresh model. Failures leave the current model and file unchanged. */
    public synchronized T reload() {
        try {
            boolean missing = Files.notExists(file);
            YamlDocument document = YamlDocument.read(file);
            T loaded = mapper.read(document.values(), type, defaults.get());
            EncodedConfig encoded = encode(loaded);
            if (missing) {
                document.apply(encoded, type);
                document.save(file);
            }
            value = loaded;
            return loaded;
        } catch (Exception e) {
            throw failure("load", e);
        }
    }

    /** Saves known fields, preserving unknown object fields and existing comments. */
    public synchronized void save() {
        try {
            EncodedConfig encoded = encode(value);
            YamlDocument document = YamlDocument.read(file);
            document.apply(encoded, type);
            document.save(file);
        } catch (Exception e) {
            throw failure("save", e);
        }
    }

    private EncodedConfig encode(T model) {
        EncodedConfig encoded = mapper.write(model, type);
        YamlFormatter.validate(encoded, type);
        return encoded;
    }

    private ConfigException failure(String action, Exception e) {
        return new ConfigException("Cannot " + action + " config '" + file + "': " + e.getMessage(), e);
    }
}
