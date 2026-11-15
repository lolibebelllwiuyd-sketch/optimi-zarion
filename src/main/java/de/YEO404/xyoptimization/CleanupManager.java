package de.YEO404.xyoptimization;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;

import java.time.Instant;
import java.util.*;

/**
 * Periodic entity/item cleanup and emergency TPS cleanup.
 */
public class CleanupManager {

    private final XYOptimization plugin;

    private boolean autoEnabled   = true;
    private int     intervalMin   = 10;
    private boolean clearItems    = true;
    private boolean clearMobs     = false;
    private boolean clearProjectiles = true;
    private boolean clearXp       = true;
    private boolean emergEnabled  = true;
    private double  emergTps      = 10.0;

    private Instant lastCleanup   = null;

    public CleanupManager(XYOptimization plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        var cfg = plugin.optFile.cfg();
        autoEnabled      = cfg.getBoolean("auto-clear.enabled",          true);
        intervalMin      = cfg.getInt    ("auto-clear.interval-minutes",  10);
        clearItems       = cfg.getBoolean("auto-clear.items",             true);
        clearMobs        = cfg.getBoolean("auto-clear.mobs",              false);
        clearProjectiles = cfg.getBoolean("auto-clear.projectiles",       true);
        clearXp          = cfg.getBoolean("auto-clear.xp",                true);
        emergEnabled     = cfg.getBoolean("emergency.enabled",            true);
        emergTps         = cfg.getDouble ("emergency.tps",                10.0);

        // reschedule auto-clear timer
        Bukkit.getScheduler().runTaskTimer(plugin, this::timerTick,
                intervalMin * 60 * 20L, intervalMin * 60 * 20L);
    }

    private void timerTick() {
        if (!autoEnabled) return;
        run(false);
    }

    /** Called every server tick to watch TPS for emergency mode. */
    public void emergencyTick() {
        if (!emergEnabled) return;
        if (plugin.tps.current() < emergTps) run(true);
    }

    /** Run a cleanup sweep. */
    public Result run(boolean emergency) {
        int items = 0, mobs = 0, proj = 0, xp = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if      (clearItems       && e instanceof Item)       { e.remove(); items++; }
                else if (clearMobs        && e instanceof Monster
                                          && emergency)               { e.remove(); mobs++;  }
                else if (clearProjectiles && e instanceof Projectile) { e.remove(); proj++;  }
                else if (clearXp          && e instanceof ExperienceOrb){ e.remove(); xp++;  }
            }
        }
        lastCleanup = Instant.now();
        return new Result(items, mobs, proj, xp);
    }

    public Instant lastCleanup() { return lastCleanup; }

    record Result(int items, int mobs, int projectiles, int xp) {
        int total() { return items + mobs + projectiles + xp; }
    }
}
