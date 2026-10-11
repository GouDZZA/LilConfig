package by.likebebras.lilconfig;

import by.likebebras.lilconfig.adapter.ConfigAdapter;
import by.likebebras.lilconfig.annotation.mapping.ConfigFile;
import by.likebebras.lilconfig.annotation.mapping.KeysFormat;
import by.likebebras.lilconfig.format.KeyFormat;
import by.likebebras.lilconfig.internal.mapping.ConfigMapper;
import by.likebebras.lilconfig.internal.mapping.ModelFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** A directory and adapter registry. Configure adapters before loading handles. */
public final class ConfigProvider {
    private final Path directory;
    private final Map<Class<?>, ConfigAdapter<?>> adapters = new LinkedHashMap<>();

    ConfigProvider(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    public Path directory() { return directory; }

    /** Exact-type registration; each new handle receives an adapter snapshot. */
    public synchronized <T> ConfigProvider adapter(Class<T> type, ConfigAdapter<T> adapter) {
        adapters.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(adapter, "adapter"));
        return this;
    }

    public <T> Config<T> load(Class<T> type) { return load(fileName(type), type); }

    public <T> Config<T> load(String fileName, Class<T> type) {
        return load(fileName, type, () -> ModelFactory.defaults(type));
    }

    /** The supplier provides record defaults or customized POJO defaults on every reload. */
    public <T> Config<T> load(Class<T> type, Supplier<? extends T> defaults) {
        return load(fileName(type), type, defaults);
    }

    public synchronized <T> Config<T> load(String fileName, Class<T> type, Supplier<? extends T> defaults) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaults, "defaults");
        KeysFormat format = type.getAnnotation(KeysFormat.class);
        return new Config<>(resolve(fileName), type, defaults, new ConfigMapper(Map.copyOf(adapters),
                format == null ? KeyFormat.IDENTITY : format.value()));
    }

    private static String fileName(Class<?> type) {
        Objects.requireNonNull(type, "type");
        ConfigFile annotation = type.getAnnotation(ConfigFile.class);
        if (annotation != null) return annotation.value();
        String name = type.getSimpleName().replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("([A-Z])([A-Z][a-z])", "$1-$2");
        return name.toLowerCase(java.util.Locale.ROOT) + ".yml";
    }

    private Path resolve(String name) {
        Objects.requireNonNull(name, "fileName");
        Path relative = Path.of(name);
        Path file = directory.resolve(relative).normalize();
        if (name.isBlank() || relative.isAbsolute() || !file.startsWith(directory) || file.equals(directory)) {
            throw new ConfigException("Config file must be inside " + directory + ": " + name);
        }
        for (Path current = file; !current.equals(directory); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new ConfigException("Symbolic links are not allowed in config paths: " + current);
            }
        }
        return file;
    }
}
