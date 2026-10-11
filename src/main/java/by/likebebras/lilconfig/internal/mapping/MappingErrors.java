package by.likebebras.lilconfig.internal.mapping;

import by.likebebras.lilconfig.ConfigException;

import java.lang.reflect.InvocationTargetException;

/** Consistent conversion errors, including the original constructor failure. */
final class MappingErrors {
    private MappingErrors() { }

    static String reason(Throwable e) {
        if (e instanceof InvocationTargetException invocation && invocation.getCause() != null) return reason(invocation.getCause());
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    static ConfigException error(String path, String message) { return new ConfigException(path + ": " + message); }
    static ConfigException error(String path, String message, Throwable cause) { return new ConfigException(path + ": " + message, cause); }
}
