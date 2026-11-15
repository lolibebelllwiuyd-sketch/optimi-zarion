package de.YEO404.xyoptimization;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.util.*;

/**
 * Controls redstone activity per world:
 *  - enable/disable redstone globally or per world
 *  - rate-limiter (max updates/sec per chunk)
 *  - freeze individual chunks
 *  - cancel pistons / dispensers / hoppers when disabled/frozen
 */
public class RedstoneManager implements Listener {

    private final XYOptimization plugin;

    // worlds where redstone is OFF
    private final Set<String>         disabledWorlds = new HashSet<>();
    // worlds where redstone is explicitly ON (overrides global default)
    private final Set<String>         enabledWorlds  = new HashSet<>();
    // frozen chunk keys (world:cx:cz) → unfreeze time (-1 = permanent)
    private final Map<String, Long>   frozen         = new HashMap<>();

    private boolean allOff           = false;
    private boolean cancelPistons    = true;
    private boolean cancelDispensers = true;
    private boolean cancelHoppers    = true;

    // limiter
    private boolean limiterEnabled = false;
    private int     limiterMax     = 100;
    // chunk key → updates this second
    private final Map<String, Integer> limiterCounts = new HashMap<>();

    public RedstoneManager(XYOptimization plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // reset limiter counts every second
        Bukkit.getScheduler().runTaskTimer(plugin, limiterCounts::clear, 20L, 20L);
    }

    public void reload() {
        var cfg = plugin.redFile.cfg();
        allOff           = cfg.getBoolean("redstone.all-off",    false);
        cancelPistons    = cfg.getBoolean("cancel-pistons-when-disabled",    true);
        cancelDispensers = cfg.getBoolean("cancel-dispensers-when-frozen",   true);
        cancelHoppers    = cfg.getBoolean("cancel-hoppers-when-frozen",      true);

        disabledWorlds.clear();
        var dw = cfg.getStringList("redstone.disabled-worlds");
        disabledWorlds.addAll(dw);

        reloadLimiter();
    }

    public void reloadLimiter() {
        var cfg = plugin.redFile.cfg();
        limiterEnabled = cfg.getBoolean("limiter.enabled",             false);
        limiterMax     = cfg.getInt    ("limiter.max-updates-per-second", 100);
    }

    public void save() {
        // persist runtime state (frozen chunks, world toggles) to state.yml
        var state = plugin.stateFile.cfg();
        state.set("redstone.all-off",        allOff);
        state.set("redstone.disabled-worlds", new ArrayList<>(disabledWorlds));
        plugin.stateFile.save();
    }

    // ── event handlers ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent e) {
        if (shouldCancel(e.getBlock().getWorld(), e.getBlock().getChunk().getX(),
                e.getBlock().getChunk().getZ()))
            e.setNewCurrent(e.getOldCurrent());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        if (cancelPistons && shouldCancel(e.getBlock().getWorld(),
                e.getBlock().getChunk().getX(), e.getBlock().getChunk().getZ()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (cancelPistons && shouldCancel(e.getBlock().getWorld(),
                e.getBlock().getChunk().getX(), e.getBlock().getChunk().getZ()))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (!cancelHoppers) return;
        var loc = e.getSource().getLocation();
        if (loc == null) return;
        if (shouldCancel(loc.getWorld(), loc.getChunk().getX(), loc.getChunk().getZ()))
            e.setCancelled(true);
    }

    // ── public API ───────────────────────────────────────────────────────────

    public boolean isWorldEnabled(World w) {
        if (allOff) return enabledWorlds.contains(w.getName());
        return !disabledWorlds.contains(w.getName());
    }

    public void setWorldEnabled(World w, boolean enabled) {
        if (enabled) { disabledWorlds.remove(w.getName()); enabledWorlds.add(w.getName()); }
        else         { disabledWorlds.add(w.getName());    enabledWorlds.remove(w.getName()); }
    }

    public void freezeChunk(World w, int cx, int cz, int seconds) {
        long until = seconds < 0 ? -1L : System.currentTimeMillis() + seconds * 1000L;
        frozen.put(key(w, cx, cz), until);
    }

    public void unfreezeChunk(World w, int cx, int cz) {
        frozen.remove(key(w, cx, cz));
    }

    public boolean isFrozen(World w, int cx, int cz) {
        Long until = frozen.get(key(w, cx, cz));
        if (until == null) return false;
        if (until == -1L) return true;
        if (System.currentTimeMillis() > until) { frozen.remove(key(w, cx, cz)); return false; }
        return true;
    }

    public boolean isAllOff()      { return allOff; }
    public void    setAllOff(boolean v) { allOff = v; }

    // ── internals ────────────────────────────────────────────────────────────

    private boolean shouldCancel(World w, int cx, int cz) {
        if (!isWorldEnabled(w))   return true;
        if (isFrozen(w, cx, cz)) return true;
        if (limiterEnabled) {
            String k = key(w, cx, cz);
            int count = limiterCounts.getOrDefault(k, 0) + 1;
            limiterCounts.put(k, count);
            if (count > limiterMax) return true;
        }
        return false;
    }

    private static String key(World w, int cx, int cz) {
        return w.getName() + ":" + cx + ":" + cz;
    }
}
