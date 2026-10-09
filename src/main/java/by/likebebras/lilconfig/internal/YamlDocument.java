package by.likebebras.lilconfig.internal;

import by.likebebras.lilconfig.ConfigException;
import by.likebebras.lilconfig.annotation.Comment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bukkit YAML backend with strict loading, literal keys and atomic file replacement. */
public final class YamlDocument {
    private final YamlConfiguration yaml;

    private YamlDocument(YamlConfiguration yaml) { this.yaml = yaml; }

    public static YamlDocument read(Path file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator(ConfigMapper.SEPARATOR);
        yaml.options().parseComments(true);
        if (Files.exists(file)) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            // Check duplicates and cycles before Bukkit expands mappings into sections.
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setNestingDepthLimit(128);
            Object parsed = new Yaml(new SafeConstructor(options)).load(content);
            checkTree(parsed, new IdentityHashMap<>(), 0);
            yaml.loadFromString(content);
        }
        return new YamlDocument(yaml);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> values() {
        return (Map<String, Object>) normalize(yaml);
    }

    public void apply(ConfigMapper.Encoded encoded, Class<?> model) {
        merge(encoded.values(), "", encoded.objects());
        encoded.comments().forEach(yaml::setComments);
        Comment header = model.getAnnotation(Comment.class);
        if (header != null) yaml.options().setHeader(List.of(header.value()));
    }

    private void merge(Map<?, ?> values, String parent, Set<String> objects) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = entry.getKey().toString();
            String path = parent.isEmpty() ? key : parent + ConfigMapper.SEPARATOR + key;
            if (objects.contains(path) && entry.getValue() instanceof Map<?, ?> children) {
                if (!yaml.isConfigurationSection(path)) yaml.createSection(path);
                merge(children, path, objects);
            } else {
                // Replacing collections/maps also removes deleted entries.
                yaml.set(path, entry.getValue());
            }
        }
    }

    public void save(Path file) throws IOException {
        String content = yaml.saveToString();
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".lilconfig-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Object normalize(Object value) {
        if (value instanceof ConfigurationSection section) return normalize(section.getValues(false));
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (result.containsKey(key)) throw new ConfigException("Duplicate YAML key after string conversion: " + key);
                result.put(key, normalize(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) result.add(normalize(item));
            return result;
        }
        return value;
    }

    private static void checkTree(Object value, IdentityHashMap<Object, Boolean> active, int depth) {
        if (depth > 128) throw new ConfigException("YAML nesting exceeds 128");
        if (!(value instanceof Map<?, ?>) && !(value instanceof Collection<?>)) return;
        if (active.put(value, Boolean.TRUE) != null) throw new ConfigException("Cyclic YAML aliases are unsupported");
        try {
            if (value instanceof Map<?, ?> map) {
                Set<String> keys = new HashSet<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = entry.getKey();
                    if (!(key instanceof String || key instanceof Number || key instanceof Boolean)) throw new ConfigException("YAML keys must be scalar strings, numbers or booleans");
                    String text = key.toString();
                    // '==' belongs to Bukkit's own serialized objects.
                    if (!text.equals("==")) ConfigMapper.checkKey(text);
                    if (!keys.add(text)) throw new ConfigException("Duplicate YAML key after string conversion: " + text);
                    checkTree(entry.getValue(), active, depth + 1);
                }
            } else if (value instanceof Collection<?> collection) {
                for (Object item : collection) checkTree(item, active, depth + 1);
            }
        } finally {
            active.remove(value);
        }
    }
}
