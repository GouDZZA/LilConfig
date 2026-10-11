package by.likebebras.lilconfig.annotation.formatting;

import java.lang.annotation.*;

/** Minimum blank lines after this property (0..10). */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface SpaceAfter { int value(); }
