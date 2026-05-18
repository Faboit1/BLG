package io.github.faboit1.blg.auth;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Soft-dependency bridge for Geyser / Floodgate.
 *
 * <p>Detection order:
 * <ol>
 *   <li>If the Floodgate plugin is present, use
 *       {@code FloodgateApi.getInstance().isFloodgatePlayer(uuid)} via
 *       reflection so we don't need Floodgate on the compile classpath.</li>
 *   <li>Fallback: check whether the player's name contains a {@code .}
 *       character.  Floodgate prefixes Bedrock player names with a dot by
 *       default, so this works even without the Floodgate API available.</li>
 * </ol>
 */
public class GeyserHook {

    private final BLGPlugin plugin;

    /**
     * Cached reference to the {@code FloodgateApi} instance, or {@code null}
     * when Floodgate is not installed.
     */
    private Object floodgateApi;

    /**
     * The {@code isFloodgatePlayer(UUID)} method handle, or {@code null} if
     * Floodgate is absent.
     */
    private java.lang.reflect.Method isFloodgatePlayerMethod;

    public GeyserHook(BLGPlugin plugin) {
        this.plugin = plugin;
        tryHook();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the player is a Bedrock player (connected via
     * Geyser / Floodgate) or if the player's username contains a dot
     * ({@code .}), which is the default Floodgate name prefix.
     *
     * @param player the player to check
     * @return {@code true} if the player appears to be a Bedrock player
     */
    public boolean isBedrockPlayer(Player player) {
        // Primary: Floodgate API
        if (floodgateApi != null && isFloodgatePlayerMethod != null) {
            try {
                Object result = isFloodgatePlayerMethod.invoke(floodgateApi, player.getUniqueId());
                if (result instanceof Boolean b) {
                    return b;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Floodgate isFloodgatePlayer() threw an exception for "
                        + player.getName() + ": " + e.getMessage(), e);
            }
        }

        // Fallback: dot in username (default Floodgate prefix)
        return player.getName().contains(".");
    }

    /**
     * Returns {@code true} when the Floodgate API was found and hooked
     * successfully.  When {@code false}, the dot-in-name fallback is used.
     */
    public boolean isHooked() {
        return floodgateApi != null;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void tryHook() {
        // Only attempt if the Floodgate plugin is actually loaded
        if (plugin.getServer().getPluginManager().getPlugin("floodgate") == null
                && plugin.getServer().getPluginManager().getPlugin("Floodgate") == null) {
            plugin.getLogger().info(
                    "Floodgate not found. Bedrock detection will use dot-in-name fallback.");
            return;
        }

        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApi = apiClass.getMethod("getInstance").invoke(null);
            isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            plugin.getLogger().info("Hooked into Floodgate for Bedrock player detection.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Floodgate plugin is present but the API could not be loaded – "
                    + "falling back to dot-in-name Bedrock detection. Cause: "
                    + e.getMessage(), e);
        }
    }
}
