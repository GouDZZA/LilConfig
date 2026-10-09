package by.likebebras.lilconfig;

/** IO, schema, conversion or validation failure with file/key context. */
public final class ConfigException extends RuntimeException {
    public ConfigException(String message) { super(message); }
    public ConfigException(String message, Throwable cause) { super(message, cause); }
}
