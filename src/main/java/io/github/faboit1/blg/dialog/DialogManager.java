package io.github.faboit1.blg.dialog;

import io.github.faboit1.blg.BLGPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
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
    private static final int NAV_BUTTON_WIDTH    = 100;
    private static final int MAX_INPUT_LENGTH = 100;
    // Attempt order: most likely names first based on Paper snapshots and
    // potential API naming variations exposed through reflection.
    private static final List<String> PASSWORD_MASKING_METHODS = Arrays.asList(
            "obfuscated",
            "password",
            "secret",
            "masked",
            "hidden",
            "hideInput"
    );
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();

    private final BLGPlugin plugin;

    /** Whether the Paper Dialog API is available on this server build. */
    private final boolean dialogApiAvailable;

    /**
     * Cached reference to {@code net.kyori.adventure.dialog.DialogLike}, loaded
     * lazily when the Dialog API is first confirmed available.  {@code null} if
     * the class is not on the classpath.
     */
    private static Class<?> dialogLikeClass;
    private boolean passwordMaskingWarningLogged;

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
                        new boolean[]{true},
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
                        new boolean[]{true, true},
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

    /**
     * Opens the login-stub dialog for a registered player.
     *
     * <p>Shows a single "Login" button.  When clicked the client runs
     * {@code /blg_login_choice}, which stops the spam task and opens the actual
     * login dialog (with the password input field).
     */
    public void openLoginChoiceDialog(Player player) {
        String title  = plugin.cfg("dialog.login-choice-title");
        String body   = plugin.cfg("dialog.login-choice-body");
        String button = plugin.cfg("dialog.login-choice-button");

        if (dialogApiAvailable) {
            try {
                Object dialog = buildButtonOnlyDialog(
                        title, body,
                        List.<String[]>of(new String[]{button, "/blg_login_choice"}),
                        null, null);
                showDialogReflective(player, dialog);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to open login-choice dialog for " + player.getName()
                        + ": " + e.getMessage(), e);
                openAuthFallback(player);
            }
        } else {
            openAuthFallback(player);
        }
    }

    /**
     * Opens the register-stub dialog for an unregistered player.
     *
     * <p>Shows a single "Register" button.  When clicked the client runs
     * {@code /blg_register_choice}, which stops the spam task and opens the
     * actual register dialog (with the password input fields).
     */
    public void openRegisterChoiceDialog(Player player) {
        String title  = plugin.cfg("dialog.register-choice-title");
        String body   = plugin.cfg("dialog.register-choice-body");
        String button = plugin.cfg("dialog.register-choice-button");

        if (dialogApiAvailable) {
            try {
                Object dialog = buildButtonOnlyDialog(
                        title, body,
                        List.<String[]>of(new String[]{button, "/blg_register_choice"}),
                        null, null);
                showDialogReflective(player, dialog);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to open register-choice dialog for " + player.getName()
                        + ": " + e.getMessage(), e);
                openAuthFallback(player);
            }
        } else {
            openAuthFallback(player);
        }
    }

    /**
     * Opens the rules dialog for the given player.
     *
     * @param page           0-based page index (ignored when pages are disabled)
     * @param canAct         whether the player has waited long enough to accept/leave
     * @param secondsLeft    seconds remaining in the mandatory wait (0 when canAct is true)
     */
    public void openRulesDialog(Player player, int page, boolean canAct, int secondsLeft) {
        boolean pagesEnabled = plugin.getConfig().getBoolean("rules.pages.enabled", false);
        int totalPages       = plugin.getFlowManager().getTotalPages();
        List<String> lines   = plugin.getFlowManager().getLinesForPage(page);

        // Build body text
        StringJoiner sj = new StringJoiner("\n");
        for (String line : lines) {
            sj.add(line);
        }
        String bodyText = sj.toString();

        // Build title – show countdown when player must still wait
        String titleTemplate = canAct
                ? plugin.cfg("dialog.rules-title")
                : plugin.cfg("dialog.rules-wait-title").replace("%seconds%", String.valueOf(secondsLeft));

        // Build page label when pagination is active
        if (pagesEnabled && totalPages > 1) {
            titleTemplate = titleTemplate + " &7(" + (page + 1) + "/" + totalPages + ")";
            titleTemplate = org.bukkit.ChatColor.translateAlternateColorCodes('&', titleTemplate);
        }

        // Collect main action buttons (page navigation + accept)
        List<String[]> mainButtons = new ArrayList<>();

        if (pagesEnabled && totalPages > 1) {
            if (page > 0) {
                mainButtons.add(new String[]{plugin.cfg("dialog.rules-prev-button"),
                        "/blg_rules_page " + (page - 1)});
            }
            if (page < totalPages - 1) {
                mainButtons.add(new String[]{plugin.cfg("dialog.rules-next-button"),
                        "/blg_rules_page " + (page + 1)});
            }
        }

        // Accept button is always present; the server enforces the wait on the command side
        mainButtons.add(new String[]{plugin.cfg("dialog.rules-accept-button"), "/blg_rules_accept"});

        String leaveLabel = plugin.cfg("dialog.rules-leave-button");

        if (dialogApiAvailable) {
            try {
                Object dialog = buildButtonOnlyDialog(
                        titleTemplate, bodyText,
                        mainButtons,
                        leaveLabel, "/blg_rules_leave");
                showDialogReflective(player, dialog);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to open rules dialog for " + player.getName()
                        + ": " + e.getMessage(), e);
                sendRulesFallbackChat(player);
            }
        } else {
            sendRulesFallbackChat(player);
        }
    }

    // -----------------------------------------------------------------------
    // Dialog building via reflection
    // (All io.papermc.paper.dialog.* classes are loaded dynamically so that
    //  the plugin compiles against paper-api snapshots that may not yet
    //  include the Dialog API packages.)
    // -----------------------------------------------------------------------

    /**
     * Constructs a dialog that contains only action buttons (no text-input
     * fields) using Paper's Dialog API loaded via reflection.
     *
     * @param title           dialog title (legacy colour codes supported)
     * @param bodyText        body text shown in the dialog (newlines supported)
     * @param mainButtons     list of {@code [label, command]} pairs for the main button row
     * @param exitButtonLabel label for the exit/cancel button, or {@code null} for none
     * @param exitButtonCmd   command run by the exit button, or {@code null} for close-only
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object buildButtonOnlyDialog(String title, String bodyText,
                                          List<String[]> mainButtons,
                                          String exitButtonLabel, String exitButtonCmd)
            throws Exception {

        Class<?> dialogBaseClass   = Class.forName("io.papermc.paper.registry.data.dialog.DialogBase");
        Class<?> dialogBodyClass   = Class.forName("io.papermc.paper.registry.data.dialog.body.DialogBody");
        Class<?> dialogTypeClass   = Class.forName("io.papermc.paper.registry.data.dialog.type.DialogType");
        Class<?> actionButtonClass = Class.forName("io.papermc.paper.registry.data.dialog.ActionButton");
        Class<?> dialogActionClass = Class.forName("io.papermc.paper.registry.data.dialog.action.DialogAction");
        Class<?> afterActionEnum   = Class.forName("io.papermc.paper.registry.data.dialog.DialogBase$DialogAfterAction");
        Class<?> providerClass     = Class.forName("io.papermc.paper.registry.data.InlinedRegistryBuilderProvider");

        // ----- Build DialogBase -----
        Object baseBuilder = dialogBaseClass
                .getMethod("builder", Component.class)
                .invoke(null, toComponent(title));

        Object bodyEntry = dialogBodyClass
                .getMethod("plainMessage", Component.class)
                .invoke(null, toComponent(bodyText));
        baseBuilder = call(baseBuilder, "body", List.class, List.of(bodyEntry));
        baseBuilder = call(baseBuilder, "canCloseWithEscape", boolean.class, false);

        Object closeAction = Enum.valueOf((Class<Enum>) afterActionEnum, "CLOSE");
        baseBuilder = baseBuilder.getClass()
                .getMethod("afterAction", afterActionEnum)
                .invoke(baseBuilder, closeAction);

        Object dialogBase = baseBuilder.getClass().getMethod("build").invoke(baseBuilder);

        // ----- Build main action buttons -----
        List<Object> actionBtnObjects = new ArrayList<>();
        for (String[] btn : mainButtons) {
            String label = btn.length > 0 ? btn[0] : "";
            String cmd   = btn.length > 1 ? btn[1] : null;
            Object action = cmd != null
                    ? dialogActionClass.getMethod("commandTemplate", String.class).invoke(null, cmd)
                    : null;
            Object button = actionButtonClass
                    .getMethod("create", Component.class, Component.class, int.class, dialogActionClass)
                    .invoke(null, toComponent(label), null, SUBMIT_BUTTON_WIDTH, action);
            actionBtnObjects.add(button);
        }

        // ----- Build DialogType -----
        Object typeBuilder = dialogTypeClass
                .getMethod("multiAction", List.class)
                .invoke(null, actionBtnObjects);

        if (exitButtonLabel != null) {
            Object exitAction = exitButtonCmd != null
                    ? dialogActionClass.getMethod("commandTemplate", String.class).invoke(null, exitButtonCmd)
                    : null;
            Object exitBtn = actionButtonClass
                    .getMethod("create", Component.class, Component.class, int.class, dialogActionClass)
                    .invoke(null, toComponent(exitButtonLabel), null, CANCEL_BUTTON_WIDTH, exitAction);
            typeBuilder = typeBuilder.getClass()
                    .getMethod("exitAction", actionButtonClass)
                    .invoke(typeBuilder, exitBtn);
        }

        Object dialogType = typeBuilder.getClass().getMethod("build").invoke(typeBuilder);

        // ----- Create Dialog via InlinedRegistryBuilderProvider -----
        Object provider = providerClass.getMethod("instance").invoke(null);

        final Object finalBase  = dialogBase;
        final Object finalType  = dialogType;
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

        return providerClass
                .getMethod("createDialog", Consumer.class)
                .invoke(provider, factoryConsumer);
    }

    /**
     * Constructs a dialog using Paper's Dialog API loaded entirely via
     * reflection so that the plugin compiles against any paper-api snapshot,
     * even those that do not yet include the Dialog API packages.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object buildDialog(String title, String bodyText,
                                   String[] inputKeys, boolean[] passwordInputs, String[] inputLabels,
                                    String commandTemplate,
                                    String submitLabel, String cancelLabel)
            throws Exception {
        if (inputKeys.length != inputLabels.length || inputKeys.length != passwordInputs.length) {
            throw new IllegalArgumentException(
                    "Input metadata length mismatch: keys=" + inputKeys.length
                            + ", labels=" + inputLabels.length
                            + ", passwordInputs=" + passwordInputs.length);
        }

        boolean passwordMaskingEnabled = plugin.getConfig().getBoolean("dialog.password-masking-enabled", true);

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
                .invoke(null, toComponent(title));

        Object bodyEntry = dialogBodyClass
                .getMethod("plainMessage", Component.class)
                .invoke(null, toComponent(bodyText));
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
                    .invoke(null, inputKeys[i], toComponent(inputLabels[i]));
            inputBuilder = call(inputBuilder, "labelVisible", boolean.class, true);
            inputBuilder = call(inputBuilder, "maxLength", int.class, MAX_INPUT_LENGTH);
            if (passwordMaskingEnabled && passwordInputs[i]) {
                inputBuilder = applyPasswordMasking(inputBuilder);
            }
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
                .invoke(null, toComponent(submitLabel), null, SUBMIT_BUTTON_WIDTH, cmdAction);

        // ----- Build cancel button (null action = just closes) -----
        Object cancelBtn = actionButtonClass
                .getMethod("create", Component.class, Component.class, int.class, dialogActionClass)
                .invoke(null, toComponent(cancelLabel), null, CANCEL_BUTTON_WIDTH, null);

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

    private Object applyPasswordMasking(Object inputBuilder) {
        for (String method : PASSWORD_MASKING_METHODS) {
            Object updated = callIfPresent(inputBuilder, method, true);
            if (updated != null) {
                return updated;
            }
        }

        Object inverseUpdated = callIfPresent(inputBuilder, "showCharacters", false);
        if (inverseUpdated != null) {
            return inverseUpdated;
        }

        if (!passwordMaskingWarningLogged) {
            passwordMaskingWarningLogged = true;
            plugin.getLogger().warning(
                    "Password masking is enabled in config, but this Paper Dialog API build " +
                    "does not expose a known masking method. Falling back to plain text input.");
        }
        return inputBuilder;
    }

    private Object callIfPresent(Object obj, String method, Object arg) {
        try {
            Class<?> type = arg instanceof Boolean ? boolean.class : arg.getClass();
            return obj.getClass().getMethod(method, type).invoke(obj, arg);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Exception e) {
            // `showCharacters(false)` is a fallback probe and can fail on many
            // builds where the API simply does not expose it, so keep noise low.
            Level level = method.equals("showCharacters") ? Level.FINE : Level.WARNING;
            plugin.getLogger().log(level,
                    "Failed to apply password masking method '" + method + "': " + e.getMessage(), e);
            return null;
        }
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

    /** Converts a legacy-colour-coded string into an Adventure component. */
    private Component toComponent(String text) {
        return LEGACY_SERIALIZER.deserialize(text == null ? "" : text);
    }

    /** Sends a friendly chat message when dialogs are unavailable. */
    private void fallbackChat(Player player, String messageKey) {
        player.sendMessage(plugin.msg(messageKey));
    }

    /**
     * Fallback when the Dialog API is unavailable and a player needs to go
     * through the auth flow: directly open the appropriate auth dialog.
     */
    private void openAuthFallback(Player player) {
        if (plugin.getAuthMeHook().isHooked() && !plugin.getAuthMeHook().isRegistered(player)) {
            openRegisterDialog(player);
        } else {
            openLoginDialog(player);
        }
    }

    /**
     * Sends the rules as chat messages when the Dialog API is unavailable.
     * Called at most once per spam tick – the caller should stop spamming
     * after the fallback is shown.
     */
    private void sendRulesFallbackChat(Player player) {
        List<String> lines = plugin.getRulesManager().getFormattedLines();
        player.sendMessage(plugin.cfg("messages.prefix") + plugin.cfg("dialog.rules-title"));
        for (String line : lines) {
            player.sendMessage(line);
        }
        player.sendMessage(plugin.cfg("messages.prefix")
                + "§7Type §a/blg_rules_accept §7to accept the rules or §c/blg_rules_leave §7to leave.");
    }
}
