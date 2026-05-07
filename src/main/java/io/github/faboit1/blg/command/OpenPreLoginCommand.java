package io.github.faboit1.blg.command;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /openprelogin [player]} – opens the pre-login choice dialog.
 *
 * <p>The pre-login dialog shows a single "Open Login" or "Open Register" button
 * (chosen from AuthMe registration state).  Clicking it opens the actual login
 * or register form.
 *
 * <p>When a target player name is supplied and the sender has the
 * {@code blg.openprelogin.others} permission, the dialog is opened for that
 * player instead.
 */
public class OpenPreLoginCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public OpenPreLoginCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length > 1) {
            sender.sendMessage(plugin.msg("openprelogin-usage"));
            return true;
        }

        if (args.length == 1) {
            if (!sender.hasPermission("blg.openprelogin.others")) {
                sender.sendMessage(plugin.msg("no-permission-others"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.msg("player-not-found"));
                return true;
            }

            openChoiceFor(target);
            sender.sendMessage(plugin.msg("force-opened-prelogin")
                    .replace("%player%", target.getName()));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("openprelogin-console-usage"));
            return true;
        }

        openChoiceFor(player);
        return true;
    }

    private void openChoiceFor(Player player) {
        boolean registered = plugin.getAuthMeHook().isHooked()
                && plugin.getAuthMeHook().isRegistered(player);
        if (registered) {
            plugin.getDialogManager().openLoginChoiceDialog(player);
        } else {
            plugin.getDialogManager().openRegisterChoiceDialog(player);
        }
    }
}