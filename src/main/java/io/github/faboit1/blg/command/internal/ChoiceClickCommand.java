package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Internal command: {@code /blg_choice_click}
 *
 * <p>Triggered when the player clicks the "Login / Register" button on the
 * welcome choice dialog.  Stops the choice-dialog spam task and opens the
 * correct auth dialog (login or register) based on the player's AuthMe status.
 */
public class ChoiceClickCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public ChoiceClickCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        // Stop the choice-dialog spam task
        plugin.getFlowManager().stopFlow(player);

        // Determine which auth dialog to open
        boolean isRegistered = plugin.getAuthMeHook().isHooked()
                && plugin.getAuthMeHook().isRegistered(player);

        if (isRegistered) {
            plugin.getDialogManager().openLoginDialog(player);
        } else {
            plugin.getDialogManager().openRegisterDialog(player);
        }

        return true;
    }
}
