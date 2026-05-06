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
 * The <b>Error</b> dialog.
 *
 * Shown when an AuthMe error message is detected in the player's chat stream.
 * Two "Back" buttons are shown depending on the error context (LOGIN or REGISTER).
 *
 * Slot constants are referenced by {@link fr.faboit.blg.listener.InventoryListener}.
 */
public final class ErrorDialog {

    public static final int SLOT_BACK_LOGIN    = 11;
    public static final int SLOT_BACK_REGISTER = 15;
    public static final int SLOT_CLOSE         = 22;

    private final BLGPlugin plugin;

    public ErrorDialog(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Open the error dialog for {@code player}.
     *
     * @param player       the player
     * @param errorMessage the raw error text intercepted from chat
     * @param context      the dialog type that was active when the error occurred
     */
    public void open(Player player, String errorMessage, DialogType context) {
        PlayerDialogState state = plugin.getDialogManager().getState(player);
        state.setCurrentDialog(DialogType.ERROR);
        state.setLastError(errorMessage);
        state.setErrorContext(context);

        Inventory inv = buildInventory(player, state);
        player.openInventory(inv);
        plugin.getDialogManager().trackInventory(player, inv);
        DialogManager.playSound(plugin, player, "error");
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private Inventory buildInventory(Player player, PlayerDialogState state) {
        ConfigurationSection root = plugin.getConfigManager().section("error-dialog");

        int    rows  = root.getInt("rows", 3);
        String title = root.getString("title", "&8[ &cError &8]");

        Inventory inv = Bukkit.createInventory(
                null,
                rows * 9,
                LegacyComponentSerializer.legacyAmpersand().deserialize(title));

        // Filler
        ConfigurationSection fillerSec = root.getConfigurationSection("filler");
        if (fillerSec != null && fillerSec.getBoolean("enabled", true)) {
            Material fillerMat = parseMat(fillerSec.getString("material", "RED_STAINED_GLASS_PANE"));
            GUIBuilder.fillEmpty(inv, GUIBuilder.filler(fillerMat));
        }

        // Error icon
        placeItem(inv, root, "error-icon", Map.of("{error}", state.getLastError()));

        // Error message item
        placeItem(inv, root, "error-message-item", Map.of("{error}", state.getLastError()));

        // Context-aware back buttons
        DialogType ctx = state.getErrorContext();

        ConfigurationSection backLoginSec    = root.getConfigurationSection("back-login-button");
        ConfigurationSection backRegisterSec = root.getConfigurationSection("back-register-button");
        ConfigurationSection closeSec        = root.getConfigurationSection("close-button");

        if (ctx == DialogType.LOGIN && backLoginSec != null) {
            inv.setItem(SLOT_BACK_LOGIN, GUIBuilder.fromSection(backLoginSec, Map.of()));
        } else if (ctx == DialogType.REGISTER && backRegisterSec != null) {
            inv.setItem(SLOT_BACK_REGISTER, GUIBuilder.fromSection(backRegisterSec, Map.of()));
        } else {
            // Both contexts or unknown: show both
            if (backLoginSec    != null) inv.setItem(SLOT_BACK_LOGIN,    GUIBuilder.fromSection(backLoginSec, Map.of()));
            if (backRegisterSec != null) inv.setItem(SLOT_BACK_REGISTER, GUIBuilder.fromSection(backRegisterSec, Map.of()));
        }

        // Close button
        if (closeSec != null) {
            inv.setItem(SLOT_CLOSE, GUIBuilder.fromSection(closeSec, Map.of()));
        }

        return inv;
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
        Material m = name == null ? null : Material.matchMaterial(name);
        return m != null ? m : Material.STONE;
    }
}
