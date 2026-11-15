package de.YEO404.xyoptimization;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Builds and opens the /opti dialog menus (Paper 1.21.6+).
 * Falls back gracefully if dialogs are unavailable.
 */
public class Ui {

    // ── inner records ────────────────────────────────────────────────────────

    /** A button shown in a dialog screen. */
    public record Btn(String label, String tooltip, String command, String url) {}

    /** A complete dialog screen. */
    public record Screen(String title, List<String> sections,
                         List<Btn> buttons, Btn exit, int columns, int width) {}

    /** Which dialog page to open. */
    public enum Open {
        MAIN, PERFORMANCE, CLEANUP, REDSTONE, ADVANCED,
        OPTIMIZATION, COMMANDS, UNKNOWN_PAGE, FAILED
    }

    @FunctionalInterface
    interface ScreenRenderer {
        void show(Player player, Screen screen);
    }

    // ── state ────────────────────────────────────────────────────────────────

    private final XYOptimization plugin;

    private String safeColor    = "#7DD3FC";
    private String problemColor = "#86EFAC";
    private double problemTps   = 18.0;
    private int    problemMinutes = 10;

    private boolean dialogWanted  = true;
    private boolean everWorked    = false;
    private int     failures      = 0;

    private static final List<Open> PAGES = List.of(
            Open.MAIN, Open.PERFORMANCE, Open.CLEANUP,
            Open.REDSTONE, Open.ADVANCED, Open.OPTIMIZATION, Open.COMMANDS);

    private static final List<String> TOPICS = List.of(
            "plugins", "performance", "cleanup",
            "redstone", "advanced", "optimization", "commands");

    public Ui(XYOptimization plugin) {
        this.plugin = plugin;
        dialogWanted = plugin.mainFile.cfg().getBoolean("use-dialogs", true);
    }

    public void refreshTheme(String safe, String problem,
                              double problemTps, int problemMinutes) {
        this.safeColor      = safe;
        this.problemColor   = problem;
        this.problemTps     = problemTps;
        this.problemMinutes = problemMinutes;
    }

    // ── public API ───────────────────────────────────────────────────────────

    /** Open a named page for a player. */
    public Open open(Player player, String pageName) {
        Open page = resolve(pageName);
        if (page == Open.UNKNOWN_PAGE) return Open.UNKNOWN_PAGE;

        Screen screen = build(page, player);
        if (screen == null) return Open.FAILED;

        try {
            show(player, screen);
            everWorked = true;
        } catch (Throwable t) {
            failures++;
            writeDiagnostic(plugin, pageName, t);
            return Open.FAILED;
        }
        return page;
    }

    // ── screen builders ──────────────────────────────────────────────────────

    private Screen build(Open page, Player player) {
        String accent = currentAccent();

        return switch (page) {
            case MAIN -> buildMain(accent);
            case PERFORMANCE -> buildPerformance(accent);
            case CLEANUP     -> buildCleanup(accent);
            case REDSTONE    -> buildRedstone(accent);
            case ADVANCED    -> buildAdvanced(accent);
            default          -> buildMain(accent);
        };
    }

    private Screen buildMain(String accent) {
        double tps  = plugin.tps.current();
        double mspt = plugin.tps.mspt();

        List<String> sections = List.of(
                accent + "XY-OPTIMIZATION",
                "TPS: " + String.format("%.1f", tps)
                        + "  MSPT: " + String.format("%.1f", mspt),
                "Plugin by XY STUDIO  |  /xyopti help"
        );

        List<Btn> buttons = List.of(
                new Btn("Performance",  "View/simulation distance & adaptive", "xyopti menu performance", null),
                new Btn("Cleanup",      "Auto entity cleanup settings",        "xyopti menu cleanup",     null),
                new Btn("Redstone",     "Redstone limiter & world toggles",    "xyopti menu redstone",    null),
                new Btn("Advanced",     "Profiler & lag machine history",      "xyopti menu advanced",    null),
                new Btn("Discord",      "Get support",                         null, "https://discord.gg/acx8GFwY4v")
        );

        return new Screen("XY-OPT Main", sections, buttons, null, 2, 300);
    }

    private Screen buildPerformance(String accent) {
        var perf = plugin.perf;
        List<String> sections = List.of(
                accent + "Performance",
                "View distance: " + perf.viewDistance()
                        + "  Sim distance: " + perf.simDistance(),
                "Adaptive: " + (perf.isReduced() ? "&cReduced" : "&aNormal")
        );
        List<Btn> buttons = List.of(
                new Btn("Back", "Return to the main menu", "xyopti menu main", null)
        );
        return new Screen("Performance", sections, buttons, null, 1, 300);
    }

    private Screen buildCleanup(String accent) {
        var cleanup = plugin.cleanup;
        String last = cleanup.lastCleanup() == null ? "Never" : cleanup.lastCleanup().toString();
        List<String> sections = List.of(
                accent + "Cleanup",
                "Last cleanup: " + last
        );
        List<Btn> buttons = List.of(
                new Btn("Run cleanup now", "Remove items/mobs/projectiles/xp", "xyopti clear", null),
                new Btn("Back", "Return to the main menu", "xyopti menu main", null)
        );
        return new Screen("Cleanup", sections, buttons, null, 1, 300);
    }

    private Screen buildRedstone(String accent) {
        var rs = plugin.redstone;
        List<String> sections = List.of(
                accent + "Redstone",
                "Global: " + (rs.isAllOff() ? "&cDISABLED" : "&aENABLED"),
                "Limiter: " + (plugin.redFile.cfg().getBoolean("limiter.enabled", false)
                        ? "&aON" : "&cOFF")
        );
        List<Btn> buttons = List.of(
                new Btn("Toggle global", "Enable/disable all redstone", "xyopti redstone toggle", null),
                new Btn("Back", "Return to the main menu", "xyopti menu main", null)
        );
        return new Screen("Redstone", sections, buttons, null, 1, 300);
    }

    private Screen buildAdvanced(String accent) {
        var profiler = plugin.profiler;
        List<String> sections = List.of(
                accent + "Advanced",
                "Profiler: " + (profiler.isEnabled() ? "&aON" : "&cOFF"),
                "Lag machines detected: " + plugin.lagDetector.getHistory().size()
        );
        List<Btn> buttons = List.of(
                new Btn("View lag machines", "Show lag machine history", "xyopti lagmachines", null),
                new Btn("Back", "Return to the main menu", "xyopti menu main", null)
        );
        return new Screen("Advanced", sections, buttons, null, 1, 300);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void show(Player player, Screen screen) {
        // try Paper dialog API first; fall back to chat
        if (dialogWanted) {
            try {
                DialogRenderer.show(plugin, player, screen);
                return;
            } catch (Throwable t) {
                failures++;
            }
        }
        // chat fallback
        plugin.msg.plainP(player, "&8&m----------");
        plugin.msg.plainP(player, "&b" + screen.title());
        for (String s : screen.sections())
            plugin.msg.plainP(player, " " + s);
        for (Btn b : screen.buttons())
            if (b.command() != null)
                plugin.msg.plainP(player, " &7▶ &f" + b.label() + " &8→ &7/" + b.command());
        plugin.msg.plainP(player, "&8&m----------");
    }

    private Open resolve(String name) {
        if (name == null) return Open.MAIN;
        int idx = TOPICS.indexOf(name.toLowerCase(java.util.Locale.ROOT));
        return idx >= 0 ? PAGES.get(idx) : Open.UNKNOWN_PAGE;
    }

    private String currentAccent() {
        return plugin.tps.current() < problemTps ? problemColor : safeColor;
    }

    private static void writeDiagnostic(XYOptimization plugin, String page, Throwable t) {
        plugin.getLogger().warning("Dialog failed for page '" + page + "': " + t.getMessage());
    }

    public List<Open>  getPages()  { return PAGES;  }
    public List<String> getTopics(){ return TOPICS; }
}
