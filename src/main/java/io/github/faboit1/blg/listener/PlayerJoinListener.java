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
 *       spammed every {@code join-choice-spam-interval-ticks} ticks
 *       up to the configured timeout</li>
 *   <li>The actual login or register dialog (opened when the button is clicked)</li>
 * </ol>
 *
 * <p>A configurable delay is applied before starting the flow so the player's
 * client has time to finish the loading screen (avoids packet-order issues).
 * When running behind a Velocity proxy, set {@code velocity-backend: true} to
 * disable the unreliable online-mode detection and use a proxy-friendly delay.
 */
public class PlayerJoinListener implements Listener {

    private final BLGPlugin plugin;

    public PlayerJoinListener(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        boolean autoOpen = plugin.getConfig().getBoolean("autojoinlogingui",
                plugin.getConfig().getBoolean("auto-join-login-gui",
                        plugin.getConfig().getBoolean("auto-open-on-join", false)));

        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] PlayerJoin: " + event.getPlayer().getName()
                    + " | autojoinlogingui=" + autoOpen);
        }

        if (!autoOpen) {
            return;
        }

        var player = event.getPlayer();
        boolean velocityBackend = plugin.getConfig().getBoolean("velocity-backend", false);

        // Delay before opening the first dialog so the client finishes the loading screen.
        // When behind a Velocity proxy the default delay may be insufficient; the admin can
        // raise join-dialog-delay-ticks in the config (recommended 40–60 for Velocity).
        long delayTicks = plugin.getConfig().getLong("join-dialog-delay-ticks", 20L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Skipping flow for " + player.getName()
                            + " – player went offline during delay.");
                }
                return;
            }

            // Online-mode detection is unreliable on Velocity backends (Velocity-forwarded
            // premium players appear as online-mode to Paper even on offline-mode backends).
            // Skip this check entirely when velocity-backend: true.
            if (!velocityBackend
                    && plugin.getConfig().getBoolean("skip-online-mode-players", true)
                    && isOnlineMode(player)) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Skipping flow for " + player.getName()
                            + " – detected as online-mode player (skip-online-mode-players=true).");
                }
                return;
            }

            if (plugin.getConfig().getBoolean("skip-bedrock-players", true)
                    && plugin.getGeyserHook().isBedrockPlayer(player)) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Skipping flow for " + player.getName()
                            + " – detected as Bedrock player.");
                }
                return;
            }

            if (plugin.getAuthMeHook().isHooked()
                    && plugin.getAuthMeHook().isAuthenticated(player)) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Skipping flow for " + player.getName()
                            + " – already authenticated via AuthMe.");
                }
                return;
            }

            if (plugin.isDebugMode()) {
                plugin.getLogger().info("[DEBUG] Starting flow for " + player.getName()
                        + " | velocityBackend=" + velocityBackend
                        + " | delayTicks=" + delayTicks);
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
     *
     * <p><strong>Note:</strong> This method is skipped entirely when
     * {@code velocity-backend: true} is set in config, because Velocity-forwarded
     * players may incorrectly appear as online-mode on the backend server.
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
