package fr.faboit.blg.command;

import fr.faboit.blg.BLGPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /openlogin – directly opens the Login dialog.
 */
public final class OpenLoginCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public OpenLoginCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(colorize(plugin.getConfigManager().msg("player-only")));
            return true;
        }

        if (!player.hasPermission("blg.use")) {
            player.sendMessage(colorize(
                    plugin.getConfigManager().prefix() +
                    plugin.getConfigManager().msg("no-permission")));
            return true;
        }

        if (plugin.getDialogManager().hasOpenDialog(player)) {
            player.sendMessage(colorize(
                    plugin.getConfigManager().prefix() +
                    plugin.getConfigManager().msg("already-open")));
            return true;
        }

        plugin.getDialogManager().openLogin(player);
        return true;
    }

    private static net.kyori.adventure.text.Component colorize(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }
}
