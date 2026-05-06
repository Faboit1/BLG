package fr.faboit.blg.command;

import fr.faboit.blg.BLGPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /blgreload – reloads config and resets all active dialogs.
 */
public final class BLGReloadCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public BLGReloadCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!sender.hasPermission("blg.admin")) {
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(plugin.getConfigManager().prefix() +
                                 plugin.getConfigManager().msg("no-permission")));
            return true;
        }

        plugin.reloadPlugin();

        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(plugin.getConfigManager().prefix() +
                             plugin.getConfigManager().msg("reload-success")));
        return true;
    }
}
