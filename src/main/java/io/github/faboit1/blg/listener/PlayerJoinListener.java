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
 *
 * <p><strong>Proxy / Velocity support:</strong> When this server is running
 * behind a Velocity (or BungeeCord) proxy the backend is almost always
 * configured in offline-mode ({@code server.getOnlineMode() == false}).  The
 * per-player online-mode check ({@code skip-online-mode-players}) is disabled
 * by default, so no special configuration is needed – the dialog flow works
 * out of the box on all server types (standalone, proxy backend, online-mode,
 * offline-mode).  No {@code velocity-backend: true} config flag or separate
 * Velocity-side plugin is required.
 *
 * <p>Even if an admin explicitly enables {@code skip-online-mode-players},
 * the check is guarded by both the {@code velocity-backend} flag and
 * {@link org.bukkit.Server#getOnlineMode()} so it can only fire on standalone
 * offline-mode servers where the per-player detection is actually reliable.
 */
public class PlayerJoinListener implements Listener {

    private final BLGPlugin plugin;

    public PlayerJoinListener(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        // Kick players whose username contains a dot after the first character.
        // Leading dots are allowed (Bedrock/Floodgate prefix). Bedrock players
        // are always exempt from this check.
        if (plugin.getConfig().getBoolean("kick-dot-usernames", true)) {
            String name = player.getName();
            if (name.indexOf('.') > 0 && !plugin.getGeyserHook().isBedrockPlayer(player)) {
                String reason = plugin.cfg("messages.kick-dot-username-reason");
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Kicking " + name
                            + " – username contains a dot at an illegal position.");
                }
                player.kickPlayer(reason);
                return;
            }
        }

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

        boolean velocityBackend = plugin.getConfig().getBoolean("velocity-backend", false);
        boolean openBeforeIngame = plugin.getConfig().getBoolean("open-before-ingame", false);

        // Delay before opening the first dialog so the client finishes the loading screen.
        // When open-before-ingame is enabled, we use a 1-tick delay and skip the choice
        // stage to show the actual auth dialog as early as possible.
        // When behind a Velocity proxy the default delay may be insufficient; the admin can
        // raise join-dialog-delay-ticks in the config (recommended 40–60 for Velocity).
        long delayTicks = openBeforeIngame ? 1L
                : plugin.getConfig().getLong("join-dialog-delay-ticks", 20L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Skipping flow for " + player.getName()
                            + " – player went offline during delay.");
                }
                return;
            }

            // Per-player online-mode detection (Player#isOnlineMode) is only
            // meaningful on offline-mode (cracked) standalone servers where a mix
            // of premium and cracked players can join.  On online-mode servers
            // every player is premium by definition, so skipping them all would
            // prevent the dialog from ever opening.  On Velocity/BungeeCord
            // backends the per-player flag is unreliable (proxy-forwarded premium
            // players appear as online-mode even on offline-mode backends).
            //
            // This check is therefore DISABLED by default (skip-online-mode-players: false).
            // Admins on standalone cracked servers who want to skip premium players
            // can enable it manually.  The velocity-backend flag and server
            // online-mode auto-detection act as additional safety guards:
            //   - velocity-backend: true → always skip the check
            //   - server in online-mode  → always skip the check (skipping
            //     everyone on an online-mode server is never useful)
            //   - server in offline-mode → honour the config setting
            boolean serverOnlineMode = plugin.getServer().getOnlineMode();
            if (!velocityBackend
                    && !serverOnlineMode
                    && plugin.getConfig().getBoolean("skip-online-mode-players", false)
                    && isOnlineMode(player)) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Skipping flow for " + player.getName()
                            + " – detected as online-mode player (skip-online-mode-players=true).");
                }
                return;
            }

            if (plugin.getConfig().getBoolean("skip-bedrock-players", true)
                    && plugin.getGeyserHook().isBedrockPlayer(player)) {
                // Auto-authenticate Bedrock players if configured
                if (plugin.getConfig().getBoolean("auto-authenticate-bedrock", false)
                        && plugin.getAuthMeHook().isHooked()) {
                    if (plugin.isDebugMode()) {
                        plugin.getLogger().info("[DEBUG] Auto-authenticating Bedrock player "
                                + player.getName() + " via AuthMe forceLogin.");
                    }
                    plugin.getAuthMeHook().forceLogin(player);
                } else if (plugin.isDebugMode()) {
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

            // Check premium status via Mojang API when auto-authenticate-premium
            // is enabled.  The check runs asynchronously so the server thread is
            // not blocked.  Works correctly on Velocity / BungeeCord backends
            // where Player.isOnlineMode() is unreliable.
            boolean autoAuthPremium = plugin.getConfig().getBoolean("auto-authenticate-premium", false);
            if (autoAuthPremium && plugin.getAuthMeHook().isHooked()) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Checking Mojang API for premium status of "
                            + player.getName() + "...");
                }
                final boolean finalOpenBeforeIngame = openBeforeIngame;
                plugin.getPremiumChecker().isPremiumAsync(player.getName(), isPremium -> {
                    if (!player.isOnline()) return;
                    if (isPremium) {
                        if (plugin.isDebugMode()) {
                            plugin.getLogger().info("[DEBUG] " + player.getName()
                                    + " is a premium account – auto-authenticating via AuthMe forceLogin.");
                        }
                        plugin.getAuthMeHook().forceLogin(player);
                    } else {
                        if (plugin.isDebugMode()) {
                            plugin.getLogger().info("[DEBUG] " + player.getName()
                                    + " is NOT a premium account – starting normal flow.");
                        }
                        startLoginFlow(player, finalOpenBeforeIngame);
                    }
                });
                return;
            }

            if (plugin.isDebugMode()) {
                plugin.getLogger().info("[DEBUG] Starting flow for " + player.getName()
                        + " | velocityBackend=" + velocityBackend
                        + " | serverOnlineMode=" + serverOnlineMode
                        + " | delayTicks=" + delayTicks
                        + " | openBeforeIngame=" + openBeforeIngame);
            }

            startLoginFlow(player, openBeforeIngame);
        }, delayTicks);
    }

    /**
     * Starts the appropriate login flow for the player (direct or choice-based).
     */
    private void startLoginFlow(Player player, boolean openBeforeIngame) {
        if (openBeforeIngame) {
            plugin.getFlowManager().startDirectFlow(player);
        } else {
            plugin.getFlowManager().startFlow(player);
        }
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
     * <p><strong>Note:</strong> This method is only consulted when
     * {@code skip-online-mode-players} is explicitly set to {@code true} by the
     * admin <em>and</em> the server is running in offline-mode (standalone
     * cracked servers).  The check is bypassed on online-mode servers and
     * on Velocity / BungeeCord backends.
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
