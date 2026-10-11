package by.likebebras.lilconfig.format;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Naming conventions for model keys. Map keys and explicit @Key names are never converted. */
public enum KeyFormat {
    IDENTITY, KEBAB_CASE, SNAKE_CASE, SCREAMING_SNAKE_CASE,
    CAMEL_CASE, PASCAL_CASE, DOT_CASE, LOWER_CASE, UPPER_CASE;

    public String apply(String name) {
        if (this == IDENTITY) return name;
        String words = name.replaceAll("([\\p{Ll}0-9])(\\p{Lu})", "$1 $2")
                .replaceAll("(\\p{Lu})(\\p{Lu}\\p{Ll})", "$1 $2")
                .replaceAll("[-_.\\s]+", " ").trim().toLowerCase(Locale.ROOT);
        String[] parts = words.split(" +");
        return switch (this) {
            case KEBAB_CASE -> String.join("-", parts);
            case SNAKE_CASE -> String.join("_", parts);
            case SCREAMING_SNAKE_CASE -> String.join("_", parts).toUpperCase(Locale.ROOT);
            case DOT_CASE -> String.join(".", parts);
            case LOWER_CASE -> String.join("", parts);
            case UPPER_CASE -> String.join("", parts).toUpperCase(Locale.ROOT);
            case PASCAL_CASE -> Arrays.stream(parts).map(KeyFormat::capitalize).collect(Collectors.joining());
            case CAMEL_CASE -> parts[0] + Arrays.stream(parts).skip(1).map(KeyFormat::capitalize).collect(Collectors.joining());
            case IDENTITY -> name;
        };
    }

    private static String capitalize(String word) {
        if (word.isEmpty()) return word;
        int first = word.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(first))) + word.substring(Character.charCount(first));
    }
}
