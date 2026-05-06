package fr.faboit.blg.command;

import fr.faboit.blg.BLGPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /openauto – queries AuthMe to decide whether to show Login or Register.
 *
 * Flow:
 * <ol>
 *   <li>Check AuthMe (async if configured)</li>
 *   <li>If registered → openLogin(player)</li>
 *   <li>If not registered → openRegister(player)</li>
 * </ol>
 */
public final class OpenAutoCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public OpenAutoCommand(BLGPlugin plugin) {
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

        if (!plugin.getAuthMeWrapper().isAuthMePresent()) {
            player.sendMessage(colorize(
                    plugin.getConfigManager().prefix() +
                    plugin.getConfigManager().msg("authme-not-found")));
            return true;
        }

        // Async check then open appropriate dialog on main thread
        plugin.getAuthMeWrapper().isRegistered(player, registered -> {
            if (registered) {
                plugin.getDialogManager().openLogin(player);
            } else {
                plugin.getDialogManager().openRegister(player);
            }
        });

        return true;
    }

    private static net.kyori.adventure.text.Component colorize(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }
}
