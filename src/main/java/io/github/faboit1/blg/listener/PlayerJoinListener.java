package io.github.faboit1.blg.listener;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.entity.Player;
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
            // Skip the GUI for players authenticated via Microsoft / Mojang (online-mode).
            // isOnlineMode() is a Paper-specific method not present in all API stubs,
            // so it is called reflectively with a safe fallback of false.
            if (plugin.getConfig().getBoolean("skip-online-mode-players", true)
                    && isOnlineMode(player)) {
                return;
            }
            // Skip the GUI for Bedrock players (Geyser / Floodgate).
            if (plugin.getConfig().getBoolean("skip-bedrock-players", true)
                    && plugin.getGeyserHook().isBedrockPlayer(player)) {
                return;
            }
            if (plugin.getAuthMeHook().isHooked()
                    && plugin.getAuthMeHook().isAuthenticated(player)) {
                return;
            }
            plugin.getFlowManager().startFlow(player);
        }, delayTicks);
    }

    /**
     * Returns {@code true} if the player authenticated in online-mode (premium
     * Microsoft/Mojang account).
     *
     * <p>{@code Player.isOnlineMode()} is a Paper-specific addition that is not
     * present in all paper-api builds.  Calling it reflectively keeps the code
     * compilable against older stubs while still working at runtime on servers
     * that provide the method.  If the method is absent, {@code false} is
     * returned (i.e. the GUI is shown – the safe default for cracked servers).
     */
    private boolean isOnlineMode(Player player) {
        try {
            Object result = player.getClass().getMethod("isOnlineMode").invoke(player);
            return result instanceof Boolean b && b;
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
