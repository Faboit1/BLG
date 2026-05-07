package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Internal command: {@code /blg_rules_leave}
 *
 * <p>Triggered when the player clicks the "Leave Server" button on the rules
 * dialog.  The server checks that the mandatory wait time has elapsed; if not,
 * the action is silently ignored and the spam continues.
 *
 * <p>On success: clears the player's flow state and kicks them from the server.
 */
public class RulesLeaveCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public RulesLeaveCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (!plugin.getFlowManager().canActOnRules(player)) {
            // Not enough time has passed – silently reject
            return true;
        }

        plugin.getFlowManager().clearPlayer(player);
        player.kickPlayer(plugin.cfg("messages.rules-leave-kick"));

        return true;
    }
}
