package by.likebebras.lilconfig.internal;

import by.likebebras.lilconfig.*;
import by.likebebras.lilconfig.annotation.*;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Internal recursive mapper. No Bukkit server is needed for ordinary Java models. */
public final class ConfigMapper {
    public static final char SEPARATOR = '\u001f';
    private static final int MAX_DEPTH = 128;
    private final Map<Class<?>, ConfigAdapter<?>> adapters;

    public ConfigMapper(Map<Class<?>, ConfigAdapter<?>> adapters) { this.adapters = adapters; }

    public static <T> T defaults(Class<T> type) {
        if (type.isRecord()) return null;
        return type.cast(construct(type));
    }

    public <T> T read(Map<String, Object> tree, Class<T> type, T defaults) {
        requireModel(type);
        return type.cast(decode(tree, type, defaults, "$", 0));
    }

    public Encoded write(Object value, Class<?> type) {
        requireModel(type);
        Objects.requireNonNull(value, "config value");
        Encoded result = new Encoded(new LinkedHashMap<>(), new LinkedHashSet<>(), new LinkedHashMap<>());
        Object tree = encode(value, type, "", "$", result, new IdentityHashMap<>(), 0);
        @SuppressWarnings("unchecked") Map<String, Object> root = (Map<String, Object>) tree;
        result.values.putAll(root);
        return result;
    }

    public record Encoded(Map<String, Object> values, Set<String> objects, Map<String, List<String>> comments) { }

    private void requireModel(Class<?> type) {
        if (type.isArray() || type.isPrimitive() || type.isEnum() || type.getName().startsWith("java.")
                || Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)
                || ConfigurationSerializable.class.isAssignableFrom(type) || adapters.containsKey(type)) {
            throw new ConfigException("Root config must be a POJO or record: " + type.getName());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object decode(Object value, Type type, Object fallback, String path, int depth) {
        try {
            depth(depth, path);
            Class<?> raw = Types.raw(type);
            if (raw == Optional.class) {
                Type item = Types.argument(type, Optional.class, 0);
                Object old = fallback instanceof Optional<?> optional ? optional.orElse(null) : null;
                return Optional.ofNullable(decode(value, item, old, path, depth + 1));
            }
            if (value == null) {
                if (raw.isPrimitive()) throw error(path, "null is not allowed for " + raw.getSimpleName());
                return null;
            }
            ConfigAdapter adapter = adapters.get(raw);
            if (adapter != null) {
                Object decoded = adapter.deserialize(value);
                if (decoded != null && !boxed(raw).isInstance(decoded)) throw error(path, "adapter returned the wrong type");
                return decoded;
            }
            if (raw == Object.class) return value;
            if (raw == String.class) {
                if (!(value instanceof String)) throw error(path, "expected a string (quote numeric/boolean text in YAML)");
                return value;
            }
            if (raw == boolean.class || raw == Boolean.class) {
                if (!(value instanceof Boolean)) throw error(path, "expected true or false");
                return value;
            }
            if (raw == char.class || raw == Character.class) {
                if (!(value instanceof String s) || s.length() != 1) throw error(path, "expected one character");
                return s.charAt(0);
            }
            if (raw == BigDecimal.class && value instanceof String text) return new BigDecimal(text);
            if (Number.class.isAssignableFrom(boxed(raw))) return number(value, raw, path);
            if (raw.isEnum()) {
                String name = string(value, path);
                for (Object constant : raw.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equalsIgnoreCase(name)) return constant;
                }
                throw error(path, "unknown " + raw.getSimpleName() + " '" + name + "'");
            }
            if (raw == UUID.class) return UUID.fromString(string(value, path));
            if (raw == Duration.class) return Duration.parse(string(value, path));
            if (raw == Instant.class) return Instant.parse(string(value, path));
            if (raw == URI.class) return URI.create(string(value, path));
            if (ConfigurationSerializable.class.isAssignableFrom(raw)) {
                if (!raw.isInstance(value)) throw error(path, "expected Bukkit serialized " + raw.getSimpleName());
                return value;
            }
            if (raw.isArray()) {
                List<?> list = list(value, path);
                Type item = type instanceof GenericArrayType a ? a.getGenericComponentType() : raw.getComponentType();
                Object array = Array.newInstance(Types.raw(item), list.size());
                for (int i = 0; i < list.size(); i++) Array.set(array, i, decode(list.get(i), item, null, path + "[" + i + "]", depth + 1));
                return array;
            }
            if (Collection.class.isAssignableFrom(raw)) {
                Type item = Types.argument(type, Collection.class, 0);
                Collection result = collection(raw, item);
                List<?> list = list(value, path);
                for (int i = 0; i < list.size(); i++) result.add(decode(list.get(i), item, null, path + "[" + i + "]", depth + 1));
                return result;
            }
            if (Map.class.isAssignableFrom(raw)) {
                Type keyType = Types.argument(type, Map.class, 0);
                Type itemType = Types.argument(type, Map.class, 1);
                Map result = map(raw, keyType);
                for (Map.Entry<?, ?> entry : mapping(value, path).entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    Object decodedKey = decodeKey(key, keyType, path);
                    if (result.containsKey(decodedKey)) throw error(path, "duplicate converted map key '" + key + "'");
                    result.put(decodedKey, decode(entry.getValue(), itemType, null, path + "[" + key + "]", depth + 1));
                }
                return result;
            }
            Map<?, ?> values = mapping(value, path);
            List<Property> properties = properties(type);
            Object baseline = fallback != null ? fallback : raw.isRecord() ? null : construct(raw);
            Object result = raw.isRecord() ? null : construct(raw);
            Object[] args = new Object[properties.size()];
            for (int i = 0; i < properties.size(); i++) {
                Property property = properties.get(i);
                String child = path + "." + property.key;
                Object old = baseline == null ? null : property.get(baseline);
                Object decoded;
                if (values.containsKey(property.key)) {
                    decoded = decode(values.get(property.key), property.type, old, child, depth + 1);
                } else if (baseline != null) {
                    // Clone defaults: a failed reload never mutates a supplier's existing object.
                    Object encoded = encode(old, property.type, null, child, null, new IdentityHashMap<>(), depth + 1);
                    decoded = decode(encoded, property.type, null, child, depth + 1);
                } else {
                    throw error(child, "missing record component; provide defaults with load(Type.class, supplier)");
                }
                property.validate(decoded, child);
                if (raw.isRecord()) args[i] = decoded;
                else property.field.set(result, decoded);
            }
            if (raw.isRecord()) {
                Class<?>[] signature = Arrays.stream(raw.getRecordComponents()).map(RecordComponent::getType).toArray(Class<?>[]::new);
                Constructor<?> constructor = raw.getDeclaredConstructor(signature);
                constructor.setAccessible(true);
                result = constructor.newInstance(args);
            }
            if (result instanceof Validatable validatable) validatable.validate();
            return result;
        } catch (ConfigException e) {
            throw e;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw error(path, "cannot read " + type.getTypeName() + ": " + reason(e), e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object encode(Object value, Type type, String yamlPath, String path, Encoded output,
                          IdentityHashMap<Object, Boolean> active, int depth) {
        try {
            depth(depth, path);
            Class<?> raw = Types.raw(type);
            if (value == null) return null;
            ConfigAdapter adapter = adapters.get(raw);
            if (adapter != null) return yamlValue(adapter.serialize(value), active, path, depth + 1);
            if (raw == Object.class) return yamlValue(value, active, path, depth + 1);
            if (value instanceof String || value instanceof Boolean) return value;
            if (value instanceof Character || value instanceof UUID || value instanceof Duration || value instanceof Instant || value instanceof URI) return value.toString();
            // YAML parsers normally read decimal scalars as doubles. Preserve exact precision.
            if (value instanceof BigDecimal decimal) return decimal.toPlainString();
            if (value instanceof Number number) {
                if ((number instanceof Double || number instanceof Float) && !Double.isFinite(number.doubleValue())) {
                    throw error(path, "non-finite numbers are unsupported");
                }
                return number;
            }
            if (value instanceof Enum<?> e) return e.name();
            if (value instanceof ConfigurationSerializable) return value;
            if (active.put(value, Boolean.TRUE) != null) throw error(path, "cyclic object reference");
            try {
                if (value instanceof Optional<?> optional) {
                    return encode(optional.orElse(null), Types.argument(type, Optional.class, 0), yamlPath, path, output, active, depth + 1);
                }
                if (raw.isArray()) {
                    List<Object> list = new ArrayList<>();
                    Type item = type instanceof GenericArrayType a ? a.getGenericComponentType() : raw.getComponentType();
                    for (int i = 0; i < Array.getLength(value); i++) list.add(encode(Array.get(value, i), item, null, path + "[" + i + "]", output, active, depth + 1));
                    return list;
                }
                if (value instanceof Collection<?> collection) {
                    Type item = Types.argument(type, Collection.class, 0);
                    List<Object> list = new ArrayList<>();
                    int i = 0;
                    for (Object element : collection) list.add(encode(element, item, null, path + "[" + i++ + "]", output, active, depth + 1));
                    return list;
                }
                if (value instanceof Map<?, ?> map) {
                    Type keyType = Types.argument(type, Map.class, 0);
                    Type itemType = Types.argument(type, Map.class, 1);
                    Map<String, Object> result = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        String key = encodeKey(entry.getKey(), keyType, path);
                        if (result.containsKey(key)) throw error(path, "duplicate encoded map key '" + key + "'");
                        result.put(key, encode(entry.getValue(), itemType, null, path + "[" + key + "]", output, active, depth + 1));
                    }
                    return result;
                }
                if (value instanceof Validatable validatable) validatable.validate();
                if (output != null && yamlPath != null) output.objects.add(yamlPath);
                Map<String, Object> result = new LinkedHashMap<>();
                for (Property property : properties(type)) {
                    String child = path + "." + property.key;
                    Object fieldValue = property.get(value);
                    property.validate(fieldValue, child);
                    String childYaml = yamlPath == null ? null : yamlPath.isEmpty() ? property.key : yamlPath + SEPARATOR + property.key;
                    Comment comment = property.annotations.getAnnotation(Comment.class);
                    if (output != null && childYaml != null && comment != null) output.comments.put(childYaml, List.of(comment.value()));
                    result.put(property.key, encode(fieldValue, property.type, childYaml, child, output, active, depth + 1));
                }
                return result;
            } finally {
                active.remove(value);
            }
        } catch (ConfigException e) {
            throw e;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw error(path, "cannot write " + type.getTypeName() + ": " + reason(e), e);
        }
    }

    private Object yamlValue(Object value, IdentityHashMap<Object, Boolean> active, String path, int depth) {
        depth(depth, path);
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Number n) {
            if (!(n instanceof Byte || n instanceof Short || n instanceof Integer || n instanceof Long
                    || n instanceof Float || n instanceof Double || n instanceof BigInteger || n instanceof BigDecimal)) {
                throw error(path, "unsupported YAML number: " + n.getClass().getName());
            }
            if ((n instanceof Float || n instanceof Double) && !Double.isFinite(n.doubleValue())) throw error(path, "non-finite number");
            return n;
        }
        if (active.put(value, Boolean.TRUE) != null) throw error(path, "cyclic YAML value");
        try {
            if (value instanceof List<?> list) {
                List<Object> result = new ArrayList<>();
                for (Object item : list) result.add(yamlValue(item, active, path, depth + 1));
                return result;
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw error(path, "adapter/untyped YAML map keys must be strings");
                    checkKey(key);
                    result.put(key, yamlValue(entry.getValue(), active, path + "[" + key + "]", depth + 1));
                }
                return result;
            }
            throw error(path, "expected YAML scalar, List or Map; register an adapter for " + value.getClass().getName());
        } finally {
            active.remove(value);
        }
    }

    private static List<Property> properties(Type type) {
        Class<?> raw = Types.raw(type);
        if (raw.getName().startsWith("java.") || raw.isInterface()) {
            throw new ConfigException("Unsupported model " + type.getTypeName() + "; register an adapter");
        }
        List<Property> result = new ArrayList<>();
        Map<TypeVariable<?>, Type> bindings = Types.bindings(type);
        if (raw.isRecord()) {
            for (RecordComponent component : raw.getRecordComponents()) {
                Field field;
                try { field = raw.getDeclaredField(component.getName()); }
                catch (NoSuchFieldException e) { throw new ConfigException("Missing record field", e); }
                if (field.isAnnotationPresent(Ignore.class)) throw new ConfigException("@Ignore is unsupported on record components: " + component.getName());
                field.setAccessible(true);
                result.add(new Property(key(component, component.getName()), Types.resolve(component.getGenericType(), bindings), component, field));
            }
        } else {
            Type parent = raw.getGenericSuperclass();
            if (parent != null && parent != Object.class) result.addAll(properties(Types.resolve(parent, bindings)));
            for (Field field : raw.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic() || field.isAnnotationPresent(Ignore.class)) continue;
                if (Modifier.isFinal(modifiers)) throw new ConfigException("Final field " + field + ": use a record, @Ignore, or a mutable field");
                field.setAccessible(true);
                result.add(new Property(key(field, field.getName()), Types.resolve(field.getGenericType(), bindings), field, field));
            }
        }
        Set<String> keys = new HashSet<>();
        for (Property property : result) if (!keys.add(property.key)) throw new ConfigException("Duplicate YAML key '" + property.key + "' in " + raw.getName());
        return result;
    }

    private record Property(String key, Type type, AnnotatedElement annotations, Field field) {
        Object get(Object object) throws IllegalAccessException { return field.get(object); }

        void validate(Object value, String path) {
            if (annotations.isAnnotationPresent(Required.class) && (value == null || value instanceof Optional<?> o && o.isEmpty())) {
                throw error(path, "required value is missing");
            }
            Range range = annotations.getAnnotation(Range.class);
            if (range != null) {
                if (range.min() > range.max() || Double.isNaN(range.min()) || Double.isNaN(range.max())) throw error(path, "invalid @Range bounds");
                if (!(value instanceof Number n)) throw error(path, "@Range requires a numeric value");
                if (!Double.isFinite(n.doubleValue()) || n.doubleValue() < range.min() || n.doubleValue() > range.max()) {
                    throw error(path, "expected a number in [" + range.min() + ", " + range.max() + "]");
                }
            }
        }
    }

    private static Object construct(Class<?> type) {
        try {
            if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers())) throw new ConfigException("Nested config classes must be static: " + type.getName());
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (e instanceof ConfigException config) throw config;
            throw new ConfigException("Cannot construct " + type.getName() + "; a no-arg constructor is required: " + reason(e), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection<?> collection(Class<?> raw, Type item) {
        if (raw == EnumSet.class) return EnumSet.noneOf((Class<? extends Enum>) Types.raw(item));
        if (raw == Set.class) return new LinkedHashSet<>();
        if (raw == SortedSet.class || raw == NavigableSet.class) return new TreeSet<>();
        if (raw == List.class || raw == Collection.class) return new ArrayList<>();
        if (raw == Queue.class || raw == Deque.class) return new ArrayDeque<>();
        return (Collection<?>) construct(raw);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<?, ?> map(Class<?> raw, Type key) {
        if (raw == EnumMap.class) return new EnumMap((Class<? extends Enum>) Types.raw(key));
        if (raw == Map.class) return new LinkedHashMap<>();
        if (raw == SortedMap.class || raw == NavigableMap.class) return new TreeMap<>();
        return (Map<?, ?>) construct(raw);
    }

    private Object decodeKey(String key, Type type, String path) {
        Class<?> raw = Types.raw(type);
        checkKey(key);
        requireKeyType(raw, path);
        Object value = key;
        if (Number.class.isAssignableFrom(boxed(raw))) value = new BigDecimal(key);
        else if (raw == Boolean.class || raw == boolean.class) {
            if (!key.equals("true") && !key.equals("false")) throw error(path, "invalid boolean map key '" + key + "'");
            value = Boolean.valueOf(key);
        }
        return decode(value, type, null, path + "[" + key + "]", 0);
    }

    private String encodeKey(Object key, Type type, String path) {
        if (key == null) throw error(path, "null map keys are unsupported");
        requireKeyType(Types.raw(type), path);
        String value = key instanceof Enum<?> e ? e.name() : key.toString();
        checkKey(value);
        // Also validates finite numeric keys and the declared scalar type.
        decodeKey(value, type, path);
        return value;
    }

    private void requireKeyType(Class<?> raw, String path) {
        if (adapters.containsKey(raw) || !(raw == String.class || raw == Character.class || raw == char.class
                || raw == Boolean.class || raw == boolean.class || raw == UUID.class || raw.isEnum()
                || Number.class.isAssignableFrom(boxed(raw)))) {
            throw error(path, "map keys must be strings, numbers, booleans, characters, UUIDs or enums");
        }
    }

    private static Object number(Object value, Class<?> raw, String path) {
        if (!(value instanceof Number n)) throw error(path, "expected a number");
        try {
            BigDecimal decimal = new BigDecimal(n.toString());
            if (raw == byte.class || raw == Byte.class) return decimal.byteValueExact();
            if (raw == short.class || raw == Short.class) return decimal.shortValueExact();
            if (raw == int.class || raw == Integer.class) return decimal.intValueExact();
            if (raw == long.class || raw == Long.class) return decimal.longValueExact();
            if (raw == BigInteger.class) return decimal.toBigIntegerExact();
            if (raw == BigDecimal.class) return decimal;
            if (raw == float.class || raw == Float.class) {
                float f = decimal.floatValue();
                if (Float.isFinite(f)) return f;
            }
            if (raw == double.class || raw == Double.class) {
                double d = decimal.doubleValue();
                if (Double.isFinite(d)) return d;
            }
        } catch (ArithmeticException | NumberFormatException e) {
            throw error(path, "number '" + value + "' does not fit " + raw.getSimpleName(), e);
        }
        throw error(path, "number does not fit a supported numeric type: " + raw.getName());
    }

    private static Class<?> boxed(Class<?> raw) {
        if (raw == byte.class) return Byte.class;
        if (raw == short.class) return Short.class;
        if (raw == int.class) return Integer.class;
        if (raw == long.class) return Long.class;
        if (raw == float.class) return Float.class;
        if (raw == double.class) return Double.class;
        if (raw == boolean.class) return Boolean.class;
        if (raw == char.class) return Character.class;
        return raw;
    }

    private static String string(Object value, String path) {
        if (value instanceof String text) return text;
        throw error(path, "expected a string");
    }

    private static List<?> list(Object value, String path) {
        if (value instanceof List<?> list) return list;
        throw error(path, "expected a YAML list");
    }

    private static Map<?, ?> mapping(Object value, String path) {
        if (value instanceof Map<?, ?> map) return map;
        throw error(path, "expected a YAML mapping");
    }

    private static String key(AnnotatedElement element, String fallback) {
        Key annotation = element.getAnnotation(Key.class);
        String key = annotation == null ? fallback : annotation.value();
        checkKey(key);
        return key;
    }

    public static void checkKey(String key) {
        if (key.isEmpty() || key.indexOf(SEPARATOR) >= 0 || key.equals("==")) {
            throw new ConfigException("Empty keys, '==' and keys containing U+001F are reserved/unsupported: '" + key + "'");
        }
    }

    private static void depth(int depth, String path) {
        if (depth > MAX_DEPTH) throw error(path, "configuration nesting exceeds " + MAX_DEPTH);
    }

    private static String reason(Throwable e) {
        if (e instanceof InvocationTargetException invocation && invocation.getCause() != null) return reason(invocation.getCause());
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static ConfigException error(String path, String message) { return new ConfigException(path + ": " + message); }
    private static ConfigException error(String path, String message, Throwable cause) { return new ConfigException(path + ": " + message, cause); }
}
