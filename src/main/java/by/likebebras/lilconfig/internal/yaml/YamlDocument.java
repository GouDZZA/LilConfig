package by.likebebras.lilconfig.internal.yaml;

import by.likebebras.lilconfig.annotation.formatting.Comment;
import by.likebebras.lilconfig.annotation.formatting.Footer;
import by.likebebras.lilconfig.annotation.formatting.Header;
import by.likebebras.lilconfig.internal.tree.EncodedConfig;
import by.likebebras.lilconfig.internal.tree.YamlPaths;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bukkit YAML backend with strict loading, literal keys and atomic file replacement. */
public final class YamlDocument {
    private final YamlConfiguration yaml;
    private EncodedConfig encoded;
    private Class<?> model;

    private YamlDocument(YamlConfiguration yaml) { this.yaml = yaml; }

    public static YamlDocument read(Path file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator(YamlPaths.SEPARATOR);
        yaml.options().parseComments(true);
        if (Files.exists(file)) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            // Check duplicates and cycles before Bukkit expands mappings into sections.
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setNestingDepthLimit(128);
            Object parsed = new Yaml(new SafeConstructor(options)).load(content);
            YamlTree.validate(parsed);
            yaml.loadFromString(content);
        }
        return new YamlDocument(yaml);
    }

    public Map<String, Object> values() {
        return YamlTree.values(yaml);
    }

    public void apply(EncodedConfig encoded, Class<?> model) {
        this.encoded = encoded;
        this.model = model;
        merge(encoded.values(), "", encoded.objects());
        encoded.comments().forEach(yaml::setComments);
        Comment header = model.getAnnotation(Comment.class);
        if (header != null) yaml.options().setHeader(List.of(header.value()));
        Header explicitHeader = model.getAnnotation(Header.class);
        if (explicitHeader != null) yaml.options().setHeader(List.of(explicitHeader.value()));
        Footer footer = model.getAnnotation(Footer.class);
        if (footer != null) yaml.options().setFooter(List.of(footer.value()));
    }

    private void merge(Map<?, ?> values, String parent, Set<String> objects) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = entry.getKey().toString();
            String path = parent.isEmpty() ? key : parent + YamlPaths.SEPARATOR + key;
            if (objects.contains(path) && entry.getValue() instanceof Map<?, ?> children) {
                if (!yaml.isConfigurationSection(path)) yaml.createSection(path);
                merge(children, path, objects);
            } else {
                // Replacing collections/maps also removes deleted entries.
                yaml.set(path, entry.getValue());
            }
        }
    }

    public void save(Path file) throws IOException {
        String content = encoded == null ? yaml.saveToString() : YamlFormatter.render(yaml, encoded, model);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".lilconfig-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

}
