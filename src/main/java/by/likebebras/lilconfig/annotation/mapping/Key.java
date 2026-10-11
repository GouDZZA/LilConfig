package by.likebebras.lilconfig.annotation.mapping;

import java.lang.annotation.*;

/** Literal YAML key, not a dotted path. Defaults to the Java field name. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Key { String value(); }
