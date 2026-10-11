package by.likebebras.lilconfig.internal.mapping;

import by.likebebras.lilconfig.annotation.validation.Range;
import by.likebebras.lilconfig.annotation.validation.Required;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Optional;

import static by.likebebras.lilconfig.internal.mapping.MappingErrors.error;

/** A resolved field or record component and its value constraints. */
record ModelProperty(String key, Type type, AnnotatedElement annotations, Field field) {
    Object get(Object object) throws IllegalAccessException { return field.get(object); }
    void set(Object object, Object value) throws IllegalAccessException { field.set(object, value); }

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
