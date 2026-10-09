package by.likebebras.lilconfig.annotation;
import java.lang.annotation.*;

/** File name relative to the provider directory. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigFile { String value(); }
