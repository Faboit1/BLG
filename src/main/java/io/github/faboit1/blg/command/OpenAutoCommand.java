package io.github.faboit1.blg.command;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /openauto} – queries AuthMe to decide which dialog to open.
 *
 * <ul>
 *   <li>If the player is already registered in AuthMe → login dialog.</li>
 *   <li>If the player is NOT registered (or AuthMe is unavailable) → register
 *       dialog.</li>
 * </ul>
 */
public class OpenAutoCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public OpenAutoCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!plugin.getAuthMeHook().isHooked()) {
            player.sendMessage(plugin.msg("authme-unavailable"));
            plugin.getDialogManager().openLoginDialog(player);
            return true;
        }

        player.sendMessage(plugin.msg("auto-prompt"));

        if (plugin.getAuthMeHook().isRegistered(player)) {
            plugin.getDialogManager().openLoginDialog(player);
        } else {
            plugin.getDialogManager().openRegisterDialog(player);
        }

        return true;
    }
}
