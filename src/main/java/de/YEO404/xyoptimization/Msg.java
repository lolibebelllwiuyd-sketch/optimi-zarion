package de.YEO404.xyoptimization;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.regex.Pattern;

/**
 * Handles all player/console messages.
 * Supports & colour codes and &#RRGGBB hex colours.
 */
public class Msg {

    private static final Pattern HEX_PATTERN =
            Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()
                    .build();

    private final XYOptimization plugin;
    private final YmlFile file;

    private String safeColor    = "#7DD3FC";
    private String problemColor = "#86EFAC";
    private String prefix;

    public Msg(XYOptimization plugin, YmlFile file) {
        this.plugin = plugin;
        this.file   = file;
        reload();
    }

    public void reload() {
        prefix = file.cfg().getString("prefix", "&8[&bXY-OPT&8] &r");
    }

    public void theme(String safe, String problem) {
        this.safeColor    = safe;
        this.problemColor = problem;
    }

    /** Send a message from messages.yml to a player/console. */
    public void send(CommandSender sender, String key, String... placeholders) {
        String raw = file.cfg().getString(key, "&c[MSG NOT FOUND: " + key + "]");
        raw = applyPlaceholders(raw, placeholders);
        sender.sendMessage(color(prefix + raw));
    }

    /** Send a plain coloured string directly. */
    public void plainP(CommandSender sender, String text) {
        sender.sendMessage(color(text));
    }

    /** Translate & codes + &#RRGGBB hex to a Component. */
    public Component color(String text, String... replacements) {
        String s = applyPlaceholders(text, replacements);
        // convert &#RRGGBB → &x&R&R&G&G&B&B for the legacy serializer
        s = HEX_PATTERN.matcher(s).replaceAll(mr ->
                "&x&" + mr.group(1).chars()
                        .mapToObj(c -> "&" + (char) c)
                        .reduce("", String::concat));
        return LEGACY.deserialize(s);
    }

    /** Strip colour codes from a string. */
    public String strip(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .serialize(color(text))
                .replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    public String safeColor()    { return safeColor;    }
    public String problemColor() { return problemColor; }

    // ── internals ────────────────────────────────────────────────────────────

    private String applyPlaceholders(String s, String[] pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2)
            s = s.replace(pairs[i], pairs[i + 1]);
        return s;
    }
}
