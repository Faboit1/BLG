package fr.faboit.blg.cooldown;

import fr.faboit.blg.BLGPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks per-player cooldowns.
 *
 * Currently used to implement the 5-second lock on the Confirm button
 * inside the confirmation dialog.
 */
public final class CooldownManager {

    private final BLGPlugin plugin;

    /** UUID → time (ms) when a dialog was opened for the confirm cooldown. */
    private final Map<UUID, Long> confirmStartTimes = new HashMap<>();

    public CooldownManager(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Confirm cooldown ──────────────────────────────────────────────────────

    /** Record the moment a player opens the Confirm dialog. */
    public void startConfirmCooldown(UUID uuid) {
        confirmStartTimes.put(uuid, System.currentTimeMillis());
    }

    /** Remove the cooldown entry (e.g. dialog closed). */
    public void clearConfirmCooldown(UUID uuid) {
        confirmStartTimes.remove(uuid);
    }

    /**
     * Returns the number of seconds that still need to pass before the
     * Confirm button is clickable, or {@code 0} if the cooldown has elapsed.
     */
    public int remainingConfirmSeconds(UUID uuid) {
        Long start = confirmStartTimes.get(uuid);
        if (start == null) return 0;
        int required = plugin.getConfigManager().confirmCooldownSeconds();
        long elapsed = (System.currentTimeMillis() - start) / 1000L;
        int remaining = (int) (required - elapsed);
        return Math.max(0, remaining);
    }

    /** @return true if the cooldown has expired and the button is usable. */
    public boolean canConfirm(UUID uuid) {
        return remainingConfirmSeconds(uuid) == 0;
    }

    /** Remove all data (used on plugin disable). */
    public void clearAll() {
        confirmStartTimes.clear();
    }
}
