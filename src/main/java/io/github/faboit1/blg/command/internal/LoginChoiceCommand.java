package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
import io.github.faboit1.blg.flow.PendingAction;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Internal command: {@code /blg_login_choice}
 *
 * <p>Triggered when a registered player clicks the "Login" button on the
 * login-stub dialog.  If the player has not yet accepted the current rules,
 * the rules dialog is shown first and the login intent is stored so that it
 * can be resumed automatically once the player accepts.  Otherwise the actual
 * login dialog (with the password input field) is opened immediately.
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

        if (plugin.getRulesManager().needsToAccept(player)) {
            plugin.getFlowManager().setPendingAction(player, PendingAction.LOGIN);
            plugin.getFlowManager().startRulesStage(player);
            return true;
        }

        plugin.getFlowManager().stopFlow(player);
        plugin.getDialogManager().openLoginDialog(player);
        return true;
    }
}
