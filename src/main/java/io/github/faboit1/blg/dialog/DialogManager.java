package io.github.faboit1.blg.dialog;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Builds and sends Minecraft Dialog API packets to players.
 *
 * <h2>How the Dialog API works</h2>
 * The Minecraft Dialog API (introduced in 1.21.5 / 25w17a) lets servers open
 * a rich, form-like GUI in the client without requiring a resource-pack or
 * mod.  The packet is {@code ClientboundShowDialogPacket} and carries an NBT
 * payload that describes the dialog tree.
 *
 * <p>When a player fills in text inputs and presses a submit button the
 * client runs the command template stored in the button's {@code action}
 * field, substituting every {@code %<key>%} placeholder with the value of
 * the input whose {@code key} matches.  The server receives a perfectly
 * ordinary {@link org.bukkit.event.player.PlayerCommandPreprocessEvent}, so
 * no special packet listener is required to handle the response.
 *
 * <h2>Packet structure (NBT)</h2>
 * <pre>
 * dialog: {
 *   type: "minecraft:form_dialog",      // new in 25w17a
 *   title: {text: "…"},
 *   body:  [ {type: "minecraft:plain_message", contents: {text: "…"}} ],
 *   inputs: [
 *     {
 *       type:   "minecraft:text_box",
 *       key:    "password",
 *       label:  {text: "Password"},
 *       options: { secret: true }
 *     }
 *   ],
 *   action: {
 *     type:     "minecraft:action_on_click",
 *     on_click: {
 *       type:     "minecraft:run_command",
 *       // %password% is replaced by the value the player typed
 *       template: "/blg_login_submit %password%"
 *     }
 *   }
 * }
 * </pre>
 *
 * <h2>Reflection strategy</h2>
 * To stay independent of exact Paper/NMS build numbers the packet is built via
 * Minecraft's {@code CompoundTag} (NBT) classes reached through reflection and
 * dispatched through the NMS connection object.  The class-name constants
 * below target the Mojang-mapped names that Paper exposes; if a future version
 * changes them the plugin logs a clear warning instead of crashing.
 */
public class DialogManager {

    // -----------------------------------------------------------------------
    // NMS class-name constants (Mojang-mapped, Paper 1.21.5+)
    // -----------------------------------------------------------------------

    /** net.minecraft.nbt.CompoundTag */
    private static final String CLS_COMPOUND_TAG =
            "net.minecraft.nbt.CompoundTag";

    /** net.minecraft.nbt.StringTag */
    private static final String CLS_STRING_TAG =
            "net.minecraft.nbt.StringTag";

    /** net.minecraft.nbt.ListTag */
    private static final String CLS_LIST_TAG =
            "net.minecraft.nbt.ListTag";

    /** net.minecraft.nbt.ByteTag */
    private static final String CLS_BYTE_TAG =
            "net.minecraft.nbt.ByteTag";

    /**
     * Packet that opens a dialog on the client.
     * Introduced in 25w17a; maps to {@code ClientboundShowDialogPacket}.
     */
    private static final String CLS_SHOW_DIALOG_PACKET =
            "net.minecraft.network.protocol.game.ClientboundShowDialogPacket";

    /** CraftPlayer – org.bukkit.craftbukkit.entity.CraftPlayer */
    private static final String CLS_CRAFT_PLAYER =
            "org.bukkit.craftbukkit.entity.CraftPlayer";

    // -----------------------------------------------------------------------

    private final BLGPlugin plugin;

    /** Whether the Dialog packet API is available on this server. */
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
     * <p>When the player clicks the "Login" button the client runs:
     * <pre>/blg_login_submit &lt;their-password&gt;</pre>
     */
    public void openLoginDialog(Player player) {
        String title  = plugin.cfg("dialog.login-title");
        String body   = plugin.cfg("dialog.login-body");
        String button = plugin.cfg("dialog.login-button");
        String cancel = plugin.cfg("dialog.cancel-button");
        String pwLabel = plugin.cfg("dialog.password-label");

        if (dialogApiAvailable) {
            sendFormDialog(player, title, body,
                    new TextBoxInput("password", pwLabel, true),
                    "/blg_login_submit %password%", button, cancel);
        } else {
            fallbackChat(player, "login-prompt");
        }
    }

    /**
     * Opens the register dialog for the given player.
     *
     * <p>When the player clicks the "Register" button the client runs:
     * <pre>/blg_register_submit &lt;password&gt; &lt;confirmPassword&gt;</pre>
     */
    public void openRegisterDialog(Player player) {
        String title       = plugin.cfg("dialog.register-title");
        String body        = plugin.cfg("dialog.register-body");
        String button      = plugin.cfg("dialog.register-button");
        String cancel      = plugin.cfg("dialog.cancel-button");
        String pwLabel     = plugin.cfg("dialog.password-label");
        String confirmLabel = plugin.cfg("dialog.confirm-password-label");

        if (dialogApiAvailable) {
            sendFormDialog(player, title, body,
                    new TextBoxInput[]  {
                            new TextBoxInput("password",        pwLabel,      true),
                            new TextBoxInput("confirmPassword", confirmLabel, true)
                    },
                    "/blg_register_submit %password% %confirmPassword%",
                    button, cancel);
        } else {
            fallbackChat(player, "register-prompt");
        }
    }

    // -----------------------------------------------------------------------
    // Internal – dialog building & sending
    // -----------------------------------------------------------------------

    /** Convenience overload for a single text-box input. */
    private void sendFormDialog(Player player,
                                String title, String body,
                                TextBoxInput input,
                                String commandTemplate,
                                String submitLabel, String cancelLabel) {
        sendFormDialog(player, title, body,
                new TextBoxInput[]{input}, commandTemplate, submitLabel, cancelLabel);
    }

    /**
     * Builds an NBT compound tag representing a {@code minecraft:form_dialog}
     * and sends it to the player via the NMS packet.
     *
     * <p><strong>NBT schema:</strong>
     * <pre>
     * {
     *   type: "minecraft:form_dialog",
     *   title: { text: "&lt;title&gt;" },
     *   body: [ { type: "minecraft:plain_message",
     *              contents: { text: "&lt;body&gt;" } } ],
     *   inputs: [
     *     { type: "minecraft:text_box",
     *       key:  "&lt;key&gt;",
     *       label: { text: "&lt;label&gt;" },
     *       options: { secret: 1b } }
     *   ],
     *   action: {
     *     type: "minecraft:action_on_click",
     *     on_click: {
     *       type: "minecraft:run_command",
     *       template: "&lt;commandTemplate&gt;"
     *     }
     *   },
     *   cancel_action: {
     *     type: "minecraft:run_command",
     *     template: ""
     *   },
     *   external_title: "&lt;title (plain)&gt;"
     * }
     * </pre>
     */
    private void sendFormDialog(Player player,
                                String title, String body,
                                TextBoxInput[] inputs,
                                String commandTemplate,
                                String submitLabel, String cancelLabel) {
        try {
            Class<?> compoundTagClass = Class.forName(CLS_COMPOUND_TAG);
            Object root = compoundTagClass.getDeclaredConstructor().newInstance();

            // -- type --
            putString(root, "type", "minecraft:form_dialog");

            // -- title --
            Object titleTag = compoundTagClass.getDeclaredConstructor().newInstance();
            putString(titleTag, "text", stripColor(title));
            put(root, "title", titleTag);

            // -- body --
            Object bodyEntry = compoundTagClass.getDeclaredConstructor().newInstance();
            putString(bodyEntry, "type", "minecraft:plain_message");
            Object bodyContents = compoundTagClass.getDeclaredConstructor().newInstance();
            putString(bodyContents, "text", stripColor(body));
            put(bodyEntry, "contents", bodyContents);
            Object bodyList = buildListTag(new Object[]{bodyEntry}, 10 /* TAG_Compound */);
            put(root, "body", bodyList);

            // -- inputs --
            Object[] inputTags = new Object[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                TextBoxInput inp = inputs[i];
                Object inputTag = compoundTagClass.getDeclaredConstructor().newInstance();
                putString(inputTag, "type", "minecraft:text_box");
                putString(inputTag, "key",  inp.key());
                Object labelTag = compoundTagClass.getDeclaredConstructor().newInstance();
                putString(labelTag, "text", stripColor(inp.label()));
                put(inputTag, "label", labelTag);
                if (inp.secret()) {
                    Object options = compoundTagClass.getDeclaredConstructor().newInstance();
                    putByte(options, "secret", (byte) 1);
                    put(inputTag, "options", options);
                }
                inputTags[i] = inputTag;
            }
            Object inputList = buildListTag(inputTags, 10);
            put(root, "inputs", inputList);

            // -- action (submit button) --
            Object onClickAction = compoundTagClass.getDeclaredConstructor().newInstance();
            putString(onClickAction, "type",     "minecraft:run_command");
            putString(onClickAction, "template", commandTemplate);

            Object submitLabelTag = compoundTagClass.getDeclaredConstructor().newInstance();
            putString(submitLabelTag, "text", stripColor(submitLabel));

            Object actionTag = compoundTagClass.getDeclaredConstructor().newInstance();
            putString(actionTag, "type", "minecraft:action_on_click");
            put(actionTag, "label", submitLabelTag);
            put(actionTag, "on_click", onClickAction);
            put(root, "action", actionTag);

            // -- cancel_action --
            Object cancelLabelTag = compoundTagClass.getDeclaredConstructor().newInstance();
            putString(cancelLabelTag, "text", stripColor(cancelLabel));
            put(root, "cancel_label", cancelLabelTag);

            // -- external_title (shown in server-links & pause menu) --
            putString(root, "external_title", stripColor(title));

            // ---- dispatch the packet ----------------------------------------
            dispatchDialogPacket(player, root);

        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to build dialog NBT for " + player.getName() + ": " + e.getMessage(), e);
            fallbackChat(player, "login-prompt");
        }
    }

    /**
     * Wraps the NBT dialog compound tag in a {@code ClientboundShowDialogPacket}
     * and sends it through the player's NMS connection.
     */
    private void dispatchDialogPacket(Player player, Object dialogNbt)
            throws ReflectiveOperationException {
        // CraftPlayer.getHandle() → ServerPlayer
        Class<?> craftPlayerClass = Class.forName(CLS_CRAFT_PLAYER);
        Object serverPlayer = craftPlayerClass
                .getMethod("getHandle")
                .invoke(player);

        // Build the packet – the constructor accepts a single CompoundTag
        Class<?> packetClass = Class.forName(CLS_SHOW_DIALOG_PACKET);
        Class<?> compoundTagClass = Class.forName(CLS_COMPOUND_TAG);
        Object packet = packetClass
                .getDeclaredConstructor(compoundTagClass)
                .newInstance(dialogNbt);

        // ServerPlayer.connection.send(Packet)
        Object connection = serverPlayer.getClass()
                .getField("connection")
                .get(serverPlayer);
        Method send = connection.getClass()
                .getMethod("send",
                        Class.forName("net.minecraft.network.protocol.Packet"));
        send.invoke(connection, packet);
    }

    // -----------------------------------------------------------------------
    // NBT helpers (all via reflection to avoid compile-time NMS dependency)
    // -----------------------------------------------------------------------

    /** Calls {@code CompoundTag.putString(key, value)}. */
    private void putString(Object tag, String key, String value)
            throws ReflectiveOperationException {
        tag.getClass().getMethod("putString", String.class, String.class)
                .invoke(tag, key, value);
    }

    /** Calls {@code CompoundTag.put(key, Tag)}. */
    private void put(Object tag, String key, Object childTag)
            throws ReflectiveOperationException {
        Class<?> nbtTagClass = Class.forName("net.minecraft.nbt.Tag");
        tag.getClass().getMethod("put", String.class, nbtTagClass)
                .invoke(tag, key, childTag);
    }

    /** Calls {@code CompoundTag.putByte(key, value)}. */
    private void putByte(Object tag, String key, byte value)
            throws ReflectiveOperationException {
        tag.getClass().getMethod("putByte", String.class, byte.class)
                .invoke(tag, key, value);
    }

    /**
     * Creates a {@code ListTag} containing the provided elements.
     *
     * @param elements  array of {@code Tag} instances (must all be the same type)
     * @param elementTypeId  NBT type id (10 = TAG_Compound)
     */
    private Object buildListTag(Object[] elements, int elementTypeId)
            throws ReflectiveOperationException {
        Class<?> listTagClass   = Class.forName(CLS_LIST_TAG);
        Class<?> nbtTagClass    = Class.forName("net.minecraft.nbt.Tag");
        Object listTag = listTagClass.getDeclaredConstructor().newInstance();
        Method addMethod = listTagClass.getMethod("add", nbtTagClass);
        for (Object el : elements) {
            addMethod.invoke(listTag, el);
        }
        return listTag;
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    /**
     * Checks at startup whether the Dialog API packet class is present on this
     * server version, so we can fall back gracefully on older builds.
     */
    private boolean probeDialogApi() {
        try {
            Class.forName(CLS_SHOW_DIALOG_PACKET);
            plugin.getLogger().info("Minecraft Dialog API detected – dialog UI enabled.");
            return true;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning(
                    "Minecraft Dialog API not found on this server version. " +
                    "Chat-based prompts will be used instead. " +
                    "Upgrade to Paper 1.21.5+ to enable dialog UI.");
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

    // -----------------------------------------------------------------------
    // Inner record – text box input descriptor
    // -----------------------------------------------------------------------

    /** Describes a single text-box field in a form dialog. */
    private record TextBoxInput(String key, String label, boolean secret) {}
}
