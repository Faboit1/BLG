package fr.faboit.blg.listener;

import fr.faboit.blg.BLGPlugin;
import fr.faboit.blg.dialog.DialogManager;
import fr.faboit.blg.dialog.DialogType;
import fr.faboit.blg.dialog.PlayerDialogState;
import fr.faboit.blg.dialog.impl.ConfirmDialog;
import fr.faboit.blg.dialog.impl.ErrorDialog;
import fr.faboit.blg.dialog.impl.LoginDialog;
import fr.faboit.blg.dialog.impl.RegisterDialog;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Handles all inventory interactions for BLG dialogs.
 *
 * The listener intercepts clicks on the tracked dialog inventories and
 * routes them to the appropriate dialog logic.
 *
 * Anvil events are handled separately inside {@link fr.faboit.blg.dialog.gui.AnvilInputGUI}.
 */
public final class InventoryListener implements Listener {

    private final BLGPlugin plugin;

    public InventoryListener(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Click routing ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        var dm  = plugin.getDialogManager();
        var inv = event.getInventory();

        if (!dm.isTracked(player, inv)) return;

        // Always cancel clicks inside our dialogs to prevent item theft
        event.setCancelled(true);

        // Ignore clicks outside the top inventory (e.g. player's own hotbar)
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != inv) return;

        int slot = event.getSlot();
        PlayerDialogState state = dm.getState(player);

        switch (state.getCurrentDialog()) {
            case LOGIN    -> handleLoginClick(player, dm, state, slot);
            case REGISTER -> handleRegisterClick(player, dm, state, slot);
            case CONFIRM  -> handleConfirmClick(player, dm, state, slot);
            case ERROR    -> handleErrorClick(player, dm, state, slot);
            default       -> { /* no-op */ }
        }

        DialogManager.playSound(plugin, player, "button-click");
    }

    // ── Close handling ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        var dm = plugin.getDialogManager();
        if (!dm.isTracked(player, event.getInventory())) return;

        // If a dialog closed without explicit cancellation, clean up state
        dm.onDialogClosed(player);
        DialogManager.playSound(plugin, player, "dialog-close");
    }

    // ── Login dialog ──────────────────────────────────────────────────────────

    private void handleLoginClick(Player player, DialogManager dm,
                                  PlayerDialogState state, int slot) {
        if (slot == LoginDialog.SLOT_PASSWORD_FIELD) {
            // Open anvil input for password; suspend dialog tracking temporarily
            player.closeInventory(); // close the menu inventory first
            dm.openAnvilInput(player, "login-password",
                    password -> {
                        // back on main thread
                        if (password != null && !password.isBlank()) {
                            state.setPassword(password);
                        }
                        dm.openLogin(player);
                    },
                    () -> dm.openLogin(player));

        } else if (slot == LoginDialog.SLOT_LOGIN_BUTTON) {
            if (!state.hasPassword()) {
                sendMsg(player, "messages.login.password-empty");
                return;
            }
            // Execute login command and close dialog
            String pw = state.getPassword();
            state.clearCredentials();
            dm.onDialogClosed(player);
            player.closeInventory();
            plugin.getAuthMeWrapper().performLogin(player, pw);

        } else if (slot == LoginDialog.SLOT_CANCEL_BUTTON) {
            dm.onDialogClosed(player);
            player.closeInventory();

        } else if (slot == LoginDialog.SLOT_SWITCH_REGISTER) {
            state.clearCredentials();
            dm.openRegister(player);
        }
    }

    // ── Register dialog ───────────────────────────────────────────────────────

    private void handleRegisterClick(Player player, DialogManager dm,
                                     PlayerDialogState state, int slot) {
        if (slot == RegisterDialog.SLOT_PASSWORD_FIELD) {
            player.closeInventory();
            dm.openAnvilInput(player, "register-password",
                    password -> {
                        if (password != null && !password.isBlank()) {
                            state.setPassword(password);
                        }
                        dm.openRegister(player);
                    },
                    () -> dm.openRegister(player));

        } else if (slot == RegisterDialog.SLOT_CONFIRM_FIELD) {
            player.closeInventory();
            dm.openAnvilInput(player, "register-confirm",
                    confirm -> {
                        if (confirm != null && !confirm.isBlank()) {
                            state.setConfirmation(confirm);
                        }
                        dm.openRegister(player);
                    },
                    () -> dm.openRegister(player));

        } else if (slot == RegisterDialog.SLOT_DONE_BUTTON) {
            if (!state.hasPassword() || !state.hasConfirmation()) {
                sendMsg(player, "messages.register.passwords-empty");
                return;
            }
            if (!state.getPassword().equals(state.getConfirmation())) {
                // Re-open register dialog with mismatch error
                String errMsg = plugin.getConfigManager().msg("register.passwords-mismatch");
                state.setPassword("");
                state.setConfirmation("");
                dm.openRegisterWithError(player, errMsg);
                return;
            }
            // Both fields match → open confirmation dialog
            dm.openConfirm(player);

        } else if (slot == RegisterDialog.SLOT_CANCEL_BUTTON) {
            state.clearCredentials();
            dm.onDialogClosed(player);
            player.closeInventory();

        } else if (slot == RegisterDialog.SLOT_SWITCH_LOGIN) {
            state.clearCredentials();
            dm.openLogin(player);
        }
    }

    // ── Confirm dialog ────────────────────────────────────────────────────────

    private void handleConfirmClick(Player player, DialogManager dm,
                                    PlayerDialogState state, int slot) {
        if (slot == ConfirmDialog.SLOT_CONFIRM_BUTTON) {
            int remaining = plugin.getCooldownManager()
                    .remainingConfirmSeconds(player.getUniqueId());

            if (remaining > 0) {
                String waitMsg = plugin.getConfigManager()
                        .msg("confirm.cooldown-wait")
                        .replace("{remaining}", String.valueOf(remaining));
                sendRaw(player, waitMsg);
                DialogManager.playSound(plugin, player, "cooldown-wait");
                return;
            }

            // Cooldown done → execute register
            String pw      = state.getPassword();
            String confirm = state.getConfirmation();
            state.clearCredentials();
            plugin.getCooldownManager().clearConfirmCooldown(player.getUniqueId());
            dm.onDialogClosed(player);
            player.closeInventory();
            plugin.getAuthMeWrapper().performRegister(player, pw, confirm);

        } else if (slot == ConfirmDialog.SLOT_BACK_BUTTON) {
            // Back to register dialog
            dm.openRegister(player);
        }
    }

    // ── Error dialog ──────────────────────────────────────────────────────────

    private void handleErrorClick(Player player, DialogManager dm,
                                  PlayerDialogState state, int slot) {
        DialogType ctx = state.getErrorContext();

        if (slot == ErrorDialog.SLOT_BACK_LOGIN
                && (ctx == DialogType.LOGIN || ctx == DialogType.NONE)) {
            state.setLastError("");
            dm.openLogin(player);

        } else if (slot == ErrorDialog.SLOT_BACK_REGISTER
                && (ctx == DialogType.REGISTER || ctx == DialogType.NONE)) {
            state.setLastError("");
            dm.openRegister(player);

        } else if (slot == ErrorDialog.SLOT_CLOSE) {
            state.clearCredentials();
            state.setLastError("");
            dm.onDialogClosed(player);
            player.closeInventory();

        } else {
            // Fallback: clicking any back button goes to the right screen
            if (slot == ErrorDialog.SLOT_BACK_LOGIN) {
                state.setLastError("");
                dm.openLogin(player);
            } else if (slot == ErrorDialog.SLOT_BACK_REGISTER) {
                state.setLastError("");
                dm.openRegister(player);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendMsg(Player player, String msgPath) {
        String text = plugin.getConfigManager().prefix()
                + plugin.getConfigManager().raw().getString(msgPath, "");
        sendRaw(player, text);
    }

    private void sendRaw(Player player, String text) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
    }
}
