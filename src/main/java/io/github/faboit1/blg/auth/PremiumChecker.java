package io.github.faboit1.blg.auth;

import io.github.faboit1.blg.BLGPlugin;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Checks whether a Minecraft username belongs to a premium (paid) account by
 * querying the Mojang session-server API.
 *
 * <p>The lookup hits
 * {@code https://sessionserver.mojang.com/session/minecraft/hasJoined?username=NAME}
 * on a background thread so that the main server thread is never blocked.
 *
 * <p>This works correctly on Velocity / BungeeCord backends where the
 * per-player {@code Player.isOnlineMode()} flag is unreliable because Velocity
 * proxies always forward players as online-mode to the backend.
 */
public class PremiumChecker {

    /**
     * Mojang API endpoint.  A 200 response means the username belongs to a
     * paid account; a 204 (no content) means it does not.
     */
    private static final String MOJANG_PROFILE_URL =
            "https://api.mojang.com/users/profiles/minecraft/";

    private final BLGPlugin plugin;

    public PremiumChecker(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Asynchronously checks whether {@code username} belongs to a premium
     * Mojang / Microsoft account.
     *
     * <p>The {@code callback} receives {@code true} if the account exists
     * (premium), {@code false} otherwise.  The callback is invoked on the
     * <strong>main server thread</strong> so it is safe to interact with the
     * Bukkit API from inside it.
     *
     * @param username the player's username to look up
     * @param callback called with the result on the main thread
     */
    public void isPremiumAsync(String username, Consumer<Boolean> callback) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean premium = checkMojangApi(username);
            // Return the result on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(premium));
        });
    }

    /**
     * Synchronous Mojang API lookup.  <strong>Must be called off the main
     * thread.</strong>
     *
     * @param username the player username to query
     * @return {@code true} if the Mojang API reports the account as existing
     */
    private boolean checkMojangApi(String username) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(MOJANG_PROFILE_URL
                    + URLEncoder.encode(username, StandardCharsets.UTF_8));
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(false);

            int responseCode = connection.getResponseCode();

            if (plugin.isDebugMode()) {
                plugin.getLogger().info("[DEBUG] Mojang API check for '" + username
                        + "' returned HTTP " + responseCode);
            }

            // 200 = profile found (premium account)
            // 204 or 404 = no profile (cracked / non-existent)
            return responseCode == 200;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Mojang API lookup failed for '" + username + "': " + e.getMessage());
            // On failure, assume NOT premium so the login flow still runs
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
