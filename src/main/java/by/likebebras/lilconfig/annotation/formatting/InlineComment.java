package by.likebebras.lilconfig.annotation.formatting;

import java.lang.annotation.*;

/** A single-line YAML comment next to the property's value. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface InlineComment { String value(); }
