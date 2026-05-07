package io.github.faboit1.blg.command;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * {@code /blgreload} – reloads {@code config.yml} and {@code rules.txt}.
 *
 * <p>Requires the {@code blg.admin} permission.
 */
public class ReloadCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public ReloadCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!sender.hasPermission("blg.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        plugin.reloadConfig();
        plugin.getRulesManager().reloadRules();

        sender.sendMessage(plugin.cfg("messages.prefix")
                + "§aBetterLoginGui configuration reloaded.");
        return true;
    }
}
