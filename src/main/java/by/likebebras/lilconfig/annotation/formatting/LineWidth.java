package by.likebebras.lilconfig.annotation.formatting;

import java.lang.annotation.*;

/** Preferred document wrap width (at least 20), or 0 to disable wrapping. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface LineWidth { int value(); }
