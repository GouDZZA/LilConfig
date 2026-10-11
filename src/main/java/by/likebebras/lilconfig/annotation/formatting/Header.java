package by.likebebras.lilconfig.annotation.formatting;

import java.lang.annotation.*;

/** Document header; overrides the root @Comment. An empty array clears it. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface Header { String[] value(); }
