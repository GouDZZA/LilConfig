package by.likebebras.lilconfig.annotation.formatting;

import by.likebebras.lilconfig.format.Newline;

import java.lang.annotation.*;

/** Document line ending; used on the root model. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface LineBreak { Newline value(); }
