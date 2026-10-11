package by.likebebras.lilconfig.annotation.mapping;

import by.likebebras.lilconfig.format.KeyFormat;

import java.lang.annotation.*;

/** Naming convention for model properties; explicit @Key always wins. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface KeysFormat { KeyFormat value(); }
