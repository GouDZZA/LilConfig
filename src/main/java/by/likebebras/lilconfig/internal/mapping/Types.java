package by.likebebras.lilconfig.internal.mapping;

import by.likebebras.lilconfig.ConfigException;

import java.lang.reflect.*;
import java.util.*;

/** Generic substitution, including inherited fields and nested type arguments. */
final class Types {
    private Types() { }

    static Class<?> raw(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType p) return (Class<?>) p.getRawType();
        if (type instanceof GenericArrayType a) return Array.newInstance(raw(a.getGenericComponentType()), 0).getClass();
        throw new ConfigException("Unresolved type " + type + "; declare concrete generic arguments");
    }

    static Map<TypeVariable<?>, Type> bindings(Type type) {
        Map<TypeVariable<?>, Type> result = new HashMap<>();
        if (type instanceof ParameterizedType p) {
            TypeVariable<?>[] names = raw(type).getTypeParameters();
            for (int i = 0; i < names.length; i++) result.put(names[i], p.getActualTypeArguments()[i]);
        }
        return result;
    }

    static Type resolve(Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof TypeVariable<?> variable) {
            Type resolved = bindings.get(variable);
            if (resolved == null || resolved.equals(variable)) {
                throw new ConfigException("Unresolved type " + type + "; declare concrete generic arguments");
            }
            return resolve(resolved, bindings);
        }
        if (type instanceof ParameterizedType p) {
            Type[] args = Arrays.stream(p.getActualTypeArguments()).map(t -> resolve(t, bindings)).toArray(Type[]::new);
            return new Parameterized(p.getOwnerType(), p.getRawType(), args);
        }
        if (type instanceof WildcardType w) {
            if (w.getLowerBounds().length != 0) throw new ConfigException("Lower-bounded wildcard is unsupported: " + type);
            return resolve(w.getUpperBounds()[0], bindings);
        }
        if (type instanceof GenericArrayType a) {
            Type component = resolve(a.getGenericComponentType(), bindings);
            if (component instanceof Class<?> c) return Array.newInstance(c, 0).getClass();
            return (GenericArrayType) () -> component;
        }
        return type;
    }

    static Type argument(Type type, Class<?> target, int index) {
        if (raw(type) == target) {
            if (type instanceof ParameterizedType p) return p.getActualTypeArguments()[index];
            throw new ConfigException("Use generic arguments for " + type.getTypeName());
        }
        Map<TypeVariable<?>, Type> bindings = bindings(type);
        for (Type parent : raw(type).getGenericInterfaces()) {
            if (target.isAssignableFrom(raw(parent))) return argument(resolve(parent, bindings), target, index);
        }
        Type parent = raw(type).getGenericSuperclass();
        if (parent != null && target.isAssignableFrom(raw(parent))) {
            return argument(resolve(parent, bindings), target, index);
        }
        throw new ConfigException("Cannot resolve " + target.getSimpleName() + " arguments for " + type);
    }

    private record Parameterized(Type owner, Type raw, Type[] args) implements ParameterizedType {
        @Override public Type[] getActualTypeArguments() { return args.clone(); }
        @Override public Type getRawType() { return raw; }
        @Override public Type getOwnerType() { return owner; }
        @Override public String getTypeName() { return raw.getTypeName() + Arrays.toString(args); }
    }
}
