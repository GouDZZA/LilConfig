package by.likebebras.lilconfig.annotation.formatting;

import java.lang.annotation.*;

/** Blank lines between mapping entries (0..10); inherited by nested mappings. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Inherited
public @interface KeysSpacing { int value(); }
