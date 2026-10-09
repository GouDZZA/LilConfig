package by.likebebras.lilconfig;

import org.bukkit.plugin.Plugin;
import java.nio.file.Path;
import java.util.Objects;

/** Entry point. No global plugin references are retained. */
public final class LilConfig {
    private LilConfig() {
    }

    public static ConfigProvider provider(Plugin plugin) {
        return provider(Objects.requireNonNull(plugin, "plugin").getDataFolder().toPath());
    }

    /** Useful for tests and tools without a running Bukkit server. */
    public static ConfigProvider provider(Path directory) {
        return new ConfigProvider(directory);
    }
}
