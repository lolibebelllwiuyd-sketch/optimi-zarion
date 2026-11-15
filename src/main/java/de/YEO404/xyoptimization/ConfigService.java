package de.YEO404.xyoptimization;

import java.util.*;

/**
 * Registry of every config entry that the GUI menus can display and edit.
 * Each Entry describes one setting: its display label, yml path, and type.
 */
public class ConfigService {

    public enum Type { BOOL, INT, DOUBLE }

    public record Entry(String section, String label, String ymlPath,
                        Type type, String description) {}

    private final XYOptimization      plugin;
    private final Map<String, Entry>  entries = new LinkedHashMap<>();

    public ConfigService(XYOptimization plugin) {
        this.plugin = plugin;
        register();
    }

    private void register() {
        // ── redstone ─────────────────────────────────────────────────────────
        add("redstone", "Redstone limiter",          "limiter.enabled",                   Type.BOOL,   "Enable the per-chunk update rate limiter");
        add("redstone", "Limiter: updates/sec/chunk","limiter.max-updates-per-second",     Type.INT,    "Max redstone updates per second per chunk");
        add("redstone", "Stop pistons when disabled","cancel-pistons-when-disabled",       Type.BOOL,   "Cancel piston events in disabled worlds");

        // ── cleanup ───────────────────────────────────────────────────────────
        add("cleanup",  "Auto clear",                "auto-clear.enabled",                Type.BOOL,   "Enable automatic entity cleanup");
        add("cleanup",  "Minutes between clears",    "auto-clear.interval-minutes",       Type.INT,    "How often to run cleanup (minutes)");
        add("cleanup",  "Clear items",               "auto-clear.items",                  Type.BOOL,   "Remove dropped items");
        add("cleanup",  "Clear mobs",                "auto-clear.mobs",                   Type.BOOL,   "Remove hostile mobs (emergency only)");
        add("cleanup",  "Clear arrows",              "auto-clear.projectiles",            Type.BOOL,   "Remove projectiles");
        add("cleanup",  "Clear xp orbs",             "auto-clear.xp",                     Type.BOOL,   "Remove experience orbs");
        add("cleanup",  "Emergency cleanup",         "emergency.enabled",                 Type.BOOL,   "Trigger cleanup when TPS drops below threshold");
        add("cleanup",  "Emergency below TPS",       "emergency.tps",                     Type.DOUBLE, "TPS threshold for emergency cleanup");
    }

    private void add(String section, String label, String path, Type type, String desc) {
        entries.put(path, new Entry(section, label, path, type, desc));
    }

    /** All entries, in insertion order. */
    public Collection<Entry> all() { return entries.values(); }

    /** Entries for one section (e.g. "redstone", "cleanup"). */
    public List<Entry> forSection(String section) {
        return entries.values().stream()
                .filter(e -> e.section().equals(section))
                .toList();
    }

    /** Get the current value of an entry as a String for display. */
    public String getValue(Entry e) {
        return switch (e.type()) {
            case BOOL   -> String.valueOf(plugin.optFile.cfg().getBoolean(e.ymlPath(), false));
            case INT    -> String.valueOf(plugin.optFile.cfg().getInt    (e.ymlPath(), 0));
            case DOUBLE -> String.valueOf(plugin.optFile.cfg().getDouble (e.ymlPath(), 0.0));
        };
    }

    /** Toggle a BOOL entry. */
    public void toggle(Entry e) {
        if (e.type() != Type.BOOL) return;
        boolean cur = plugin.optFile.cfg().getBoolean(e.ymlPath(), false);
        plugin.optFile.cfg().set(e.ymlPath(), !cur);
        plugin.optFile.save();
        plugin.reload();
    }
}
