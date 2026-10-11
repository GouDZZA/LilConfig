package by.likebebras.lilconfig.internal.tree;

import by.likebebras.lilconfig.ConfigException;

import java.util.ArrayList;
import java.util.List;

/** Literal key rules and paths shared by model mapping and YAML processing. */
public final class YamlPaths {
    public static final char SEPARATOR = '\u001f';

    private YamlPaths() { }

    public static List<String> childPath(List<String> parent, String key) {
        List<String> child = new ArrayList<>(parent);
        child.add(key);
        return List.copyOf(child);
    }

    public static void checkKey(String key) {
        if (key.isEmpty() || key.indexOf(SEPARATOR) >= 0 || key.equals("==")) {
            throw new ConfigException("Empty keys, '==' and keys containing U+001F are reserved/unsupported: '" + key + "'");
        }
    }

}
