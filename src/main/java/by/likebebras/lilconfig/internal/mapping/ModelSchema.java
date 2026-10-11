package by.likebebras.lilconfig.internal.mapping;

import by.likebebras.lilconfig.ConfigException;
import by.likebebras.lilconfig.annotation.mapping.Ignore;
import by.likebebras.lilconfig.annotation.mapping.Key;
import by.likebebras.lilconfig.annotation.mapping.KeysFormat;
import by.likebebras.lilconfig.format.KeyFormat;

import java.lang.reflect.*;
import java.util.*;

import static by.likebebras.lilconfig.internal.tree.YamlPaths.checkKey;

/** Discovers model properties, resolves generic types and rejects ambiguous schemas. */
final class ModelSchema {
    private final KeyFormat defaultFormat;

    ModelSchema(KeyFormat defaultFormat) { this.defaultFormat = defaultFormat; }

    List<ModelProperty> properties(Type type) {
        KeysFormat annotation = Types.raw(type).getAnnotation(KeysFormat.class);
        return properties(type, annotation == null ? defaultFormat : annotation.value());
    }

    private List<ModelProperty> properties(Type type, KeyFormat format) {
        Class<?> raw = Types.raw(type);
        if (raw.getName().startsWith("java.") || raw.isInterface()) {
            throw new ConfigException("Unsupported model " + type.getTypeName() + "; register an adapter");
        }
        List<ModelProperty> result = new ArrayList<>();
        Map<TypeVariable<?>, Type> bindings = Types.bindings(type);
        if (raw.isRecord()) {
            for (RecordComponent component : raw.getRecordComponents()) {
                Field field;
                try { field = raw.getDeclaredField(component.getName()); }
                catch (NoSuchFieldException e) { throw new ConfigException("Missing record field", e); }
                if (field.isAnnotationPresent(Ignore.class)) throw new ConfigException("@Ignore is unsupported on record components: " + component.getName());
                field.setAccessible(true);
                result.add(new ModelProperty(key(component, format.apply(component.getName())), Types.resolve(component.getGenericType(), bindings), component, field));
            }
        } else {
            Type parent = raw.getGenericSuperclass();
            if (parent != null && parent != Object.class) result.addAll(properties(Types.resolve(parent, bindings), format));
            for (Field field : raw.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic() || field.isAnnotationPresent(Ignore.class)) continue;
                if (Modifier.isFinal(modifiers)) throw new ConfigException("Final field " + field + ": use a record, @Ignore, or a mutable field");
                field.setAccessible(true);
                result.add(new ModelProperty(key(field, format.apply(field.getName())), Types.resolve(field.getGenericType(), bindings), field, field));
            }
        }
        Set<String> keys = new HashSet<>();
        for (ModelProperty property : result) if (!keys.add(property.key())) throw new ConfigException("Duplicate YAML key '" + property.key() + "' in " + raw.getName());
        return result;
    }

    private static String key(AnnotatedElement element, String fallback) {
        Key annotation = element.getAnnotation(Key.class);
        String key = annotation == null ? fallback : annotation.value();
        checkKey(key);
        return key;
    }
}
