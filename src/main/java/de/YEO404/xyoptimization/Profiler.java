package de.YEO404.xyoptimization;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.*;

/**
 * Samples the main thread's stack trace to estimate which plugin
 * is consuming the most tick time.
 */
public class Profiler {

    private final XYOptimization plugin;

    private boolean   enabled      = true;
    private long      sampleMs     = 50L;

    private Thread    mainThread   = null;
    private ScheduledExecutorService executor;

    // plugin name → sample hit count
    private final Map<String, Long>   weights = new HashMap<>();
    // class-loader → plugin name cache
    private final Map<ClassLoader, String> owners = new HashMap<>();
    private final List<Plugin>             loaders = new ArrayList<>();

    private long busy = 0;
    private long idle = 0;

    public Profiler(XYOptimization plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        mainThread = Thread.currentThread();
        refreshLoaders();
        Bukkit.getScheduler().runTaskTimer(plugin,
                this::refreshLoaders, 200L, 200L);     // refresh plugin list every 10s
        startExecutor();
    }

    public void reload() {
        var cfg = plugin.mainFile.cfg();
        enabled  = cfg.getBoolean("profiler.enabled",         true);
        sampleMs = cfg.getLong   ("profiler.sample-interval-ms", 50L);

        if (executor != null) {
            executor.shutdownNow();
            if (enabled) startExecutor();
        }
    }

    public void stop() {
        if (executor != null) executor.shutdownNow();
    }

    private void startExecutor() {
        if (!enabled || mainThread == null) return;
        executor = Executors.newSingleThreadScheduledExecutor(
                r -> { Thread t = new Thread(r, "xy-profiler"); t.setDaemon(true); return t; });
        executor.scheduleAtFixedRate(this::sample, sampleMs, sampleMs,
                TimeUnit.MILLISECONDS);
    }

    private void sample() {
        if (mainThread == null) return;
        StackTraceElement[] stack = mainThread.getStackTrace();
        if (stack.length == 0) { idle++; return; }

        String owner = ownerOf(stack);
        if (owner == null) { idle++; return; }

        busy++;
        weights.merge(owner, 1L, Long::sum);
    }

    private void refreshLoaders() {
        loaders.clear();
        owners.clear();
        loaders.addAll(Arrays.asList(Bukkit.getPluginManager().getPlugins()));
    }

    private String ownerOf(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            try {
                Class<?> cls = Class.forName(frame.getClassName(), false,
                        Thread.currentThread().getContextClassLoader());
                ClassLoader cl = cls.getClassLoader();
                String cached = owners.get(cl);
                if (cached != null) return cached;

                for (Plugin p : loaders) {
                    if (p.getClass().getClassLoader() == cl) {
                        owners.put(cl, p.getName());
                        return p.getName();
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** Returns plugin names sorted by sample weight, highest first. */
    public List<Map.Entry<String, Long>> getTopPlugins(int n) {
        return weights.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(n)
                .toList();
    }

    public long totalBusy() { return busy; }
    public long totalIdle() { return idle; }
    public void clear()     { weights.clear(); busy = idle = 0; }
    public boolean isEnabled() { return enabled; }
}
