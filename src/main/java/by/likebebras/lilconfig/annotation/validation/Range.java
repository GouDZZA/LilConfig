package by.likebebras.lilconfig.annotation.validation;

import java.lang.annotation.*;

/** Inclusive numeric bounds, checked on load and save. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Range {
    double min() default -Double.MAX_VALUE;
    double max() default Double.MAX_VALUE;
}
