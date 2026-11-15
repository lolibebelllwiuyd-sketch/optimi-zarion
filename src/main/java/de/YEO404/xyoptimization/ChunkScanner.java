package de.YEO404.xyoptimization;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.*;

/**
 * Scans loaded chunks for entity counts and other statistics.
 */
public class ChunkScanner {

    private final XYOptimization plugin;

    public ChunkScanner(XYOptimization plugin) {
        this.plugin = plugin;
    }

    /**
     * Scan all loaded chunks in the given world and return
     * the top results sorted by entity count descending.
     */
    public List<ChunkResult> scan(World world, int topN) {
        List<ChunkResult> results = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            Entity[] entities = chunk.getEntities();
            if (entities.length == 0) continue;

            // count by type
            Map<String, Integer> byType = new LinkedHashMap<>();
            for (Entity e : entities)
                byType.merge(e.getType().name(), 1, Integer::sum);

            results.add(new ChunkResult(
                    world.getName(),
                    chunk.getX(),
                    chunk.getZ(),
                    entities.length,
                    byType
            ));
        }

        results.sort(Comparator.comparingInt(ChunkResult::entityCount).reversed());
        return results.subList(0, Math.min(topN, results.size()));
    }

    // ── inner type ───────────────────────────────────────────────────────────

    record ChunkResult(String world, int cx, int cz,
                       int entityCount, Map<String, Integer> byType) {

        /** Block coordinates of the chunk corner. */
        int bx() { return cx * 16; }
        int bz() { return cz * 16; }

        @Override public String toString() {
            return world + " (" + bx() + "," + bz() + ") → " + entityCount + " entities";
        }
    }

    /** Visitor interface for custom chunk scanning logic. */
    @FunctionalInterface
    interface Visitor {
        void visit(ChunkResult result);
    }
}
