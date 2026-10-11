package by.likebebras.lilconfig.annotation.formatting;

import by.likebebras.lilconfig.format.StringFormat;

import java.lang.annotation.*;

/** Style of string values in this subtree; keys and numeric/boolean values retain their types. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Inherited
public @interface StringStyle { StringFormat value(); }
