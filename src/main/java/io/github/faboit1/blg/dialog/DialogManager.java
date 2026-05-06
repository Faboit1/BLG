package io.github.faboit1.blg.dialog;

import io.github.faboit1.blg.BLGPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Level;

/**
 * Builds and displays Minecraft Dialog API screens to players.
 *
 * <h2>How the Dialog API works</h2>
 * The Minecraft Dialog API (introduced in 1.21.5 / 25w17a) lets servers open
 * a rich, form-like GUI in the client without requiring a resource-pack or
 * mod.  Paper exposes this through {@link Dialog#create} and
 * {@link Player#showDialog}.
 *
 * <p>When a player fills in text inputs and presses a submit button the
 * client runs the command template stored in the button's action, substituting
 * every {@code $(key)} placeholder with the value of the matching input field.
 * The server receives a perfectly ordinary command, so no special packet
 * listener is required to handle the response.
 *
 * <h2>Dialog structure</h2>
 * Each dialog is built from:
 * <ul>
 *   <li>{@link DialogBase} – title, body text, and input fields</li>
 *   <li>{@link DialogType} – button layout (we use {@code multiAction})</li>
 *   <li>{@link ActionButton} – labelled button with a command template action</li>
 *   <li>{@link DialogAction#commandTemplate} – {@code $(key)} substitution syntax</li>
 * </ul>
 */
public class DialogManager {

    private static final int SUBMIT_BUTTON_WIDTH = 200;
    private static final int CANCEL_BUTTON_WIDTH = 100;

    private final BLGPlugin plugin;

    /** Whether the Paper Dialog API is available on this server build. */
    private final boolean dialogApiAvailable;

    public DialogManager(BLGPlugin plugin) {
        this.plugin = plugin;
        this.dialogApiAvailable = probeDialogApi();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Opens the login dialog for the given player.
     *
     * <p>When the player clicks "Login" the client runs:
     * <pre>/blg_login_submit $(password)</pre>
     */
    public void openLoginDialog(Player player) {
        String title   = plugin.cfg("dialog.login-title");
        String body    = plugin.cfg("dialog.login-body");
        String button  = plugin.cfg("dialog.login-button");
        String cancel  = plugin.cfg("dialog.cancel-button");
        String pwLabel = plugin.cfg("dialog.password-label");

        if (dialogApiAvailable) {
            try {
                Dialog dialog = Dialog.create(factory -> factory.empty()
                        .base(DialogBase.builder(Component.text(stripColor(title)))
                                .body(List.of(
                                        DialogBody.plainMessage(Component.text(stripColor(body)))))
                                .inputs(List.of(
                                        DialogInput.text("password",
                                                        Component.text(stripColor(pwLabel)))
                                                .labelVisible(true)
                                                .maxLength(100)
                                                .build()))
                                .canCloseWithEscape(false)
                                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                                .build())
                        .type(DialogType.multiAction(List.of(
                                        ActionButton.create(
                                                Component.text(stripColor(button)),
                                                null, SUBMIT_BUTTON_WIDTH,
                                                DialogAction.commandTemplate(
                                                        "/blg_login_submit $(password)"))))
                                .exitAction(ActionButton.create(
                                        Component.text(stripColor(cancel)),
                                        null, CANCEL_BUTTON_WIDTH, null))
                                .build()));
                player.showDialog(dialog);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to open login dialog for " + player.getName()
                        + ": " + e.getMessage(), e);
                fallbackChat(player, "login-prompt");
            }
        } else {
            fallbackChat(player, "login-prompt");
        }
    }

    /**
     * Opens the register dialog for the given player.
     *
     * <p>When the player clicks "Register" the client runs:
     * <pre>/blg_register_submit $(password) $(confirmPassword)</pre>
     */
    public void openRegisterDialog(Player player) {
        String title        = plugin.cfg("dialog.register-title");
        String body         = plugin.cfg("dialog.register-body");
        String button       = plugin.cfg("dialog.register-button");
        String cancel       = plugin.cfg("dialog.cancel-button");
        String pwLabel      = plugin.cfg("dialog.password-label");
        String confirmLabel = plugin.cfg("dialog.confirm-password-label");

        if (dialogApiAvailable) {
            try {
                Dialog dialog = Dialog.create(factory -> factory.empty()
                        .base(DialogBase.builder(Component.text(stripColor(title)))
                                .body(List.of(
                                        DialogBody.plainMessage(Component.text(stripColor(body)))))
                                .inputs(List.of(
                                        DialogInput.text("password",
                                                        Component.text(stripColor(pwLabel)))
                                                .labelVisible(true)
                                                .maxLength(100)
                                                .build(),
                                        DialogInput.text("confirmPassword",
                                                        Component.text(stripColor(confirmLabel)))
                                                .labelVisible(true)
                                                .maxLength(100)
                                                .build()))
                                .canCloseWithEscape(false)
                                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                                .build())
                        .type(DialogType.multiAction(List.of(
                                        ActionButton.create(
                                                Component.text(stripColor(button)),
                                                null, SUBMIT_BUTTON_WIDTH,
                                                DialogAction.commandTemplate(
                                                        "/blg_register_submit $(password) $(confirmPassword)"))))
                                .exitAction(ActionButton.create(
                                        Component.text(stripColor(cancel)),
                                        null, CANCEL_BUTTON_WIDTH, null))
                                .build()));
                player.showDialog(dialog);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to open register dialog for " + player.getName()
                        + ": " + e.getMessage(), e);
                fallbackChat(player, "register-prompt");
            }
        } else {
            fallbackChat(player, "register-prompt");
        }
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    /**
     * Checks at startup whether the Paper Dialog API is available on this
     * server build, so we can fall back gracefully on older builds.
     */
    private boolean probeDialogApi() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            plugin.getLogger().info("Paper Dialog API detected – dialog UI enabled.");
            return true;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning(
                    "Paper Dialog API not found on this server build. " +
                    "Chat-based prompts will be used instead. " +
                    "Upgrade to a recent Paper 1.21.5+ build to enable dialog UI.");
            return false;
        }
    }

    /** Strips Bukkit legacy colour codes from a string. */
    private String stripColor(String text) {
        return org.bukkit.ChatColor.stripColor(text);
    }

    /** Sends a friendly chat message when dialogs are unavailable. */
    private void fallbackChat(Player player, String messageKey) {
        player.sendMessage(plugin.msg(messageKey));
    }
}
