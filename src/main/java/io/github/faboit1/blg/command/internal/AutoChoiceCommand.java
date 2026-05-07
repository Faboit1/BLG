package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Internal command: {@code /blg_auto_choice}
 *
 * <p>Triggered when a player clicks the unified on-join "Register/Login"
 * button. Stops the join-choice spam task and opens the actual auth dialog
 * chosen automatically from AuthMe state.
 */
public class AutoChoiceCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public AutoChoiceCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        plugin.getFlowManager().stopFlow(player);
        plugin.getDialogManager().openAutoAuthDialog(player);
        return true;
    }
}
