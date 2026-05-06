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
 * Builds and manages the <b>Login</b> dialog.
 *
 * Layout (4 rows = 36 slots):
 * <pre>
 *   [0–8]   top border row
 *   [9–17]  middle row – info item at 13
 *   [18–26] empty / filler
 *   [27–35] bottom row – password field at 20, buttons at 29/31/33
 * </pre>
 *
 * The dialog title and every item are read from {@code login-dialog.*} in
 * config.yml so server admins can customise everything.
 */
public final class LoginDialog {

    /* Slot ids – kept as named constants so they can be checked
       in the InventoryListener without magic numbers. */
    public static final int SLOT_PASSWORD_FIELD     = 20;
    public static final int SLOT_LOGIN_BUTTON       = 29;
    public static final int SLOT_CANCEL_BUTTON      = 31;
    public static final int SLOT_SWITCH_REGISTER    = 33;

    private final BLGPlugin plugin;

    public LoginDialog(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Open the login dialog for {@code player}.
     * The inventory is built from config each time so live config changes
     * take effect without restarting.
     */
    public void open(Player player) {
        PlayerDialogState state = plugin.getDialogManager().getState(player);
        state.setCurrentDialog(DialogType.LOGIN);

        Inventory inv = buildInventory(player, state);
        player.openInventory(inv);
        plugin.getDialogManager().trackInventory(player, inv);
        DialogManager.playSound(plugin, player, "dialog-open");
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private Inventory buildInventory(Player player, PlayerDialogState state) {
        ConfigurationSection root = plugin.getConfigManager().section("login-dialog");

        int rows  = root.getInt("rows", 4);
        String title = root.getString("title", "&8[ &bLogin &8]");

        Inventory inv = Bukkit.createInventory(
                null,
                rows * 9,
                LegacyComponentSerializer.legacyAmpersand().deserialize(title));

        // ── Filler ────────────────────────────────────────────────────────────
        ConfigurationSection fillerSec = root.getConfigurationSection("filler");
        if (fillerSec != null && fillerSec.getBoolean("enabled", true)) {
            Material fillerMat = parseMat(fillerSec.getString("material", "BLACK_STAINED_GLASS_PANE"));
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

        // ── Info / welcome item ───────────────────────────────────────────────
        placeItem(inv, root, "info-item", Map.of("{player}", player.getName()));

        // ── Password field ────────────────────────────────────────────────────
        inv.setItem(SLOT_PASSWORD_FIELD, buildPasswordField(root, state));

        // ── Login button ──────────────────────────────────────────────────────
        inv.setItem(SLOT_LOGIN_BUTTON, buildLoginButton(root, state));

        // ── Cancel button ─────────────────────────────────────────────────────
        ConfigurationSection cancelSec = root.getConfigurationSection("cancel-button");
        if (cancelSec != null) {
            inv.setItem(SLOT_CANCEL_BUTTON, GUIBuilder.fromSection(cancelSec,
                    Map.of("{player}", player.getName())));
        }

        // ── Switch-to-Register button ─────────────────────────────────────────
        ConfigurationSection switchSec = root.getConfigurationSection("switch-register-button");
        if (switchSec != null) {
            inv.setItem(SLOT_SWITCH_REGISTER, GUIBuilder.fromSection(switchSec,
                    Map.of("{player}", player.getName())));
        }

        return inv;
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack buildPasswordField(ConfigurationSection root, PlayerDialogState state) {
        ConfigurationSection sec = root.getConfigurationSection("password-field");
        if (sec == null) return new ItemStack(Material.PAPER);

        boolean hasPassword = state.hasPassword();
        String  rawName = hasPassword
                ? sec.getString("name-set",  "&aPassword")
                : sec.getString("name",      "&ePassword");
        List<String> rawLore = hasPassword
                ? sec.getStringList("lore-set")
                : sec.getStringList("lore");

        Material mat = parseMat(sec.getString("material", "PAPER"));
        return GUIBuilder.build(mat, rawName, rawLore);
    }

    private ItemStack buildLoginButton(ConfigurationSection root, PlayerDialogState state) {
        ConfigurationSection sec = root.getConfigurationSection("login-button");
        if (sec == null) return new ItemStack(Material.LIME_WOOL);

        if (state.hasPassword()) {
            return GUIBuilder.fromSection(sec, Map.of());
        }

        // Disabled appearance
        Material mat  = parseMat(sec.getString("material-disabled", "GRAY_WOOL"));
        String   name = sec.getString("name-disabled", "&7&lLogin »");
        List<String> lore = sec.getStringList("lore-disabled");
        return GUIBuilder.build(mat, name, lore);
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
