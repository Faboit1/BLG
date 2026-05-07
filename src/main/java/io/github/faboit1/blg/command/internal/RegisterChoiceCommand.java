package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Internal command: {@code /blg_register_choice}
 *
 * <p>Triggered when an unregistered player clicks the "Register" button on the
 * register-stub dialog.  Stops the choice-dialog spam task and opens the actual
 * register dialog (the one with the password input fields).
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

        plugin.getFlowManager().stopFlow(player);
        plugin.getDialogManager().openRegisterDialog(player);
        return true;
    }
}
