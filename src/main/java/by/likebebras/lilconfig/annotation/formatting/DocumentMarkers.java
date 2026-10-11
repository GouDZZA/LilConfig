package by.likebebras.lilconfig.annotation.formatting;

import java.lang.annotation.*;

/** Emit YAML document start (---) and/or end (...) markers. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface DocumentMarkers { boolean start() default true; boolean end() default false; }
