package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
import io.github.faboit1.blg.flow.PendingAction;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Internal command: {@code /blg_rules_accept}
 *
 * <p>Triggered when the player clicks the "Accept" button on the rules dialog.
 * The server checks that the mandatory wait time has elapsed; if not, the
 * spam task will continue showing the dialog with the remaining countdown and
 * the accept is silently ignored.
 *
 * <p>On success: marks the player as having accepted the current rules,
 * clears the rules timer, and either opens the login or register dialog
 * directly (when the player had already clicked one of those choice buttons
 * before rules were shown) or transitions to the choice-dialog stage
 * (when rules were shown for another reason).
 */
public class RulesAcceptCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public RulesAcceptCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (!plugin.getFlowManager().canActOnRules(player)) {
            // Not enough time has passed – re-send the dialog immediately so the player
            // is not left without a dialog until the unlock task fires.
            int page = plugin.getFlowManager().getRulesPage(player);
            plugin.getDialogManager().openRulesDialog(player, page, false);
            return true;
        }

        // Record acceptance
        plugin.getRulesManager().markAccepted(player);

        // Retrieve (and clear) any pending action before clearing player state
        PendingAction pending = plugin.getFlowManager().getPendingAction(player);
        plugin.getFlowManager().clearPlayer(player);

        if (pending == PendingAction.LOGIN) {
            plugin.getDialogManager().openLoginDialog(player);
        } else if (pending == PendingAction.REGISTER) {
            plugin.getDialogManager().openRegisterDialog(player);
        } else {
            // No pending action — fall back to showing the choice dialog
            plugin.getFlowManager().startChoiceStage(player);
        }

        return true;
    }
}
