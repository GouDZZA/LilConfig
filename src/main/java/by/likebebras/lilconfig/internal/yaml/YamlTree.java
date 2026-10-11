package by.likebebras.lilconfig.internal.yaml;

import by.likebebras.lilconfig.ConfigException;
import by.likebebras.lilconfig.internal.tree.YamlPaths;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/** Converts Bukkit sections to plain values and validates loaded YAML before expansion. */
final class YamlTree {
    private YamlTree() { }

    @SuppressWarnings("unchecked")
    static Map<String, Object> values(ConfigurationSection section) {
        return (Map<String, Object>) normalize(section);
    }

    static void validate(Object value) {
        checkTree(value, new IdentityHashMap<>(), 0);
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
                    if (!text.equals("==")) YamlPaths.checkKey(text);
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
