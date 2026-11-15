package de.YEO404.xyoptimization;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Thin wrapper around a YamlConfiguration that handles
 * saving the default from the JAR, loading, and saving.
 */
public class YmlFile {

    private final JavaPlugin plugin;
    private final String     name;
    private       File       file;
    private       YamlConfiguration cfg;

    public YmlFile(JavaPlugin plugin, String name) {
        this.plugin = plugin;
        this.name   = name;
        this.file   = new File(plugin.getDataFolder(), name);

        // save default from JAR if the file doesn't exist yet
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            if (plugin.getResource(name) != null) {
                plugin.saveResource(name, false);
            } else {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not create " + name + ": " + e.getMessage());
                }
            }
        }

        load();
    }

    /** Reload from disk. */
    public void load() {
        try {
            if (plugin.getResource(name) != null) {
                // merge defaults from JAR
                try (var reader = new InputStreamReader(
                        plugin.getResource(name), StandardCharsets.UTF_8)) {
                    var defaults = YamlConfiguration.loadConfiguration(reader);
                    cfg = YamlConfiguration.loadConfiguration(file);
                    cfg.setDefaults(defaults);
                }
            } else {
                cfg = YamlConfiguration.loadConfiguration(file);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load " + name + ": " + e.getMessage());
            cfg = new YamlConfiguration();
        }
    }

    /** Save current state to disk. */
    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save " + name + ": " + e.getMessage());
        }
    }

    /** The underlying YamlConfiguration. */
    public YamlConfiguration cfg() { return cfg; }
}
