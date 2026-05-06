package io.github.faboit1.blg.command;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /openregister} – opens the register dialog for the executing player.
 */
public class OpenRegisterCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public OpenRegisterCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        plugin.getDialogManager().openRegisterDialog(player);
        return true;
    }
}
