package by.likebebras.lilconfig.internal.mapping;

import by.likebebras.lilconfig.ConfigException;

import java.lang.reflect.*;
import java.util.*;

import static by.likebebras.lilconfig.internal.mapping.MappingErrors.reason;

/** Creates default models and concrete implementations of declared container types. */
public final class ModelFactory {
    private ModelFactory() { }

    public static <T> T defaults(Class<T> type) {
        if (type.isRecord()) return null;
        return type.cast(construct(type));
    }

    static Object construct(Class<?> type) {
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
    static Collection<?> collection(Class<?> raw, Type item) {
        if (raw == EnumSet.class) return EnumSet.noneOf((Class<? extends Enum>) Types.raw(item));
        if (raw == Set.class) return new LinkedHashSet<>();
        if (raw == SortedSet.class || raw == NavigableSet.class) return new TreeSet<>();
        if (raw == List.class || raw == Collection.class) return new ArrayList<>();
        if (raw == Queue.class || raw == Deque.class) return new ArrayDeque<>();
        return (Collection<?>) construct(raw);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Map<?, ?> map(Class<?> raw, Type key) {
        if (raw == EnumMap.class) return new EnumMap((Class<? extends Enum>) Types.raw(key));
        if (raw == Map.class) return new LinkedHashMap<>();
        if (raw == SortedMap.class || raw == NavigableMap.class) return new TreeMap<>();
        return (Map<?, ?>) construct(raw);
    }

    static Object constructRecord(Class<?> type, Object[] values) throws ReflectiveOperationException {
        Class<?>[] signature = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Constructor<?> constructor = type.getDeclaredConstructor(signature);
        constructor.setAccessible(true);
        return constructor.newInstance(values);
    }
}
