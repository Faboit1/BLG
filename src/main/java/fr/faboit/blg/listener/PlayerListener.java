package fr.faboit.blg.listener;

import fr.faboit.blg.BLGPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player lifecycle events to keep dialog state consistent.
 *
 * <ul>
 *   <li>Player quits → clear all state + close open inventory.</li>
 *   <li>Player changes world → optionally close dialog (configurable).</li>
 * </ul>
 */
public final class PlayerListener implements Listener {

    private final BLGPlugin plugin;

    public PlayerListener(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!plugin.getConfigManager().closeOnDisconnect()) return;
        Player player = event.getPlayer();
        plugin.getDialogManager().removeState(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!plugin.getConfigManager().closeOnWorldChange()) return;
        Player player = event.getPlayer();
        if (plugin.getDialogManager().hasOpenDialog(player)) {
            plugin.getDialogManager().removeState(player);
            player.closeInventory();
        }
    }
}
