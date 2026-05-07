package io.github.faboit1.blg.command;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * {@code /updaterules} – reloads {@code rules.txt}, backs up the old version,
 * and notifies staff whether the rules changed.
 *
 * <p>Requires the {@code blg.admin} permission.
 */
public class UpdateRulesCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public UpdateRulesCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!sender.hasPermission("blg.admin")) {
            sender.sendMessage(plugin.msg("no-permission-others"));
            return true;
        }

        boolean changed = plugin.getRulesManager().reloadRules();
        if (changed) {
            sender.sendMessage(plugin.cfg("messages.prefix")
                    + "§aRules reloaded. §eContent changed – all players will need to re-accept on next join.");
        } else {
            sender.sendMessage(plugin.cfg("messages.prefix")
                    + "§aRules reloaded. §7No changes detected.");
        }
        return true;
    }
}
