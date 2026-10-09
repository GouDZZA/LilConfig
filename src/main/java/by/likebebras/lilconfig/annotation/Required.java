package by.likebebras.lilconfig.annotation;
import java.lang.annotation.*;

/** Requires a non-null value after applying defaults. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Required { }
