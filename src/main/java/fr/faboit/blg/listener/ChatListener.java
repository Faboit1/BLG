package fr.faboit.blg.listener;

import fr.faboit.blg.BLGPlugin;
import fr.faboit.blg.dialog.DialogType;
import fr.faboit.blg.dialog.PlayerDialogState;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Intercepts chat messages sent FROM the server TO a player and checks them
 * against the configured list of AuthMe error patterns.
 *
 * When an error is detected while the player has an active dialog, the Error
 * dialog is opened with the raw error message.
 *
 * <b>This listener does NOT cancel messages</b> – it only observes them.
 * The goal is to capture AuthMe feedback without interfering with normal chat.
 *
 * Implementation uses Paper's {@code AsyncChatEvent} which fires for all chat
 * messages including server-originated ones in Paper 1.19+.
 */
public final class ChatListener implements Listener {

    private final BLGPlugin plugin;
    private List<Pattern>   compiledPatterns;

    public ChatListener(BLGPlugin plugin) {
        this.plugin = plugin;
        recompilePatterns();
    }

    /** Recompile regex patterns from config (called on plugin reload). */
    public void recompilePatterns() {
        compiledPatterns = plugin.getConfigManager().errorPatterns().stream()
                .map(p -> {
                    try { return Pattern.compile(p); }
                    catch (Exception e) {
                        plugin.getLogger().warning("Invalid error pattern: " + p);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Intercepts messages using Paper's {@code AsyncChatEvent} and checks
     * them for AuthMe error signatures.
     *
     * We use the plain-text serialiser to strip colour formatting before matching.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChatMessageReceived(io.papermc.paper.event.player.AsyncChatEvent event) {
        if (!plugin.getConfigManager().errorDetectionEnabled()) return;

        Player player = event.getPlayer();
        PlayerDialogState state = plugin.getDialogManager().getState(player);
        if (!state.isInDialog()) return;

        String plain = PlainTextComponentSerializer.plainText()
                .serialize(event.message());

        // Check prefix list
        boolean matchedPrefix = plugin.getConfigManager().errorPrefixes().stream()
                .anyMatch(plain::startsWith);

        // Check regex patterns
        boolean matchedPattern = compiledPatterns.stream()
                .anyMatch(p -> p.matcher(plain).matches());

        if (matchedPrefix || matchedPattern) {
            DialogType ctx = state.getCurrentDialog();
            // Schedule on main thread since we're in async context
            Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getDialogManager().openError(player, plain, ctx));
        }
    }
}
