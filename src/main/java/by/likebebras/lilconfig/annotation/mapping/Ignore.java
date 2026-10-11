package by.likebebras.lilconfig.annotation.mapping;

import java.lang.annotation.*;

/** Excludes a field. Static, transient and synthetic fields are also ignored. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Ignore { }
