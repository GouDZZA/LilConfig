package by.likebebras.lilconfig.internal.mapping;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static by.likebebras.lilconfig.internal.mapping.MappingErrors.error;

/** Strict scalar conversion and YAML shape checks with model path context. */
final class ValueConversions {
    private ValueConversions() { }

    static Object number(Object value, Class<?> raw, String path) {
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

    static Class<?> boxed(Class<?> raw) {
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

    static String string(Object value, String path) {
        if (value instanceof String text) return text;
        throw error(path, "expected a string");
    }

    static List<?> list(Object value, String path) {
        if (value instanceof List<?> list) return list;
        throw error(path, "expected a YAML list");
    }

    static Map<?, ?> mapping(Object value, String path) {
        if (value instanceof Map<?, ?> map) return map;
        throw error(path, "expected a YAML mapping");
    }

}
