package de.YEO404.xyoptimization;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Handles all /xyopti (and /xyoptimization, /xyopt) sub-commands.
 */
public class OptiCommand implements CommandExecutor, TabCompleter {

    private static final List<String> COMMANDS = List.of(
            "help", "menu", "discord", "status", "stats",
            "redstone", "clear", "lagmachines", "lagtp",
            "freeze", "unfreeze", "view", "simulation",
            "toggle", "profiler", "config", "reload"
    );

    private final XYOptimization plugin;

    public OptiCommand(XYOptimization plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {
        if (!sender.hasPermission("xyopti.admin")) {
            plugin.msg.send(sender, "no-permission");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";

        try {
            switch (sub) {
                case "help"        -> sendHelp(sender);
                case "menu"        -> openMenu(sender, args);
                case "discord"     -> plugin.msg.plainP(sender, "&bhttps://discord.gg/acx8GFwY4v");
                case "status","stats" -> sendStatus(sender);
                case "redstone"    -> handleRedstone(sender, args);
                case "clear"       -> handleClear(sender);
                case "lagmachines" -> sendLagHistory(sender);
                case "lagtp"       -> handleLagTp(sender, args);
                case "freeze"      -> handleFreeze(sender, args, true);
                case "unfreeze"    -> handleFreeze(sender, args, false);
                case "view"        -> handleView(sender, args);
                case "simulation"  -> handleSim(sender, args);
                case "toggle"      -> handleToggle(sender, args);
                case "profiler"    -> handleProfiler(sender, args);
                case "config"      -> handleConfig(sender, args);
                case "reload"      -> handleReload(sender);
                default            -> plugin.msg.plainP(sender, "&cUnknown sub-command. /xyopti help");
            }
        } catch (Throwable t) {
            plugin.msg.plainP(sender, "&cError: " + t.getMessage());
            plugin.getLogger().warning("Command error: " + t.getMessage());
        }
        return true;
    }

    // ── sub-commands ─────────────────────────────────────────────────────────

    private void sendHelp(CommandSender s) {
        plugin.msg.plainP(s, "&8&m--------- &bXY-OPT Help &8&m---------");
        plugin.msg.plainP(s, "&7/xyopti status        &f- TPS, MSPT, entities");
        plugin.msg.plainP(s, "&7/xyopti clear         &f- Run entity cleanup now");
        plugin.msg.plainP(s, "&7/xyopti lagmachines   &f- Show lag machine history");
        plugin.msg.plainP(s, "&7/xyopti lagtp <id>    &f- Teleport to lag machine");
        plugin.msg.plainP(s, "&7/xyopti freeze <w> <x> <z> [sec] &f- Freeze chunk");
        plugin.msg.plainP(s, "&7/xyopti unfreeze <w> <x> <z>     &f- Unfreeze chunk");
        plugin.msg.plainP(s, "&7/xyopti redstone toggle           &f- Toggle all redstone");
        plugin.msg.plainP(s, "&7/xyopti profiler [clear]          &f- Profiler results");
        plugin.msg.plainP(s, "&7/xyopti reload        &f- Reload config");
        plugin.msg.plainP(s, "&7/opti                 &f- Open GUI menus");
        plugin.msg.plainP(s, "&8&m-----------------------------------");
    }

    private void sendStatus(CommandSender s) {
        double tps  = plugin.tps.current();
        double mspt = plugin.tps.mspt();
        String tpsColor = tps >= 18 ? "&a" : tps >= 14 ? "&e" : "&c";

        plugin.msg.plainP(s, "&8&m-- &bServer Status &8&m-----------");
        plugin.msg.plainP(s, " TPS: " + tpsColor + String.format("%.1f", tps)
                + "  &7MSPT: &f" + String.format("%.1f", mspt));
        plugin.msg.plainP(s, " View dist: &f" + plugin.perf.viewDistance()
                + "  &7Sim dist: &f" + plugin.perf.simDistance());
        plugin.msg.plainP(s, " Lag machines detected: &f"
                + plugin.lagDetector.getHistory().size());
        plugin.msg.plainP(s, " Redstone global: "
                + (plugin.redstone.isAllOff() ? "&cOFF" : "&aON"));
        plugin.msg.plainP(s, "&8&m------------------------------");
    }

    private void openMenu(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            plugin.msg.plainP(s, "&c/opti is for players only. Use /xyopti in console.");
            return;
        }
        String page = args.length > 1 ? args[1] : "main";
        Ui.Open result = plugin.ui.open(p, page);
        if (result == Ui.Open.UNKNOWN_PAGE)
            plugin.msg.plainP(s, "&cUnknown page. Pages: "
                    + String.join(", ", plugin.ui.getTopics()));
    }

    private void handleRedstone(CommandSender s, String[] args) {
        if (args.length < 2) { plugin.msg.plainP(s, "&cUsage: /xyopti redstone toggle"); return; }
        if (args[1].equalsIgnoreCase("toggle")) {
            plugin.redstone.setAllOff(!plugin.redstone.isAllOff());
            plugin.msg.plainP(s, "Redstone global: "
                    + (plugin.redstone.isAllOff() ? "&cOFF" : "&aON"));
        }
    }

    private void handleClear(CommandSender s) {
        var result = plugin.cleanup.run(false);
        plugin.msg.plainP(s, "&aCleanup done: &f" + result.total()
                + " entities removed (items=" + result.items()
                + " proj=" + result.projectiles()
                + " xp=" + result.xp() + ")");
    }

    private void sendLagHistory(CommandSender s) {
        var history = plugin.lagDetector.getHistory();
        if (history.isEmpty()) { plugin.msg.plainP(s, "&7No lag machines detected."); return; }
        plugin.msg.plainP(s, "&8&m-- &cLag Machine History &8&m------");
        for (var inc : history)
            plugin.msg.plainP(s, " &7" + inc);
        plugin.msg.plainP(s, "&8&m------------------------------");
    }

    private void handleLagTp(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { plugin.msg.plainP(s, "&cPlayers only."); return; }
        if (args.length < 2) { plugin.msg.plainP(s, "&cUsage: /xyopti lagtp <id>"); return; }
        try {
            long id = Long.parseLong(args[1]);
            plugin.lagDetector.getHistory().stream()
                    .filter(i -> i.id() == id).findFirst()
                    .ifPresentOrElse(i -> {
                        World w = Bukkit.getWorld(i.world());
                        if (w == null) { plugin.msg.plainP(p, "&cWorld not found."); return; }
                        p.teleport(w.getBlockAt(i.x(), i.y(), i.z()).getLocation().add(0.5, 0, 0.5));
                        plugin.msg.plainP(p, "&aTeleported to lag machine #" + id);
                    }, () -> plugin.msg.plainP(p, "&cLag machine #" + id + " not found."));
        } catch (NumberFormatException e) {
            plugin.msg.plainP(s, "&cInvalid ID.");
        }
    }

    private void handleFreeze(CommandSender s, String[] args, boolean freeze) {
        if (args.length < 4) {
            plugin.msg.plainP(s, "&cUsage: /xyopti " + (freeze ? "freeze" : "unfreeze")
                    + " <world> <x> <z> [seconds]");
            return;
        }
        World w = Bukkit.getWorld(args[1]);
        if (w == null) { plugin.msg.plainP(s, "&cWorld not found."); return; }
        try {
            int cx = Integer.parseInt(args[2]) >> 4;
            int cz = Integer.parseInt(args[3]) >> 4;
            if (freeze) {
                int sec = args.length > 4 ? Integer.parseInt(args[4]) : -1;
                plugin.redstone.freezeChunk(w, cx, cz, sec);
                plugin.msg.plainP(s, "&aFroze chunk (" + cx + "," + cz + ")"
                        + (sec < 0 ? " permanently." : " for " + sec + "s."));
            } else {
                plugin.redstone.unfreezeChunk(w, cx, cz);
                plugin.msg.plainP(s, "&aUnfroze chunk (" + cx + "," + cz + ").");
            }
        } catch (NumberFormatException e) {
            plugin.msg.plainP(s, "&cInvalid coordinates.");
        }
    }

    private void handleView(CommandSender s, String[] args) {
        if (args.length < 2) { plugin.msg.plainP(s, "&cUsage: /xyopti view <distance>"); return; }
        try {
            int dist = Integer.parseInt(args[1]);
            Bukkit.getWorlds().forEach(w -> w.setViewDistance(dist));
            plugin.msg.plainP(s, "&aView distance set to &f" + dist + "&a for all worlds.");
        } catch (NumberFormatException e) {
            plugin.msg.plainP(s, "&cInvalid distance.");
        }
    }

    private void handleSim(CommandSender s, String[] args) {
        if (args.length < 2) { plugin.msg.plainP(s, "&cUsage: /xyopti simulation <distance>"); return; }
        try {
            int dist = Integer.parseInt(args[1]);
            Bukkit.getWorlds().forEach(w -> w.setSimulationDistance(dist));
            plugin.msg.plainP(s, "&aSimulation distance set to &f" + dist + "&a for all worlds.");
        } catch (NumberFormatException e) {
            plugin.msg.plainP(s, "&cInvalid distance.");
        }
    }

    private void handleToggle(CommandSender s, String[] args) {
        if (args.length < 2) { plugin.msg.plainP(s, "&cUsage: /xyopti toggle <config-path>"); return; }
        var entry = plugin.configService.all().stream()
                .filter(e -> e.ymlPath().equals(args[1])).findFirst();
        entry.ifPresentOrElse(e -> {
            plugin.configService.toggle(e);
            plugin.msg.plainP(s, "&aToggled &f" + e.label()
                    + " &a→ " + plugin.configService.getValue(e));
        }, () -> plugin.msg.plainP(s, "&cConfig key not found."));
    }

    private void handleProfiler(CommandSender s, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
            plugin.profiler.clear();
            plugin.msg.plainP(s, "&aProfiler data cleared.");
            return;
        }
        var top = plugin.profiler.getTopPlugins(10);
        if (top.isEmpty()) { plugin.msg.plainP(s, "&7No profiler data yet."); return; }
        plugin.msg.plainP(s, "&8&m-- &bProfiler &8&m--------------");
        long total = plugin.profiler.totalBusy() + plugin.profiler.totalIdle();
        for (var entry : top) {
            double pct = total == 0 ? 0 : entry.getValue() * 100.0 / total;
            plugin.msg.plainP(s, " &f" + entry.getKey()
                    + " &7→ &e" + String.format("%.1f", pct) + "%");
        }
        plugin.msg.plainP(s, "&8&m------------------------------");
    }

    private void handleConfig(CommandSender s, String[] args) {
        plugin.msg.plainP(s, "&8&m-- &bConfig Entries &8&m---------");
        for (var e : plugin.configService.all())
            plugin.msg.plainP(s, " &7" + e.ymlPath() + " &f= "
                    + plugin.configService.getValue(e) + " &8(" + e.label() + ")");
        plugin.msg.plainP(s, "&8&m------------------------------");
    }

    private void handleReload(CommandSender s) {
        plugin.reload();
        plugin.msg.plainP(s, "&aConfiguration reloaded.");
    }

    // ── tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1)
            return COMMANDS.stream().filter(c -> c.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("menu"))
            return plugin.ui.getTopics();
        return Collections.emptyList();
    }
}
