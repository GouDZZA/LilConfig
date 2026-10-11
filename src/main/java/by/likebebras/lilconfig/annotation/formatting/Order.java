package by.likebebras.lilconfig.annotation.formatting;

import java.lang.annotation.*;

/** Sort priority of a model property; smaller values appear first, default is zero. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Order { int value(); }
