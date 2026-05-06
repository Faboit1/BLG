package fr.faboit.blg.dialog.impl;

import fr.faboit.blg.BLGPlugin;
import fr.faboit.blg.dialog.DialogManager;
import fr.faboit.blg.dialog.DialogType;
import fr.faboit.blg.dialog.PlayerDialogState;
import fr.faboit.blg.util.GUIBuilder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Builds and manages the <b>Register</b> dialog.
 *
 * The screen has two input fields (Password + Confirmation), a Done button,
 * Cancel, and a Switch-to-Login button.  If the passwords don't match an
 * error indicator is shown inside the same dialog.
 *
 * Slot constants are used by {@link fr.faboit.blg.listener.InventoryListener}.
 */
public final class RegisterDialog {

    public static final int SLOT_PASSWORD_FIELD  = 20;
    public static final int SLOT_CONFIRM_FIELD   = 24;
    public static final int SLOT_DONE_BUTTON     = 29;
    public static final int SLOT_CANCEL_BUTTON   = 31;
    public static final int SLOT_SWITCH_LOGIN    = 33;

    private final BLGPlugin plugin;

    public RegisterDialog(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Open the register dialog (without error). */
    public void open(Player player) {
        open(player, null);
    }

    /**
     * Open the register dialog, optionally displaying an error message
     * (e.g. "Passwords do not match").
     */
    public void open(Player player, String errorMessage) {
        PlayerDialogState state = plugin.getDialogManager().getState(player);
        state.setCurrentDialog(DialogType.REGISTER);

        Inventory inv = buildInventory(player, state, errorMessage);
        player.openInventory(inv);
        plugin.getDialogManager().trackInventory(player, inv);
        DialogManager.playSound(plugin, player, "dialog-open");
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private Inventory buildInventory(Player player, PlayerDialogState state, String error) {
        ConfigurationSection root = plugin.getConfigManager().section("register-dialog");

        int    rows  = root.getInt("rows", 4);
        String title = root.getString("title", "&8[ &6Creating a Password &8]");

        Inventory inv = Bukkit.createInventory(
                null,
                rows * 9,
                LegacyComponentSerializer.legacyAmpersand().deserialize(title));

        // ── Filler ────────────────────────────────────────────────────────────
        ConfigurationSection fillerSec = root.getConfigurationSection("filler");
        if (fillerSec != null && fillerSec.getBoolean("enabled", true)) {
            Material fillerMat = parseMat(fillerSec.getString("material", "GRAY_STAINED_GLASS_PANE"));
            ItemStack filler   = GUIBuilder.filler(fillerMat);
            List<Integer> slots = toIntList(fillerSec.getStringList("slots"));
            if (slots.isEmpty()) {
                GUIBuilder.fillEmpty(inv, filler);
            } else {
                GUIBuilder.fillSlots(inv, slots, filler);
            }
        }

        // ── Title item ────────────────────────────────────────────────────────
        placeItem(inv, root, "title-item", Map.of("{player}", player.getName()));

        // ── Welcome item (player head or paper) ───────────────────────────────
        placeWelcomeItem(inv, root, player);

        // ── Password field ────────────────────────────────────────────────────
        inv.setItem(SLOT_PASSWORD_FIELD, buildField(
                root.getConfigurationSection("password-field"), state.hasPassword()));

        // ── Confirmation field ────────────────────────────────────────────────
        inv.setItem(SLOT_CONFIRM_FIELD, buildField(
                root.getConfigurationSection("confirm-field"), state.hasConfirmation()));

        // ── Done button ───────────────────────────────────────────────────────
        inv.setItem(SLOT_DONE_BUTTON, buildDoneButton(root, state));

        // ── Cancel button ─────────────────────────────────────────────────────
        placeItem(inv, root, "cancel-button", Map.of("{player}", player.getName()));

        // ── Switch-to-Login button ────────────────────────────────────────────
        placeItem(inv, root, "switch-login-button", Map.of("{player}", player.getName()));

        // ── Error item (only when there's an error) ───────────────────────────
        if (error != null && !error.isEmpty()) {
            ConfigurationSection errSec = root.getConfigurationSection("error-item");
            if (errSec != null && errSec.getBoolean("enabled", true)) {
                int errSlot = errSec.getInt("slot", 4);
                ItemStack errItem = GUIBuilder.fromSection(errSec, Map.of("{error}", error));
                // Dynamically append the error message as lore
                ItemStack withError = GUIBuilder.build(
                        parseMat(errSec.getString("material", "BARRIER")),
                        errSec.getString("name", "&c&l⚠ Error"),
                        List.of("", "&c" + error, ""));
                inv.setItem(errSlot, withError);
            }
        }

        return inv;
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack buildField(ConfigurationSection sec, boolean isSet) {
        if (sec == null) return new ItemStack(Material.PAPER);
        String  name = isSet ? sec.getString("name-set", "&aField") : sec.getString("name", "&eField");
        List<String> lore = isSet ? sec.getStringList("lore-set") : sec.getStringList("lore");
        return GUIBuilder.build(parseMat(sec.getString("material", "PAPER")), name, lore);
    }

    private ItemStack buildDoneButton(ConfigurationSection root, PlayerDialogState state) {
        ConfigurationSection sec = root.getConfigurationSection("done-button");
        if (sec == null) return new ItemStack(Material.LIME_WOOL);

        boolean bothSet = state.hasPassword() && state.hasConfirmation();
        if (bothSet) {
            return GUIBuilder.fromSection(sec, Map.of());
        }
        Material mat  = parseMat(sec.getString("material-disabled", "GRAY_WOOL"));
        String   name = sec.getString("name-disabled", "&7&lDone");
        List<String> lore = sec.getStringList("lore-disabled");
        return GUIBuilder.build(mat, name, lore);
    }

    private void placeWelcomeItem(Inventory inv, ConfigurationSection root, Player player) {
        ConfigurationSection sec = root.getConfigurationSection("welcome-item");
        if (sec == null || !sec.getBoolean("enabled", true)) return;
        int slot = sec.getInt("slot", 13);
        if (slot < 0 || slot >= inv.getSize()) return;

        Material mat = parseMat(sec.getString("material", "PAPER"));
        // Player heads require special handling; fall back to PAPER if not available
        if (mat == Material.PLAYER_HEAD) mat = Material.PAPER;

        inv.setItem(slot, GUIBuilder.fromSection(sec, Map.of("{player}", player.getName())));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void placeItem(Inventory inv, ConfigurationSection root,
                           String key, Map<String, String> replacements) {
        ConfigurationSection sec = root.getConfigurationSection(key);
        if (sec == null || !sec.getBoolean("enabled", true)) return;
        int slot = sec.getInt("slot", 0);
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, GUIBuilder.fromSection(sec, replacements));
        }
    }

    private static Material parseMat(String name) {
        if (name == null) return Material.STONE;
        Material m = Material.matchMaterial(name);
        return m != null ? m : Material.STONE;
    }

    private static List<Integer> toIntList(List<String> strings) {
        return strings.stream()
                .filter(s -> s != null && s.matches("-?\\d+"))
                .map(Integer::parseInt)
                .toList();
    }
}
