package by.likebebras.lilconfig.annotation.formatting;

import java.lang.annotation.*;

/** Minimum blank lines before this property's comments/key (0..10). */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface SpaceBefore { int value(); }
