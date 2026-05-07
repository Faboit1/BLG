package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
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
 * clears the rules timer, and transitions to the choice-dialog stage.
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
            // Not enough time has passed – silently reject (dialog keeps spamming)
            return true;
        }

        // Accept rules and advance to the choice stage
        plugin.getRulesManager().markAccepted(player);
        plugin.getFlowManager().clearPlayer(player);
        plugin.getFlowManager().startChoiceStage(player);

        return true;
    }
}
