package by.likebebras.lilconfig.annotation.formatting;

import java.lang.annotation.*;

/** Document indentation in spaces (2..9); used on the root model. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface YamlIndent { int value(); }
