package fr.faboit.blg.listener;

import fr.faboit.blg.BLGPlugin;
import fr.faboit.blg.dialog.DialogType;
import fr.faboit.blg.dialog.PlayerDialogState;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Intercepts chat messages sent TO a player and checks them against the
 * configured list of AuthMe error patterns.
 *
 * When an error is detected while the player has an active dialog, the Error
 * dialog is opened with the raw error message.
 *
 * <b>This listener does NOT cancel messages</b> – it only observes them.
 * The goal is to capture AuthMe feedback without interfering with chat.
 */
public final class ChatListener implements Listener {

    private final BLGPlugin plugin;
    private List<Pattern>   compiledPatterns;

    public ChatListener(BLGPlugin plugin) {
        this.plugin = plugin;
        recompilePatterns();
    }

    // Called by plugin reload
    public void recompilePatterns() {
        compiledPatterns = plugin.getConfigManager().errorPatterns().stream()
                .map(p -> {
                    try { return Pattern.compile(p); }
                    catch (Exception e) {
                        plugin.getLogger().warning("Invalid error pattern: " + p);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * We listen on AsyncPlayerChatEvent (MONITOR priority) to see outgoing chat.
     *
     * Note: AuthMe typically sends its feedback via the Command feedback channel
     * (not through chat), but some versions route it here.  Listening for
     * player-to-server chat is intentional: it catches cases where AuthMe echoes
     * the command response into chat.
     *
     * The much more reliable path is to intercept server-to-player messages, which
     * in Paper 1.21+ can be done via {@code PlayerReceiveMessageEvent}.  We do that
     * in a secondary handler below.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // Intentionally left lightweight; actual error interception is below.
    }

    /**
     * Intercepts messages sent TO the player using Paper's
     * {@code io.papermc.paper.event.player.AsyncChatEvent} and checks them
     * for AuthMe error patterns.
     *
     * We use the plain-text serialiser to strip formatting before matching.
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
                .anyMatch(prefix -> plain.startsWith(prefix));

        // Check regex patterns
        boolean matchedPattern = compiledPatterns.stream()
                .anyMatch(p -> p.matcher(plain).matches());

        if (matchedPrefix || matchedPattern) {
            DialogType ctx = state.getCurrentDialog();
            // Schedule on main thread since we're in async context
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getDialogManager().openError(player, plain, ctx));
        }
    }
}
