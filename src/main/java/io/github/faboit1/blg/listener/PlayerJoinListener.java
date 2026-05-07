package io.github.faboit1.blg.listener;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Optionally auto-opens the appropriate dialog when a player joins.
 *
 * <p>This is only triggered when {@code autojoinlogingui: true} is set in
 * {@code config.yml} (with {@code auto-open-on-join} kept as a legacy alias).
 * It delegates to the same logic as {@code /openauto}.
 *
 * <p>A one-tick delay is applied before opening the dialog to ensure the
 * player's client is fully loaded (avoids packet-order issues on some
 * client versions).
 */
public class PlayerJoinListener implements Listener {

    private final BLGPlugin plugin;

    public PlayerJoinListener(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("autojoinlogingui",
                plugin.getConfig().getBoolean("auto-join-login-gui",
                        plugin.getConfig().getBoolean("auto-open-on-join", false)))) {
            return;
        }

        var player = event.getPlayer();

        // 1-tick delay to let the client finish loading
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.getAuthMeHook().isHooked()
                    && plugin.getAuthMeHook().isAuthenticated(player)) {
                return;
            }
            // When AuthMe is not hooked, default to the login dialog so
            // returning players are not incorrectly shown the register form.
            if (plugin.getAuthMeHook().isHooked()
                    && !plugin.getAuthMeHook().isRegistered(player)) {
                plugin.getDialogManager().openRegisterDialog(player);
            } else {
                plugin.getDialogManager().openLoginDialog(player);
            }
        }, 1L);
    }
}
