package io.github.faboit1.blg.listener;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Intercepts internal {@code /blg_*} commands and public BLG commands before
 * AuthMe command blocking.
 *
 * <p>AuthMe can cancel unknown commands for unauthenticated players during
 * preprocess, which would prevent dialog buttons that run internal BLG
 * commands from working. We cancel those preprocess events at LOWEST and
 * dispatch the command directly through Bukkit's command map.
 *
 * <p>Public commands ({@code /openlogin}, {@code /openregister}, etc.) are
 * also intercepted so that AuthMe does not send an "authenticated" error
 * message when the auto-join flow dispatches them.
 */
public class DialogResponseListener implements Listener {

    private static final String INTERNAL_PREFIX = "/blg_";

    /** Public BLG commands that should bypass AuthMe's command restriction. */
    private static final java.util.Set<String> PUBLIC_COMMANDS = java.util.Set.of(
            "openlogin", "openregister", "openauto", "openprelogin"
    );

    private final BLGPlugin plugin;

    public DialogResponseListener(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Intercepts command preprocessing so internal BLG commands from dialog
     * actions are not blocked by AuthMe before they reach command executors.
     *
     * <p>We neutralize the message (replace it with {@code "/"}) before
     * cancelling so that AuthMe's own preprocess handler – which may run with
     * {@code ignoreCancelled = false} at a higher priority – sees only a bare
     * slash and does not send a "cannot run this command while unauthenticated"
     * message.  The actual command is dispatched directly through
     * {@code Server#dispatchCommand}, which bypasses the preprocess event.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.regionMatches(true, 0, INTERNAL_PREFIX, 0, INTERNAL_PREFIX.length())
                || isPublicBLGCommand(message)) {
            String commandLine = message.startsWith("/") ? message.substring(1) : message;
            // Neutralize the message before cancelling so other handlers (e.g. AuthMe)
            // do not identify or block the internal command.
            event.setMessage("/");
            event.setCancelled(true);
            plugin.getServer().dispatchCommand(event.getPlayer(), commandLine);
        }
    }

    /**
     * Returns {@code true} if the message matches a public BLG command
     * (e.g. {@code /openlogin}, {@code /openregister}).
     */
    private static boolean isPublicBLGCommand(String message) {
        if (message.isEmpty() || message.charAt(0) != '/') {
            return false;
        }
        // Extract the command name (first word after the slash)
        String afterSlash = message.substring(1);
        int spaceIdx = afterSlash.indexOf(' ');
        String cmdName = (spaceIdx == -1 ? afterSlash : afterSlash.substring(0, spaceIdx))
                .toLowerCase(java.util.Locale.ROOT);
        return PUBLIC_COMMANDS.contains(cmdName);
    }
}
