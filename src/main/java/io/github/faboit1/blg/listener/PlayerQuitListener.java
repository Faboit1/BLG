package io.github.faboit1.blg.listener;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cleans up per-player flow state (spam tasks, timers) when a player
 * disconnects to prevent memory leaks and orphaned scheduler tasks.
 */
public class PlayerQuitListener implements Listener {

    private final BLGPlugin plugin;

    public PlayerQuitListener(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getFlowManager().clearPlayer(event.getPlayer());
    }
}
