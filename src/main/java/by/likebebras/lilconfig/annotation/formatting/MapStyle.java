package by.likebebras.lilconfig.annotation.formatting;

import by.likebebras.lilconfig.format.CollectionStyle;

import java.lang.annotation.*;

/** Block or flow presentation for mappings in this subtree. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Inherited
public @interface MapStyle { CollectionStyle value(); }
