package io.github.faboit1.blg.dialog;

import io.github.faboit1.blg.BLGPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Builds and displays Minecraft Dialog API screens to players.
 *
 * <h2>How the Dialog API works</h2>
 * The Minecraft Dialog API (introduced in 1.21.5 / 25w17a) lets servers open
 * a rich, form-like GUI in the client without requiring a resource-pack or
 * mod.  Paper exposes this through {@code Dialog.create} and
 * {@code Player#showDialog}.
 *
 * <p>When a player fills in text inputs and presses a submit button the
 * client runs the command template stored in the button's action, substituting
 * every {@code $(key)} placeholder with the value of the matching input field.
 * The server receives a perfectly ordinary command, so no special packet
 * listener is required to handle the response.
 *
 * <h2>Compile-time independence</h2>
 * The Paper Dialog API packages ({@code io.papermc.paper.dialog.*} and
 * {@code io.papermc.paper.registry.data.dialog.*}) may not be present in all
 * paper-api snapshot builds that the CI resolves.  All Dialog-specific classes
 * are therefore loaded at runtime via reflection so that the plugin compiles
 * cleanly regardless of which snapshot is available.  On Paper 1.21.5+ servers
 * the API is present and reflection succeeds; on older builds we fall back to
 * a chat-based prompt.
 */
public class DialogManager {

    private static final int SUBMIT_BUTTON_WIDTH = 200;
    private static final int CANCEL_BUTTON_WIDTH = 100;
    private static final int MAX_INPUT_LENGTH = 100;

    private final BLGPlugin plugin;

    /** Whether the Paper Dialog API is available on this server build. */
    private final boolean dialogApiAvailable;

    /**
     * Cached reference to {@code net.kyori.adventure.dialog.DialogLike}, loaded
     * lazily when the Dialog API is first confirmed available.  {@code null} if
     * the class is not on the classpath.
     */
    private static Class<?> dialogLikeClass;

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
                Object dialog = buildDialog(
                        title, body,
                        new String[]{"password"},
                        new String[]{pwLabel},
                        "/blg_login_submit $(password)",
                        button, cancel);
                showDialogReflective(player, dialog);
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
                Object dialog = buildDialog(
                        title, body,
                        new String[]{"password", "confirmPassword"},
                        new String[]{pwLabel, confirmLabel},
                        "/blg_register_submit $(password) $(confirmPassword)",
                        button, cancel);
                showDialogReflective(player, dialog);
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
    // Dialog building via reflection
    // (All io.papermc.paper.dialog.* classes are loaded dynamically so that
    //  the plugin compiles against paper-api snapshots that may not yet
    //  include the Dialog API packages.)
    // -----------------------------------------------------------------------

    /**
     * Constructs a dialog using Paper's Dialog API loaded entirely via
     * reflection so that the plugin compiles against any paper-api snapshot,
     * even those that do not yet include the Dialog API packages.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object buildDialog(String title, String bodyText,
                                   String[] inputKeys, String[] inputLabels,
                                   String commandTemplate,
                                   String submitLabel, String cancelLabel)
            throws Exception {

        // ----- Load Paper Dialog API classes via reflection -----
        Class<?> dialogBaseClass   = Class.forName("io.papermc.paper.registry.data.dialog.DialogBase");
        Class<?> dialogBodyClass   = Class.forName("io.papermc.paper.registry.data.dialog.body.DialogBody");
        Class<?> dialogInputClass  = Class.forName("io.papermc.paper.registry.data.dialog.input.DialogInput");
        Class<?> dialogTypeClass   = Class.forName("io.papermc.paper.registry.data.dialog.type.DialogType");
        Class<?> actionButtonClass = Class.forName("io.papermc.paper.registry.data.dialog.ActionButton");
        Class<?> dialogActionClass = Class.forName("io.papermc.paper.registry.data.dialog.action.DialogAction");
        Class<?> afterActionEnum   = Class.forName("io.papermc.paper.registry.data.dialog.DialogBase$DialogAfterAction");
        Class<?> providerClass     = Class.forName("io.papermc.paper.registry.data.InlinedRegistryBuilderProvider");

        // ----- Build DialogBase -----
        Object baseBuilder = dialogBaseClass
                .getMethod("builder", Component.class)
                .invoke(null, Component.text(strip(title)));

        Object bodyEntry = dialogBodyClass
                .getMethod("plainMessage", Component.class)
                .invoke(null, Component.text(strip(bodyText)));
        baseBuilder = call(baseBuilder, "body", List.class, List.of(bodyEntry));
        baseBuilder = call(baseBuilder, "canCloseWithEscape", boolean.class, false);

        Object closeAction = Enum.valueOf((Class<Enum>) afterActionEnum, "CLOSE");
        baseBuilder = baseBuilder.getClass()
                .getMethod("afterAction", afterActionEnum)
                .invoke(baseBuilder, closeAction);

        // ----- Build text inputs -----
        List<Object> inputs = new ArrayList<>(inputKeys.length);
        for (int i = 0; i < inputKeys.length; i++) {
            Object inputBuilder = dialogInputClass
                    .getMethod("text", String.class, Component.class)
                    .invoke(null, inputKeys[i], Component.text(strip(inputLabels[i])));
            inputBuilder = call(inputBuilder, "labelVisible", boolean.class, true);
            inputBuilder = call(inputBuilder, "maxLength", int.class, MAX_INPUT_LENGTH);
            inputs.add(inputBuilder.getClass().getMethod("build").invoke(inputBuilder));
        }
        baseBuilder = call(baseBuilder, "inputs", List.class, inputs);

        Object dialogBase = baseBuilder.getClass().getMethod("build").invoke(baseBuilder);

        // ----- Build submit button -----
        Object cmdAction = dialogActionClass
                .getMethod("commandTemplate", String.class)
                .invoke(null, commandTemplate);
        Object submitBtn = actionButtonClass
                .getMethod("create", Component.class, Component.class, int.class, dialogActionClass)
                .invoke(null, Component.text(strip(submitLabel)), null, SUBMIT_BUTTON_WIDTH, cmdAction);

        // ----- Build cancel button (null action = just closes) -----
        Object cancelBtn = actionButtonClass
                .getMethod("create", Component.class, Component.class, int.class, dialogActionClass)
                .invoke(null, Component.text(strip(cancelLabel)), null, CANCEL_BUTTON_WIDTH, null);

        // ----- Build DialogType (multiAction) -----
        Object typeBuilder = dialogTypeClass
                .getMethod("multiAction", List.class)
                .invoke(null, List.of(submitBtn));
        typeBuilder = typeBuilder.getClass()
                .getMethod("exitAction", actionButtonClass)
                .invoke(typeBuilder, cancelBtn);
        Object dialogType = typeBuilder.getClass().getMethod("build").invoke(typeBuilder);

        // ----- Create Dialog via InlinedRegistryBuilderProvider -----
        // Dialog.create() delegates to InlinedRegistryBuilderProvider.instance().createDialog(consumer).
        // The Consumer accepts a RegistryBuilderFactory; at runtime the generic types are erased,
        // so we can pass a Consumer<Object> cast to raw Consumer.
        Object provider = providerClass.getMethod("instance").invoke(null);

        final Object finalBase = dialogBase;
        final Object finalType = dialogType;
        final Class<?> finalBaseClass = dialogBaseClass;
        final Class<?> finalTypeClass = dialogTypeClass;

        Consumer<Object> factoryConsumer = factory -> {
            try {
                Object entryBuilder = factory.getClass().getMethod("empty").invoke(factory);
                entryBuilder = entryBuilder.getClass()
                        .getMethod("base", finalBaseClass)
                        .invoke(entryBuilder, finalBase);
                entryBuilder.getClass()
                        .getMethod("type", finalTypeClass)
                        .invoke(entryBuilder, finalType);
            } catch (Exception ex) {
                throw new RuntimeException("Dialog builder consumer failed", ex);
            }
        };

        Object dialog = providerClass
                .getMethod("createDialog", Consumer.class)
                .invoke(provider, factoryConsumer);

        return dialog;
    }

    /**
     * Sends a dialog to a player via reflection, so that we don't need a
     * compile-time dependency on {@code net.kyori.adventure.dialog.DialogLike}.
     * The {@code DialogLike} class reference is cached after the first lookup.
     */
    private static void showDialogReflective(Player player, Object dialog) throws Exception {
        if (dialogLikeClass == null) {
            dialogLikeClass = Class.forName("net.kyori.adventure.dialog.DialogLike");
        }
        Player.class.getMethod("showDialog", dialogLikeClass).invoke(player, dialog);
    }

    /** Calls a single-argument method by name on {@code obj} and returns the result. */
    private static Object call(Object obj, String method, Class<?> paramType, Object arg)
            throws Exception {
        return obj.getClass().getMethod(method, paramType).invoke(obj, arg);
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if the Paper Dialog API is available at runtime.
     * We probe for the {@code Dialog} class in the paper-specific package.
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
    private String strip(String text) {
        return org.bukkit.ChatColor.stripColor(text);
    }

    /** Sends a friendly chat message when dialogs are unavailable. */
    private void fallbackChat(Player player, String messageKey) {
        player.sendMessage(plugin.msg(messageKey));
    }
}

