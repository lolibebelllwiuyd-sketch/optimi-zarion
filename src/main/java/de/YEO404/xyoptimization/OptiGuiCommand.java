package de.YEO404.xyoptimization;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Handles the /opti command — opens the Paper dialog GUI.
 * Console is told to use /xyopti instead.
 */
public class OptiGuiCommand implements CommandExecutor, TabCompleter {

    private final XYOptimization plugin;

    public OptiGuiCommand(XYOptimization plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            plugin.msg.plainP(sender,
                    "&c/opti opens menus for players only. Use &f/xyopti &cin the console.");
            return true;
        }

        if (!player.hasPermission("xyopti.admin")) {
            plugin.msg.send(player, "no-permission");
            return true;
        }

        String page = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "main";

        try {
            Ui.Open result = plugin.ui.open(player, page);

            if (result == Ui.Open.UNKNOWN_PAGE) {
                plugin.msg.plainP(player, "&cUnknown page. Pages: "
                        + String.join(", ", plugin.ui.getTopics()));
            } else if (result == Ui.Open.FAILED) {
                plugin.msg.plainP(player,
                        "&cFailed to open dialog. Try &f/xyopti help &cfor text commands.");
            }
        } catch (Throwable t) {
            plugin.msg.plainP(player, "&cError: " + t.getMessage());
            plugin.getLogger().warning("/opti error: " + t.getMessage());
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1)
            return plugin.ui.getTopics().stream()
                    .filter(t -> t.startsWith(args[0].toLowerCase()))
                    .toList();
        return Collections.emptyList();
    }
}
