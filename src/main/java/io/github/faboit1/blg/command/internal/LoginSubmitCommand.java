package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Internal command: {@code /blg_login_submit <password>}
 *
 * <p>This command is <strong>never</strong> typed by the player manually.
 * It is the command template bound to the submit button of the login dialog:
 * <pre>
 *   template: "/blg_login_submit $(password)"
 * </pre>
 * When the player clicks "Login", the Minecraft client substitutes
 * {@code $(password)} with whatever the player typed in the password field and
 * sends the resulting command to the server.
 *
 * <p>The command forwards the credential to AuthMe by dispatching
 * {@code /login <password>}. If the player is still unauthenticated a moment
 * later, BLG re-opens the login dialog with a best-effort error message.
 */
public class LoginSubmitCommand implements CommandExecutor {

    private static final long RETRY_DELAY_TICKS = 2L;

    private final BLGPlugin plugin;

    public LoginSubmitCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true; // should never happen
        }

        if (args.length < 1) {
            // No password provided – dialog was submitted empty
            player.sendMessage(plugin.msg("internal-command-blocked"));
            return true;
        }

        String password = String.join(" ", args);
        String failureMessage = plugin.msg("login-failed");

        if (plugin.getAuthMeHook().isHooked()) {
            if (!plugin.getAuthMeHook().isRegistered(player)) {
                failureMessage = plugin.msg("login-not-registered");
            } else {
                Boolean passwordMatches = plugin.getAuthMeHook().checkPassword(player, password);
                if (Boolean.FALSE.equals(passwordMatches)) {
                    failureMessage = plugin.msg("login-incorrect-password");
                }
            }
        }

        player.performCommand("login " + password);
        String finalFailureMessage = failureMessage;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (plugin.getAuthMeHook().isHooked() && plugin.getAuthMeHook().isAuthenticated(player)) {
                plugin.getFlowManager().clearPlayer(player);
                return;
            }
            plugin.getDialogManager().openLoginDialog(player, finalFailureMessage);
        }, RETRY_DELAY_TICKS);

        return true;
    }
}
