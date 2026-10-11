package by.likebebras.lilconfig.annotation.formatting;

import java.lang.annotation.*;

/** Header on the root class, or block comments on a field/record component. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Comment { String[] value(); }
