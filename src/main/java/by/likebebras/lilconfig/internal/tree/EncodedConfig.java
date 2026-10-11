package by.likebebras.lilconfig.internal.tree;

import java.lang.reflect.AnnotatedElement;
import java.util.*;

/** Mapping result consumed by the YAML backend, independent of its implementation. */
public record EncodedConfig(Map<String, Object> values, Set<String> objects,
                            Map<String, List<String>> comments,
                            Map<List<String>, List<AnnotatedElement>> formatting,
                            Map<List<String>, List<String>> propertyOrder) {
    public EncodedConfig() {
        this(new LinkedHashMap<>(), new LinkedHashSet<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>(), new LinkedHashMap<>());
    }
}
