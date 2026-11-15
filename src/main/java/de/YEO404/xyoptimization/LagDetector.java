package de.YEO404.xyoptimization;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.time.Instant;
import java.util.*;

/**
 * Detects lag machines by counting redstone/piston/dispenser/hopper
 * events per chunk per second.
 */
public class LagDetector implements Listener {

    // ── config ───────────────────────────────────────────────────────────────
    private boolean enabled       = true;
    private boolean entityEnabled = false;
    private boolean notifyPlayers = true;
    private boolean notifyConsole = true;
    private boolean notifySound   = true;
    private boolean autoFreeze    = false;
    private int     window        = 5;   // seconds
    private int     thrRed        = 300; // redstone/sec
    private int     thrPis        = 60;
    private int     thrDis        = 60;
    private int     thrHop        = 200;
    private int     entThr        = 50;  // entities in chunk
    private int     cooldown      = 30;  // seconds
    private int     historySize   = 50;
    private int     freezeSeconds = 30;

    // ── state ────────────────────────────────────────────────────────────────
    private final XYOptimization plugin;
    // chunk key → counter
    private final Map<String, Counter> counters = new HashMap<>();
    // incident history
    private final ArrayDeque<Incident> history  = new ArrayDeque<>();
    // last alert time per chunk
    private final Map<String, Long>    lastAlert = new HashMap<>();
    private long nextId = 1;

    public LagDetector(XYOptimization plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // tick every second
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void reload() {
        var cfg = plugin.lagFile.cfg();
        enabled       = cfg.getBoolean("enabled",           true);
        entityEnabled = cfg.getBoolean("entity-overload.enabled", false);
        notifyPlayers = cfg.getBoolean("notify.players",    true);
        notifyConsole = cfg.getBoolean("notify.console",    true);
        notifySound   = cfg.getBoolean("notify.sound",      true);
        autoFreeze    = cfg.getBoolean("auto-action.freeze", false);
        window        = cfg.getInt("window-seconds",        5);
        thrRed        = cfg.getInt("thresholds-per-second.redstone",  300);
        thrPis        = cfg.getInt("thresholds-per-second.piston",     60);
        thrDis        = cfg.getInt("thresholds-per-second.dispenser",  60);
        thrHop        = cfg.getInt("thresholds-per-second.hopper",    200);
        entThr        = cfg.getInt("entity-overload.threshold",        50);
        cooldown      = cfg.getInt("alert-cooldown-seconds",           30);
        historySize   = cfg.getInt("history-size",                     50);
        freezeSeconds = cfg.getInt("auto-action.freeze-seconds",       30);
    }

    // ── event counting ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onRedstone(BlockRedstoneEvent e) {
        if (!enabled) return;
        get(e.getBlock().getWorld().getName(),
                e.getBlock().getChunk().getX(),
                e.getBlock().getChunk().getZ()).redstone++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPiston(BlockPistonExtendEvent e) {
        if (!enabled) return;
        get(e.getBlock().getWorld().getName(),
                e.getBlock().getChunk().getX(),
                e.getBlock().getChunk().getZ()).pistons++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDispense(BlockDispenseEvent e) {
        if (!enabled) return;
        get(e.getBlock().getWorld().getName(),
                e.getBlock().getChunk().getX(),
                e.getBlock().getChunk().getZ()).dispensers++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onHopper(InventoryMoveItemEvent e) {
        if (!enabled) return;
        var loc = e.getSource().getLocation();
        if (loc == null) return;
        get(loc.getWorld().getName(),
                loc.getChunk().getX(),
                loc.getChunk().getZ()).hoppers++;
    }

    // ── per-second tick ──────────────────────────────────────────────────────

    private void tick() {
        if (!enabled) return;
        for (var entry : counters.entrySet()) {
            Counter c = entry.getValue();
            check(entry.getKey(), c);
            c.reset();
        }
        counters.clear();
    }

    private void check(String chunkKey, Counter c) {
        String type   = null;
        String detail = null;

        if      (c.redstone  >= thrRed) { type = "REDSTONE";  detail = c.redstone  + "/s"; }
        else if (c.pistons   >= thrPis) { type = "PISTON";    detail = c.pistons   + "/s"; }
        else if (c.dispensers>= thrDis) { type = "DISPENSER"; detail = c.dispensers+ "/s"; }
        else if (c.hoppers   >= thrHop) { type = "HOPPER";    detail = c.hoppers   + "/s"; }

        if (type == null) return;

        // cooldown
        long now = System.currentTimeMillis();
        long last = lastAlert.getOrDefault(chunkKey, 0L);
        if (now - last < cooldown * 1000L) return;
        lastAlert.put(chunkKey, now);

        // parse chunk key → "world:cx:cz"
        String[] parts = chunkKey.split(":");
        String world = parts[0];
        int cx = Integer.parseInt(parts[1]);
        int cz = Integer.parseInt(parts[2]);
        int bx = cx * 16;
        int bz = cz * 16;

        addIncident(nextId++, Instant.now(), type, world, bx, 64, bz, detail);

        String msg = "&c[LAG MACHINE DETECTED] &f" + type + " at &e" + world
                + " &f(" + bx + ", " + bz + ") &7→ " + detail;

        if (notifyConsole)
            plugin.getLogger().warning(plugin.msg.strip(msg));

        if (notifyPlayers) {
            for (var p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("xyopti.alerts"))
                    plugin.msg.plainP(p, msg);
                if (notifySound)
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
            }
        }

        if (autoFreeze) {
            var w = Bukkit.getWorld(world);
            if (w != null) plugin.redstone.freezeChunk(w, cx, cz, freezeSeconds);
        }
    }

    private void addIncident(long id, Instant time, String type,
                              String world, int x, int y, int z, String detail) {
        history.addLast(new Incident(id, time, type, world, x, y, z, detail));
        while (history.size() > historySize) history.pollFirst();
    }

    // ── public API ───────────────────────────────────────────────────────────

    public List<Incident> getHistory()     { return new ArrayList<>(history); }
    public void           clearHistory()   { history.clear(); }
    public boolean        isEnabled()      { return enabled; }
    public void           setEnabled(boolean v) { enabled = v; }

    private Counter get(String world, int cx, int cz) {
        return counters.computeIfAbsent(world + ":" + cx + ":" + cz, k -> new Counter());
    }

    // ── inner types ──────────────────────────────────────────────────────────

    static class Counter {
        int redstone, pistons, dispensers, hoppers;
        void reset() { redstone = pistons = dispensers = hoppers = 0; }
    }

    record Incident(long id, Instant time, String type,
                    String world, int x, int y, int z, String detail) {
        @Override public String toString() {
            return "[#" + id + " " + time + "] " + type + " @ " + world
                    + " (" + x + "," + z + ") " + detail;
        }
    }
}
