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
 *   template: "/blg_login_submit %password%"
 * </pre>
 * When the player clicks "Login", the Minecraft client substitutes
 * {@code %password%} with whatever the player typed in the password field and
 * sends the resulting command to the server.
 *
 * <p>The command forwards the credential to AuthMe by dispatching
 * {@code /login <password>}.  BLG itself never reads or stores the password.
 */
public class LoginSubmitCommand implements CommandExecutor {

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

        // Reconstruct the full password from all args.  The dialog command
        // template substitutes %password% verbatim; if the player's password
        // contains spaces the client sends them as separate args.  Joining
        // restores the original value.
        String password = String.join(" ", args);
        player.sendMessage(plugin.msg("login-forwarded"));
        player.performCommand("login " + password);

        return true;
    }
}
