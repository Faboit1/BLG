package io.github.faboit1.blg.listener;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Intercepts internal {@code /blg_*} commands before AuthMe command blocking.
 *
 * <p>AuthMe can cancel unknown commands for unauthenticated players during
 * preprocess, which would prevent dialog buttons that run internal BLG
 * commands from working. We cancel those preprocess events at LOWEST and
 * dispatch the command directly through Bukkit's command map.
 */
public class DialogResponseListener implements Listener {

    private static final String INTERNAL_PREFIX = "/blg_";

    private final BLGPlugin plugin;

    public DialogResponseListener(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Intercepts command preprocessing so internal BLG commands from dialog
     * actions are not blocked by AuthMe before they reach command executors.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || !message.regionMatches(true, 0, INTERNAL_PREFIX, 0, INTERNAL_PREFIX.length())) {
            return;
        }

        event.setCancelled(true);
        String commandLine = message.startsWith("/") ? message.substring(1) : message;
        plugin.getServer().dispatchCommand(event.getPlayer(), commandLine);
    }
}
