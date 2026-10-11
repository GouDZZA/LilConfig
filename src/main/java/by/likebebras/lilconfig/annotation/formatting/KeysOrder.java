package by.likebebras.lilconfig.annotation.formatting;

import by.likebebras.lilconfig.format.KeyOrder;

import java.lang.annotation.*;

/** Ordering of mapping entries. @Order takes priority for model fields. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Inherited
public @interface KeysOrder { KeyOrder value(); }
