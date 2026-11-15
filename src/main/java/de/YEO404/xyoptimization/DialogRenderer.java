package de.YEO404.xyoptimization;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Wraps the Paper 1.21.6+ Dialog API via reflection so the plugin
 * still compiles and runs on servers that don't have it.
 */
public class DialogRenderer {

    private DialogRenderer() {}

    /**
     * Try to open a Paper Dialog for the player.
     * Throws if the API is unavailable or fails.
     */
    public static void show(XYOptimization plugin, Player player, Ui.Screen screen)
            throws Exception {

        // Check if Dialog class exists (Paper 1.21.6+)
        Class<?> dialogClass = Class.forName("io.papermc.paper.dialog.Dialog");

        // Build a simple dialog via reflection
        Object builder = tryCall(dialogClass, "builder", new Object[0]);

        // Set title
        builder = tryCall(builder, "title",
                new Object[]{ plugin.msg.color(screen.title()) });

        // Build body text from sections
        if (!screen.sections().isEmpty()) {
            String body = String.join("\n", screen.sections());
            builder = tryCall(builder, "body",
                    new Object[]{ plugin.msg.color(body) });
        }

        // Add buttons
        for (Ui.Btn btn : screen.buttons()) {
            Object btnObj = buildButton(plugin, player, btn);
            if (btnObj != null)
                builder = tryCall(builder, "button", new Object[]{ btnObj });
        }

        builder = tryCall(builder, "canCloseWithEscape", new Object[]{ true });

        Object dialog = tryCall(builder, "build", new Object[0]);

        // show to player
        Method showDialog = player.getClass().getMethod("showDialog", dialogClass);
        showDialog.invoke(player, dialog);
    }

    private static Object buildButton(XYOptimization plugin, Player player, Ui.Btn btn) {
        try {
            Class<?> btnClass = Class.forName("io.papermc.paper.dialog.DialogButton");
            Object b = tryCall(btnClass, "builder", new Object[0]);
            b = tryCall(b, "label", new Object[]{ plugin.msg.color(btn.label()) });
            if (btn.command() != null)
                b = tryCall(b, "command", new Object[]{ btn.command() });
            if (btn.url() != null)
                b = tryCall(b, "url", new Object[]{ btn.url() });
            return tryCall(b, "build", new Object[0]);
        } catch (Throwable t) {
            return null;
        }
    }

    // ── reflection helpers ───────────────────────────────────────────────────

    private static Object tryCall(Object target, String method, Object[] args)
            throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == args.length) {
                return m.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + method);
    }

    private static Object tryCall(Class<?> clazz, String method, Object[] args)
            throws Exception {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == args.length) {
                return m.invoke(null, args);
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "." + method);
    }
}
