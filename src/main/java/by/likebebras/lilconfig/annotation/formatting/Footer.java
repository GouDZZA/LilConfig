package by.likebebras.lilconfig.annotation.formatting;

import java.lang.annotation.*;

/** Document footer. An empty array clears it. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface Footer { String[] value(); }
