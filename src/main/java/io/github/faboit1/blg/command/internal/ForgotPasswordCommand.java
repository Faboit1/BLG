package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Internal command: {@code /blg_forgot_password}
 *
 * <p>Triggered when a player clicks the "Forgot Password?" button on the
 * login dialog.  Sends a clickable URL in chat pointing to the configured
 * support page (e.g. a Discord server).
 */
public class ForgotPasswordCommand implements CommandExecutor {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();

    private final BLGPlugin plugin;

    public ForgotPasswordCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        String url = plugin.getConfig().getString("dialog.forgot-password-url",
                "https://discord.gg/99dHhqSx4j");
        String body = plugin.cfg("dialog.forgot-password-body");

        // Send the body message
        Component bodyComponent = LEGACY_SERIALIZER.deserialize(body);
        player.sendMessage(bodyComponent);

        // Send the clickable URL on a separate line
        Component urlComponent = Component.text(url)
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url));
        player.sendMessage(urlComponent);

        return true;
    }
}
