package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
import io.github.faboit1.blg.flow.PendingAction;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Internal command: {@code /blg_register_choice}
 *
 * <p>Triggered when an unregistered player clicks the "Register" button on the
 * register-stub dialog.  If the player has not yet accepted the current rules,
 * the rules dialog is shown first and the register intent is stored so that it
 * can be resumed automatically once the player accepts.  Otherwise the actual
 * register dialog (with the password input fields) is opened immediately.
 */
public class RegisterChoiceCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public RegisterChoiceCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (plugin.getRulesManager().needsToAccept(player)) {
            plugin.getFlowManager().setPendingAction(player, PendingAction.REGISTER);
            plugin.getFlowManager().startRulesStage(player);
            return true;
        }

        plugin.getFlowManager().stopFlow(player);
        plugin.getDialogManager().openRegisterDialog(player);
        return true;
    }
}
