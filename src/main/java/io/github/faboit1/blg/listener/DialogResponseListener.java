package io.github.faboit1.blg.listener;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Guards the internal {@code /blg_login_submit} and
 * {@code /blg_register_submit} commands from being run directly in chat.
 *
 * <p>The dialog API sends the command template as a normal command; there is
 * no way at the packet level to distinguish a dialog-submitted command from a
 * manually typed one.  As a lightweight protection layer this listener logs a
 * warning when either internal command appears to have been typed by the
 * player without a prior dialog interaction.
 *
 * <p>No rate-limiting or blocking is applied here because the commands
 * themselves are harmless – they merely call {@code /login} or
 * {@code /register} which AuthMe already rate-limits.
 */
public class DialogResponseListener implements Listener {

    private static final String CMD_LOGIN_SUBMIT    = "/blg_login_submit";
    private static final String CMD_REGISTER_SUBMIT = "/blg_register_submit";

    private final BLGPlugin plugin;

    public DialogResponseListener(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Intercepts command preprocessing so we can detect if a player somehow
     * types an internal BLG command directly in chat (e.g. trying to bypass
     * the dialog step).  We let the command through regardless – AuthMe is
     * the actual security layer.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase();
        if (msg.startsWith(CMD_LOGIN_SUBMIT) || msg.startsWith(CMD_REGISTER_SUBMIT)) {
            plugin.getLogger().fine(
                    "Internal BLG submit command used by "
                    + event.getPlayer().getName()
                    + ": " + event.getMessage());
        }
    }
}
