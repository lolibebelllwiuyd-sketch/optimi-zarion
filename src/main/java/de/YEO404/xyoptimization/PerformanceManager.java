package de.YEO404.xyoptimization;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-world view/simulation distance.
 * Optional adaptive mode: lowers distances when TPS is low.
 */
public class PerformanceManager {

    private final XYOptimization plugin;

    // baseline distances (what the admin configured)
    private final Map<UUID, Integer> baselineView = new HashMap<>();
    private final Map<UUID, Integer> baselineSim  = new HashMap<>();

    private boolean adaptiveEnabled = false;
    private double  lowTps          = 15.0;
    private double  highTps         = 18.5;
    private int     viewDistance    = 10;
    private int     simDistance     = 8;

    private boolean adaptiveReduced = false;

    public PerformanceManager(XYOptimization plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        // check TPS every 5 seconds (100 ticks)
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    public void reload() {
        var cfg = plugin.optFile.cfg();
        adaptiveEnabled = cfg.getBoolean("performance.adaptive.enabled", false);
        viewDistance    = cfg.getInt    ("performance.view-distance",   10);
        simDistance     = cfg.getInt    ("performance.simulation-distance", 8);
        lowTps          = cfg.getDouble ("performance.adaptive.low-tps",  15.0);
        highTps         = cfg.getDouble ("performance.adaptive.high-tps", 18.5);
        apply();
    }

    private void tick() {
        if (!adaptiveEnabled) return;
        double tps = plugin.tps.current();

        if (!adaptiveReduced && tps < lowTps) {
            // reduce distances
            for (World w : Bukkit.getWorlds()) {
                baselineView.computeIfAbsent(w.getUID(), k -> w.getViewDistance());
                baselineSim .computeIfAbsent(w.getUID(), k -> w.getSimulationDistance());
                w.setViewDistance      (Math.max(2, viewDistance - 4));
                w.setSimulationDistance(Math.max(2, simDistance  - 4));
            }
            adaptiveReduced = true;
            plugin.getLogger().info("[XY-OPT] Adaptive: reduced view/sim distance (TPS " + String.format("%.1f", tps) + ")");

        } else if (adaptiveReduced && tps >= highTps) {
            restore();
            plugin.getLogger().info("[XY-OPT] Adaptive: restored view/sim distance (TPS " + String.format("%.1f", tps) + ")");
        }
    }

    private void apply() {
        for (World w : Bukkit.getWorlds()) {
            w.setViewDistance(clamp(viewDistance, 2, 32));
            w.setSimulationDistance(clamp(simDistance, 2, 32));
        }
    }

    /** Restore distances to baseline (called on disable or when TPS recovers). */
    public void restore() {
        for (World w : Bukkit.getWorlds()) {
            int v = baselineView.getOrDefault(w.getUID(), viewDistance);
            int s = baselineSim .getOrDefault(w.getUID(), simDistance);
            w.setViewDistance(v);
            w.setSimulationDistance(s);
        }
        baselineView.clear();
        baselineSim .clear();
        adaptiveReduced = false;
    }

    public int  viewDistance()     { return viewDistance;    }
    public int  simDistance()      { return simDistance;     }
    public boolean isReduced()     { return adaptiveReduced; }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
