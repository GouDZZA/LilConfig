package by.likebebras.lilconfig;

/** Converts a custom type to/from YAML scalars, lists, maps and null. */
public interface ConfigAdapter<T> {
    Object serialize(T value);
    T deserialize(Object value);
}
