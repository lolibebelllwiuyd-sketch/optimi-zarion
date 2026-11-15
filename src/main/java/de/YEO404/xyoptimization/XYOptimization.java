package de.YEO404.xyoptimization;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class XYOptimization extends JavaPlugin {

    // ── feature managers ────────────────────────────────────────────────────
    public YmlFile mainFile;
    public YmlFile optFile;
    public YmlFile lagFile;
    public YmlFile redFile;
    public YmlFile msgFile;
    public YmlFile stateFile;

    public Msg           msg;
    public TpsMonitor    tps;
    public PerformanceManager perf;
    public RedstoneManager    redstone;
    public LagDetector        lagDetector;
    public CleanupManager     cleanup;
    public ChunkScanner       scanner;
    public Profiler           profiler;
    public ConfigService      configService;
    public Ui                 ui;

    private boolean loaded = false;

    // ── lifecycle ────────────────────────────────────────────────────────────
    @Override
    public void onEnable() {

        // create folders
        new File(getDataFolder(), "logs").mkdirs();
        new File(getDataFolder(), "data").mkdirs();

        // load yml files
        mainFile  = new YmlFile(this, "config.yml");
        optFile   = new YmlFile(this, "optimization.yml");
        lagFile   = new YmlFile(this, "lag-machine.yml");
        redFile   = new YmlFile(this, "redstone.yml");
        msgFile   = new YmlFile(this, "messages.yml");
        stateFile = new YmlFile(this, "data/state.yml");
        stateFile.load();

        // boot features
        Bukkit.getScheduler().runTaskLater(this, this::loadFeatures, 1L);
    }

    private void loadFeatures() {
        try {
            msg           = new Msg(this, msgFile);
            tps           = new TpsMonitor(this);
            configService = new ConfigService(this);
            perf          = new PerformanceManager(this);
            redstone      = new RedstoneManager(this);
            lagDetector   = new LagDetector(this);
            cleanup       = new CleanupManager(this);
            scanner       = new ChunkScanner(this);
            profiler      = new Profiler(this);
            ui            = new Ui(this);

            // apply config values
            applyConfig();

            // register commands
            getCommand("xyopti").setExecutor(new OptiCommand(this));
            getCommand("xyopti").setTabCompleter(new OptiCommand(this));
            getCommand("opti").setExecutor(new OptiGuiCommand(this));

            // start timers
            tps.start();
            perf.start();
            redstone.start();
            lagDetector.start();
            profiler.start();

            loaded = true;
            getLogger().info("XY-OPTIMIZATION v" + getDescription().getVersion()
                    + " enabled | PLUGIN BY: XY STUDIO | AUTHOR: YEO404.JAR | /opti");

        } catch (Throwable t) {
            getLogger().severe("Failed to load XY-OPTIMIZATION: " + t.getMessage());
            t.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (!loaded) return;
        perf.restore();
        redstone.save();
        profiler.stop();
        stateFile.save();
        getLogger().info("XY-OPTIMIZATION disabled.");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Re-reads all config files and pushes values to every manager. */
    public void reload() {
        mainFile.load();
        optFile.load();
        lagFile.load();
        redFile.load();
        msgFile.load();
        msg.reload();
        applyConfig();
        perf.reload();
        redstone.reload();
        lagDetector.reload();
        profiler.reload();
    }

    private void applyConfig() {
        var cfg = mainFile.cfg();

        // theme colors
        String safe    = cfg.getString("theme.safe",    "#7DD3FC");
        String problem = cfg.getString("theme.problem", "#86EFAC");
        double problemTps     = cfg.getDouble("theme.problem-tps",     18.0);
        int    problemMinutes = cfg.getInt   ("theme.problem-minutes", 10);

        msg.theme(safe, problem);
        ui.refreshTheme(safe, problem, problemTps, problemMinutes);

        redstone.reloadLimiter();
    }

    public boolean isLoaded() { return loaded; }
}
