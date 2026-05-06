package fr.faboit.blg.dialog;

import fr.faboit.blg.BLGPlugin;
import fr.faboit.blg.dialog.gui.AnvilInputGUI;
import fr.faboit.blg.dialog.impl.ConfirmDialog;
import fr.faboit.blg.dialog.impl.ErrorDialog;
import fr.faboit.blg.dialog.impl.LoginDialog;
import fr.faboit.blg.dialog.impl.RegisterDialog;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central orchestrator for all dialogs.
 *
 * Responsibilities:
 * <ul>
 *   <li>Maintain per-player {@link PlayerDialogState}</li>
 *   <li>Open/close individual dialog types</li>
 *   <li>Provide the shared {@link AnvilInputGUI} instance</li>
 *   <li>Play config-driven sounds</li>
 * </ul>
 */
public final class DialogManager {

    private final BLGPlugin plugin;

    // Dialog implementations
    private final LoginDialog    loginDialog;
    private final RegisterDialog registerDialog;
    private final ConfirmDialog  confirmDialog;
    private final ErrorDialog    errorDialog;
    private final AnvilInputGUI  anvilInput;

    /** Per-player dialog state. */
    private final Map<UUID, PlayerDialogState> states = new HashMap<>();

    /** Maps player UUID to the Inventory currently shown as their "dialog". */
    private final Map<UUID, Inventory> trackedInventories = new HashMap<>();

    public DialogManager(BLGPlugin plugin) {
        this.plugin         = plugin;
        this.loginDialog    = new LoginDialog(plugin);
        this.registerDialog = new RegisterDialog(plugin);
        this.confirmDialog  = new ConfirmDialog(plugin);
        this.errorDialog    = new ErrorDialog(plugin);
        this.anvilInput     = new AnvilInputGUI(plugin);
    }

    // ── State management ──────────────────────────────────────────────────────

    /** Get (or lazily create) the dialog state for a player. */
    public PlayerDialogState getState(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> new PlayerDialogState());
    }

    /** @return true if the player currently has any dialog open. */
    public boolean hasOpenDialog(Player player) {
        PlayerDialogState state = states.get(player.getUniqueId());
        return state != null && state.isInDialog();
    }

    /** Remove a player's state completely (e.g. on logout). */
    public void removeState(Player player) {
        PlayerDialogState state = states.remove(player.getUniqueId());
        if (state != null) state.clearCredentials();
        trackedInventories.remove(player.getUniqueId());
        plugin.getCooldownManager().clearConfirmCooldown(player.getUniqueId());
        anvilInput.clearSession(player.getUniqueId());
    }

    /** Called when a dialog inventory is closed (but the player did not actively cancel). */
    public void onDialogClosed(Player player) {
        PlayerDialogState state = states.get(player.getUniqueId());
        if (state == null) return;
        state.clearCredentials();
        state.setCurrentDialog(DialogType.NONE);
        trackedInventories.remove(player.getUniqueId());
        plugin.getCooldownManager().clearConfirmCooldown(player.getUniqueId());
    }

    /** Close all open dialogs (used on plugin disable). */
    public void closeAllDialogs() {
        // Close inventories for all online players that have dialogs open
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> trackedInventories.containsKey(p.getUniqueId()))
                .forEach(Player::closeInventory);
        states.values().forEach(PlayerDialogState::clearCredentials);
        states.clear();
        trackedInventories.clear();
        plugin.getCooldownManager().clearAll();
    }

    // ── Inventory tracking ────────────────────────────────────────────────────

    /** Register the most recently opened dialog inventory for a player. */
    public void trackInventory(Player player, Inventory inv) {
        trackedInventories.put(player.getUniqueId(), inv);
    }

    /** @return the inventory currently tracked as this player's dialog, or null. */
    public Inventory getTrackedInventory(Player player) {
        return trackedInventories.get(player.getUniqueId());
    }

    /**
     * @return true if {@code inv} is the dialog inventory currently tracked
     *         for {@code player}.
     */
    public boolean isTracked(Player player, Inventory inv) {
        return inv != null && inv.equals(trackedInventories.get(player.getUniqueId()));
    }

    // ── Dialog openers ────────────────────────────────────────────────────────

    /** Open the login dialog. */
    public void openLogin(Player player) {
        loginDialog.open(player);
    }

    /** Open the register dialog without an error. */
    public void openRegister(Player player) {
        registerDialog.open(player);
    }

    /** Re-open the register dialog with an error message. */
    public void openRegisterWithError(Player player, String error) {
        registerDialog.open(player, error);
    }

    /** Open the confirm-registration dialog. */
    public void openConfirm(Player player) {
        confirmDialog.open(player);
    }

    /** Open the error dialog. */
    public void openError(Player player, String message, DialogType context) {
        errorDialog.open(player, message, context);
    }

    // ── Anvil input ───────────────────────────────────────────────────────────

    /** Open an anvil text input for the given config key. */
    public void openAnvilInput(Player player, String configKey,
                               java.util.function.Consumer<String> onConfirm,
                               Runnable onCancel) {
        anvilInput.open(player, configKey, onConfirm, onCancel);
    }

    public AnvilInputGUI getAnvilInput() {
        return anvilInput;
    }

    // ── Sound utility (static so dialogs can call it too) ─────────────────────

    /**
     * Play a config-driven sound to the player.
     *
     * @param plugin the plugin instance
     * @param player the target player
     * @param key    the key under {@code sounds.*} in config.yml
     */
    public static void playSound(BLGPlugin plugin, Player player, String key) {
        var cfg = plugin.getConfigManager();
        if (!cfg.soundsEnabled()) return;
        Sound sound = cfg.sound(key);
        if (sound == null) return;
        player.playSound(player.getLocation(), sound, cfg.soundVolume(key), cfg.soundPitch(key));
    }
}
