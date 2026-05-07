package io.github.faboit1.blg.auth;

import fr.xephi.authme.api.v3.AuthMeApi;
import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

/**
 * Soft-dependency bridge to AuthMe.
 *
 * <p>BLG uses AuthMe <em>only</em> to determine whether a player already has
 * a registered account.  All password handling, hashing, and authentication
 * logic remain entirely inside AuthMe.
 *
 * <p>If AuthMe is not installed the hook gracefully degrades: all
 * {@link #isRegistered} calls return {@code false} (i.e. the plugin will
 * default to showing the register dialog via {@code /openauto}).
 */
public class AuthMeHook {

    private final BLGPlugin plugin;

    /**
     * Live reference to the AuthMe API, or {@code null} if AuthMe is absent.
     */
    private AuthMeApi authMeApi;
    private boolean authenticatedMethodWarningLogged;

    public AuthMeHook(BLGPlugin plugin) {
        this.plugin = plugin;
        tryHook();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the raw {@link AuthMeApi} instance, or {@code null} if AuthMe
     * is not hooked.  Prefer calling the higher-level methods on this class
     * instead of using the API directly.
     */
    public AuthMeApi getAuthMeApi() {
        return authMeApi;
    }

    /**
     * Returns {@code true} if the given player has a registered account in
     * AuthMe.
     *
     * <p>Falls back to {@code false} when AuthMe is unavailable, so that
     * {@code /openauto} shows the register dialog rather than the login
     * dialog.
     *
     * @param player the player to check
     * @return {@code true} if registered in AuthMe, {@code false} otherwise
     */
    public boolean isRegistered(Player player) {
        if (authMeApi == null) {
            return false;
        }
        try {
            return authMeApi.isRegistered(player.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "AuthMe isRegistered() threw an exception for "
                    + player.getName() + ": " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Returns {@code true} if the given player is currently authenticated in
     * AuthMe.
     *
     * <p>The exact AuthMe API surface varies between builds, so this lookup is
     * performed reflectively against the hooked API instance.
     */
    public boolean isAuthenticated(Player player) {
        if (authMeApi == null) {
            return false;
        }

        try {
            Boolean result = invokeBoolean("isAuthenticated", Player.class, player);
            if (result != null) {
                return result;
            }

            result = invokeBoolean("isAuthenticated", String.class, player.getName());
            if (result != null) {
                return result;
            }

            logUnsupportedAuthenticatedMethod();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "AuthMe isAuthenticated() threw an exception for "
                            + player.getName() + ": " + e.getMessage(), e);
        }

        return false;
    }

    /**
     * Returns {@code true} when the AuthMe plugin was found and hooked
     * successfully.
     */
    public boolean isHooked() {
        return authMeApi != null;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Attempts to obtain the AuthMe API handle via the Bukkit services manager.
     * Tries the registered service provider first (recommended), then falls
     * back to {@link AuthMeApi#getInstance()} for older AuthMe builds.
     */
    private void tryHook() {
        // Prefer the services-manager registration (AuthMe 5.6+)
        try {
            RegisteredServiceProvider<AuthMeApi> provider =
                    plugin.getServer().getServicesManager()
                            .getRegistration(AuthMeApi.class);
            if (provider != null) {
                authMeApi = provider.getProvider();
                plugin.getLogger().info("Hooked into AuthMe via ServicesManager.");
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE,
                    "ServicesManager AuthMe lookup failed – trying getInstance()", e);
        }

        // Fallback: static singleton (AuthMe < 5.6)
        try {
            authMeApi = AuthMeApi.getInstance();
            if (authMeApi != null) {
                plugin.getLogger().info("Hooked into AuthMe via AuthMeApi.getInstance().");
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE,
                    "AuthMeApi.getInstance() failed.", e);
        }

        plugin.getLogger().warning(
                "AuthMe not found or could not be hooked. " +
                "/openauto will default to the register dialog.");
    }

    private Boolean invokeBoolean(String methodName, Class<?> parameterType, Object argument)
            throws ReflectiveOperationException {
        try {
            Object result = authMeApi.getClass()
                    .getMethod(methodName, parameterType)
                    .invoke(authMeApi, argument);
            return result instanceof Boolean bool ? bool : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private void logUnsupportedAuthenticatedMethod() {
        if (authenticatedMethodWarningLogged) {
            return;
        }
        authenticatedMethodWarningLogged = true;
        plugin.getLogger().log(Level.WARNING,
                "AuthMe API does not expose a supported isAuthenticated method; "
                        + "autojoin login checks will treat players as unauthenticated.");
    }
}
