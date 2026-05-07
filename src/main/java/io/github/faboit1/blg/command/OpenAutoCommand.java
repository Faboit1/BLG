package io.github.faboit1.blg.command;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.Bukkit;
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
 *   <li>If a target player is supplied and the sender has permission → force
 *       open the login dialog for that player.</li>
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
        if (args.length > 1) {
            sender.sendMessage(plugin.msg("openauto-usage"));
            return true;
        }

        if (args.length == 1) {
            if (!sender.hasPermission("blg.openauto.others")) {
                sender.sendMessage(plugin.msg("no-permission-others"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.msg("player-not-found"));
                return true;
            }

            plugin.getDialogManager().openLoginDialog(target);
            sender.sendMessage(plugin.cfg("messages.force-opened-login")
                    .replace("%player%", target.getName()));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("openauto-usage"));
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
