package io.github.faboit1.blg.listener;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Starts the login flow when a player joins.
 *
 * <p>This is only triggered when {@code autojoinlogingui: true} is set in
 * {@code config.yml}.  The flow:
 * <ol>
 *   <li>Rules dialog (if enabled and player hasn't accepted the current version)</li>
 *   <li>Choice dialog – a single "Open Login" or "Open Register" button,
 *       spammed every 100 ms
 *       up to the configured timeout</li>
 *   <li>The actual login or register dialog (opened when the button is clicked)</li>
 * </ol>
 *
 * <p>A configurable delay is applied before starting the flow so the player's
 * client has time to finish the loading screen (avoids packet-order issues).
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

        // Delay before opening the first dialog so the client finishes the loading screen.
        long delayTicks = plugin.getConfig().getLong("join-dialog-delay-ticks", 20L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.getAuthMeHook().isHooked()
                    && plugin.getAuthMeHook().isAuthenticated(player)) {
                return;
            }
            plugin.getFlowManager().startFlow(player);
        }, delayTicks);
    }
}
