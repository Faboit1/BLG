package io.github.faboit1.blg.dialog;

import io.github.faboit1.blg.BLGPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    private static final int DEFAULT_SUBMIT_BUTTON_WIDTH = 200;
    private static final int DEFAULT_CANCEL_BUTTON_WIDTH = 100;
    private static final int DEFAULT_NAV_BUTTON_WIDTH    = 100;
    private static final int DEFAULT_MAX_INPUT_LENGTH    = 100;
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
    // Config helpers
    // -----------------------------------------------------------------------

    private int submitButtonWidth() {
        return plugin.getConfig().getInt("dialog.submit-button-width", DEFAULT_SUBMIT_BUTTON_WIDTH);
    }

    private int cancelButtonWidth() {
        return plugin.getConfig().getInt("dialog.cancel-button-width", DEFAULT_CANCEL_BUTTON_WIDTH);
    }

    private int navButtonWidth() {
        return plugin.getConfig().getInt("dialog.nav-button-width", DEFAULT_NAV_BUTTON_WIDTH);
    }

    private int maxInputLength() {
        return plugin.getConfig().getInt("dialog.max-input-length", DEFAULT_MAX_INPUT_LENGTH);
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
        openLoginDialog(player, null);
    }

    public void openLoginDialog(Player player, String errorMessage) {
        String title   = plugin.cfg("dialog.login-title");
        String body    = withError(plugin.cfg("dialog.login-body"), errorMessage);
        String button  = plugin.cfg("dialog.login-button");
        String pwLabel = plugin.cfg("dialog.password-label");

        // Build optional forgot-password button descriptor
        List<String[]> extraButtons = null;
        if (plugin.getConfig().getBoolean("dialog.forgot-password-enabled", true)) {
            String forgotLabel = plugin.cfg("dialog.forgot-password-button");
            extraButtons = List.<String[]>of(new String[]{forgotLabel, "/blg_forgot_password"});
        }

        if (dialogApiAvailable) {
            try {
                Object dialog = buildDialog(
                        title, body,
                        new String[]{"password"},
                        new boolean[]{true},
                        new String[]{pwLabel},
                        "/blg_login_submit $(password)",
                        button, null,
                        extraButtons);
                showDialogReflective(player, dialog);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to open login dialog for " + player.getName()
                        + ": " + e.getMessage(), e);
                if (errorMessage != null && !errorMessage.isBlank()) {
                    player.sendMessage(errorMessage);
                }
                fallbackChat(player, "login-prompt");
            }
        } else {
            if (errorMessage != null && !errorMessage.isBlank()) {
                player.sendMessage(errorMessage);
            }
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
        openRegisterDialog(player, null);
    }

    public void openRegisterDialog(Player player, String errorMessage) {
        String title        = plugin.cfg("dialog.register-title");
        String body         = withError(plugin.cfg("dialog.register-body"), errorMessage);
        String button       = plugin.cfg("dialog.register-button");
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
                        button, null);
                showDialogReflective(player, dialog);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to open register dialog for " + player.getName()
                        + ": " + e.getMessage(), e);
                if (errorMessage != null && !errorMessage.isBlank()) {
                    player.sendMessage(errorMessage);
                }
                fallbackChat(player, "register-prompt");
            }
        } else {
            if (errorMessage != null && !errorMessage.isBlank()) {
                player.sendMessage(errorMessage);
            }
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
                openAutoAuthDialog(player);
            }
        } else {
            openAutoAuthDialog(player);
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
                openAutoAuthDialog(player);
            }
        } else {
            openAutoAuthDialog(player);
        }
    }

    /**
     * Opens the unified on-join choice dialog with a single "Register/Login" button.
     *
     * <p>When clicked the client runs {@code /blg_auto_choice}, which stops
     * on-join spam and opens the actual login/register dialog chosen by AuthMe state.
     */
    public void openJoinAutoChoiceDialog(Player player) {
        String title  = plugin.cfg("dialog.join-choice-title");
        String body   = plugin.cfg("dialog.join-choice-body");
        String button = plugin.cfg("dialog.join-choice-button");

        if (dialogApiAvailable) {
            try {
                Object dialog = buildButtonOnlyDialog(
                        title, body,
                        Collections.singletonList(new String[]{button, "/blg_auto_choice"}),
                        null, null);
                showDialogReflective(player, dialog);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to open join-choice dialog for " + player.getName()
                                + ": " + e.getMessage(), e);
                openAutoAuthDialog(player);
            }
        } else {
            openAutoAuthDialog(player);
        }
    }

    /**
     * Opens login or register automatically based on AuthMe registration state.
     */
    public void openAutoAuthDialog(Player player) {
        if (plugin.getAuthMeHook().isHooked() && !plugin.getAuthMeHook().isRegistered(player)) {
            openRegisterDialog(player);
        } else {
            openLoginDialog(player);
        }
    }

    /**
     * Attempts to close/dismiss any dialog currently shown to the player.
     *
     * <p>Paper 1.21.5 exposes {@code Player#clearActiveDialog()} (and possibly
     * other names) for this purpose.  The call is performed reflectively so that
     * the plugin still compiles and runs on builds that do not provide the method
     * – on those builds the call is silently skipped.
     *
     * @param player the player whose dialog should be dismissed
     */
    public void closeActiveDialog(Player player) {
        // Try known Paper method names in order of likelihood
        for (String methodName : new String[]{"clearActiveDialog", "closeDialog", "clearDialog"}) {
            try {
                Player.class.getMethod(methodName).invoke(player);
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] closeActiveDialog (" + methodName + ") → "
                            + player.getName());
                }
                return;
            } catch (NoSuchMethodException ignored) {
                // Try next name
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE,
                        "closeActiveDialog/" + methodName + " failed for " + player.getName()
                                + ": " + e.getMessage(), e);
                return;
            }
        }
        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] closeActiveDialog – no supported method found on this server build.");
        }
    }

    /**
     * Opens the rules dialog for the given player.
     *
     * <p>When {@code canAct} is {@code false} the Accept button is rendered as
     * disabled (grayed out) using the Paper Dialog API's native
     * {@code ActionButton.builder().disabled(true)} support.  The dialog is
     * sent <em>once</em> in this state; a separate scheduled task in
     * {@link io.github.faboit1.blg.flow.FlowManager} re-sends it with the
     * button enabled after the configured wait time.
     *
     * @param page     0-based page index (ignored when pages are disabled)
     * @param canAct   whether the player has waited long enough to accept/leave
     */
    public void openRulesDialog(Player player, int page, boolean canAct) {
        boolean pagesEnabled = plugin.getConfig().getBoolean("rules.pages.enabled", false);
        int totalPages       = plugin.getFlowManager().getTotalPages();
        List<String> lines   = plugin.getFlowManager().getLinesForPage(page);

        // Build body text
        StringJoiner sj = new StringJoiner("\n");
        for (String line : lines) {
            sj.add(line);
        }
        String bodyText = sj.toString();

        // Title – always use the plain title; no countdown shown in the dialog
        String titleTemplate = plugin.cfg("dialog.rules-title");

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
                        "/blg_rules_page " + (page - 1), null, null,
                        String.valueOf(navButtonWidth())});
            }
            if (page < totalPages - 1) {
                mainButtons.add(new String[]{plugin.cfg("dialog.rules-next-button"),
                        "/blg_rules_page " + (page + 1), null, null,
                        String.valueOf(navButtonWidth())});
            }
        }

        // Accept button: disabled (grayed out) until the mandatory wait has elapsed.
        // btn[2] = "true" signals disabled; btn[3] carries the tooltip text.
        String acceptLabel = plugin.cfg("dialog.rules-accept-button");
        if (canAct) {
            mainButtons.add(new String[]{acceptLabel, "/blg_rules_accept"});
        } else {
            String waitTooltip = plugin.cfg("dialog.rules-wait-button-tooltip");
            mainButtons.add(new String[]{acceptLabel, "/blg_rules_accept", "true",
                    waitTooltip != null ? waitTooltip : ""});
        }

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
     * <p>Each entry in {@code mainButtons} is a {@code String[]} with the
     * following positional elements:
     * <ol>
     *   <li>Button label (legacy colour codes supported)</li>
     *   <li>Command to run on click (may be {@code null} for no action)</li>
     *   <li><em>Optional</em> – {@code "true"} to render the button as disabled
     *       (grayed out) using {@code ActionButton.builder().disabled(true)}.
     *       Omit or set to anything other than {@code "true"} for a normal button.</li>
     *   <li><em>Optional</em> – Tooltip text shown on hover when the button is
     *       disabled.  Ignored when the button is not disabled.  May be empty.</li>
     *   <li><em>Optional</em> – Button width in pixels as a decimal string.
     *       When absent or blank the configured {@code dialog.submit-button-width}
     *       value is used.  Pass {@link #navButtonWidth()} for navigation buttons.</li>
     * </ol>
     *
     * @param title           dialog title (legacy colour codes supported)
     * @param bodyText        body text shown in the dialog (newlines supported)
     * @param mainButtons     list of button descriptors as described above
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
            String label       = btn.length > 0 ? btn[0] : "";
            String cmd         = btn.length > 1 ? btn[1] : null;
            boolean disabled   = btn.length > 2 && "true".equals(btn[2]);
            String tooltipText = btn.length > 3 && btn[3] != null && !btn[3].isEmpty() ? btn[3] : null;
            int width = submitButtonWidth();
            if (btn.length > 4 && btn[4] != null && !btn[4].isBlank()) {
                try { width = Integer.parseInt(btn[4]); } catch (NumberFormatException ignored) {}
            }
            // Always build the action from the command.  When the button is disabled and the
            // builder API is available the action won't fire (button is grayed out).  When the
            // builder is unavailable the action is attached so the button still works; the
            // server-side canActOnRules() check will silently reject premature acceptance.
            Object action = cmd != null ? buildClickAction(dialogActionClass, cmd) : null;
            Component tooltip = tooltipText != null ? toComponent(tooltipText) : null;
            Object button = buildActionButton(actionButtonClass, dialogActionClass,
                    toComponent(label), tooltip, width, action, disabled);
            actionBtnObjects.add(button);
        }

        // ----- Build DialogType -----
        Object typeBuilder = dialogTypeClass
                .getMethod("multiAction", List.class)
                .invoke(null, actionBtnObjects);

        if (exitButtonLabel != null) {
            Object exitAction = exitButtonCmd != null
                    ? buildClickAction(dialogActionClass, exitButtonCmd)
                    : null;
            Object exitBtn = actionButtonClass
                    .getMethod("create", Component.class, Component.class, int.class, dialogActionClass)
                    .invoke(null, toComponent(exitButtonLabel), null, cancelButtonWidth(), exitAction);
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
        return buildDialog(title, bodyText, inputKeys, passwordInputs, inputLabels,
                commandTemplate, submitLabel, cancelLabel, null);
    }

    /**
     * Constructs a dialog using Paper's Dialog API loaded entirely via
     * reflection.  Accepts optional extra action buttons that are placed
     * alongside the submit button in the {@code multiAction} list.
     *
     * @param extraButtons optional list of button descriptors: each entry is
     *                     {@code [label, command]}.  May be {@code null}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object buildDialog(String title, String bodyText,
                                   String[] inputKeys, boolean[] passwordInputs, String[] inputLabels,
                                    String commandTemplate,
                                    String submitLabel, String cancelLabel,
                                    List<String[]> extraButtons)
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
            inputBuilder = call(inputBuilder, "maxLength", int.class, maxInputLength());
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
                .invoke(null, toComponent(submitLabel), null, submitButtonWidth(), cmdAction);

        // ----- Build DialogType (multiAction) -----
        List<Object> actionButtons = new ArrayList<>();
        actionButtons.add(submitBtn);

        // Add optional extra buttons (e.g. "Forgot Password?")
        if (extraButtons != null) {
            for (String[] btn : extraButtons) {
                String btnLabel = btn[0];
                String btnCmd   = btn[1];
                Object btnAction = buildClickAction(dialogActionClass, btnCmd);
                Object extraBtn  = buildActionButton(actionButtonClass, dialogActionClass,
                        toComponent(btnLabel), null, cancelButtonWidth(), btnAction, false);
                actionButtons.add(extraBtn);
            }
        }

        Object typeBuilder = dialogTypeClass
                .getMethod("multiAction", List.class)
                .invoke(null, actionButtons);

        // ----- Build cancel/exit button only when a label is provided -----
        if (cancelLabel != null && !cancelLabel.isEmpty()) {
            Object cancelBtn = actionButtonClass
                    .getMethod("create", Component.class, Component.class, int.class, dialogActionClass)
                    .invoke(null, toComponent(cancelLabel), null, cancelButtonWidth(), null);
            typeBuilder = typeBuilder.getClass()
                    .getMethod("exitAction", actionButtonClass)
                    .invoke(typeBuilder, cancelBtn);
        }
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
    private void showDialogReflective(Player player, Object dialog) throws Exception {
        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] showDialogReflective → " + player.getName()
                    + " | dialogType=" + (dialog != null ? dialog.getClass().getSimpleName() : "null"));
        }
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

    /**
     * Constructs an {@code ActionButton} via reflection, preferring the builder
     * API ({@code ActionButton.builder(label)}) so that the {@code disabled}
     * state (grayed-out button) can be set.  Falls back to the static
     * {@code ActionButton.create(...)} factory on older Paper builds that do not
     * expose a builder.
     *
     * <p>When {@code disabled} is {@code true} and the builder is not available
     * the button is created with the provided action intact; the button will
     * look enabled but the server-side {@code canActOnRules} check still
     * prevents premature acceptance.
     *
     * @param actionButtonClass the {@code ActionButton} class loaded via reflection
     * @param dialogActionClass the {@code DialogAction} class loaded via reflection
     * @param label             button label component
     * @param tooltip           tooltip component (may be {@code null})
     * @param width             button width in pixels
     * @param action            click action (may be {@code null} for no-op)
     * @param disabled          whether to render the button as grayed out
     */
    private Object buildActionButton(Class<?> actionButtonClass, Class<?> dialogActionClass,
                                     Component label, Component tooltip, int width,
                                     Object action, boolean disabled) throws Exception {
        // Preferred: builder API (Paper 1.21.5+) supports the disabled state.
        // The action is attached even when disabled=true; a disabled button does
        // not fire its action, so this is safe.
        try {
            Object builder = actionButtonClass.getMethod("builder", Component.class).invoke(null, label);
            try { builder = call(builder, "width", int.class, width); }
            catch (NoSuchMethodException ignored) {}
            if (action != null) {
                try { builder = call(builder, "action", dialogActionClass, action); }
                catch (NoSuchMethodException ignored) {}
            }
            if (tooltip != null) {
                try { builder = call(builder, "tooltip", Component.class, tooltip); }
                catch (NoSuchMethodException ignored) {}
            }
            if (disabled) {
                try { builder = call(builder, "disabled", boolean.class, true); }
                catch (NoSuchMethodException ignored) {}
            }
            return builder.getClass().getMethod("build").invoke(builder);
        } catch (NoSuchMethodException e) {
            // Builder not available – fall back to the static create factory.
            // The action is always attached so the button remains functional;
            // server-side checks (e.g. canActOnRules) will reject premature clicks.
            return actionButtonClass
                    .getMethod("create", Component.class, Component.class, int.class, dialogActionClass)
                    .invoke(null, label, tooltip, width, action);
        }
    }

    /**
     * Builds a {@code DialogAction} for a plain button click that runs {@code cmd}.
     *
     * <p>Prefers {@code DialogAction.staticAction(ClickEvent.runCommand(cmd))} when
     * available (Paper 1.21.6+) because {@code staticAction} fires only when the
     * player explicitly clicks the button.  On older builds (1.21.5) only
     * {@code commandTemplate} exists; it is used as a fallback and works in the
     * same way for button clicks that carry no form-input substitutions.
     *
     * @param dialogActionClass the {@code DialogAction} class loaded via reflection
     * @param cmd               the command to run, including the leading {@code /}
     * @return the created {@code DialogAction} instance
     */
    private Object buildClickAction(Class<?> dialogActionClass, String cmd)
            throws Exception {
        // Preferred: staticAction(ClickEvent.runCommand(cmd)) – fires on explicit click only
        try {
            Class<?> clickEventClass = Class.forName("net.kyori.adventure.text.event.ClickEvent");
            Object clickEvent = clickEventClass
                    .getMethod("runCommand", String.class)
                    .invoke(null, cmd);
            return dialogActionClass
                    .getMethod("staticAction", clickEventClass)
                    .invoke(null, clickEvent);
        } catch (NoSuchMethodException e) {
            // staticAction not available on this Paper build – fall back to commandTemplate
            plugin.getLogger().log(Level.FINE,
                    "DialogAction.staticAction not found; falling back to commandTemplate for button: " + cmd);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE,
                    "Failed to build staticAction for button '" + cmd + "'; falling back to commandTemplate: "
                    + e.getMessage());
        }
        return dialogActionClass.getMethod("commandTemplate", String.class).invoke(null, cmd);
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

    private String withError(String body, String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return body;
        }
        if (body == null || body.isBlank()) {
            return errorMessage;
        }
        return errorMessage + "\n\n" + body;
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
                + plugin.cfg("messages.rules-chat-fallback-footer"));
    }
}
