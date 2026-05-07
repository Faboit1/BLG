package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Internal command: {@code /blg_login_choice}
 *
 * <p>Triggered when a registered player clicks the "Login" button on the
 * login-stub dialog.  Stops the choice-dialog spam task and opens the actual
 * login dialog (the one with the password input field).
 */
public class LoginChoiceCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public LoginChoiceCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        plugin.getFlowManager().stopFlow(player);
        plugin.getDialogManager().openLoginDialog(player);
        return true;
    }
}
