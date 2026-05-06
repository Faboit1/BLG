package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Internal command: {@code /blg_register_submit <password> <confirmPassword>}
 *
 * <p>This command is <strong>never</strong> typed by the player manually.
 * It is the command template bound to the submit button of the register dialog:
 * <pre>
 *   template: "/blg_register_submit %password% %confirmPassword%"
 * </pre>
 * When the player clicks "Register", the Minecraft client substitutes both
 * placeholders with the values the player typed and sends the command to the
 * server.
 *
 * <p>BLG validates that both fields match and then forwards the credential to
 * AuthMe via {@code /register <password> <confirmPassword>}.  BLG never
 * stores or hashes the password itself.
 */
public class RegisterSubmitCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public RegisterSubmitCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true; // should never happen
        }

        if (args.length < 2) {
            // One or both fields were empty – dialog submitted incomplete
            player.sendMessage(plugin.msg("internal-command-blocked"));
            return true;
        }

        String password        = args[0];
        String confirmPassword = args[1];

        // Basic client-side double-entry validation
        if (!password.equals(confirmPassword)) {
            player.sendMessage(plugin.msg("password-mismatch"));
            // Re-open the register dialog so the player can try again
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.getDialogManager().openRegisterDialog(player));
            return true;
        }

        // Forward to AuthMe: /register <password> <confirmPassword>
        player.sendMessage(plugin.msg("register-forwarded"));
        player.performCommand("register " + password + " " + confirmPassword);

        return true;
    }
}
