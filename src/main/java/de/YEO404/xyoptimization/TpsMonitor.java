package de.YEO404.xyoptimization;

import org.bukkit.Bukkit;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Measures server TPS and MSPT by timing Bukkit ticks.
 */
public class TpsMonitor {

    private static final int SAMPLE_SIZE = 100; // ticks to average

    private final XYOptimization plugin;
    private final Deque<Long> samples = new ArrayDeque<>();

    private long lastNano   = 0;
    private long ticks      = 0;
    private double currentTps  = 20.0;
    private double trendTps    = 20.0;
    private double mspt        = 0.0;

    public TpsMonitor(XYOptimization plugin) {
        this.plugin = plugin;
    }

    public void start() {
        lastNano = System.nanoTime();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        long now  = System.nanoTime();
        long diff = now - lastNano;
        lastNano  = now;

        samples.addLast(diff);
        if (samples.size() > SAMPLE_SIZE) samples.pollFirst();

        ticks++;
        mspt = diff / 1_000_000.0;

        // average over samples
        double avg = samples.stream().mapToLong(Long::longValue).average().orElse(50_000_000L);
        currentTps = Math.min(20.0, 1_000_000_000.0 / avg);

        // trend over last 20 samples
        if (samples.size() >= 20) {
            long sum = 0;
            int  n   = 0;
            for (long s : samples) { sum += s; n++; if (n == 20) break; }
            trendTps = Math.min(20.0, 1_000_000_000.0 / (sum / 20.0));
        } else {
            trendTps = currentTps;
        }
    }

    public double current() { return currentTps; }
    public double trend()   { return trendTps;   }
    public double mspt()    { return mspt;        }
}
