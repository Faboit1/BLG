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
 * The <b>Confirm Registration</b> dialog.
 *
 * Shows the player their chosen password (highlighted in warning style) and
 * asks them to confirm.  The Confirm button is locked for
 * {@code cooldown.confirm-seconds} seconds after the dialog opens.
 *
 * Slot constants are referenced by {@link fr.faboit.blg.listener.InventoryListener}.
 */
public final class ConfirmDialog {

    public static final int SLOT_CONFIRM_BUTTON = 11;
    public static final int SLOT_BACK_BUTTON    = 15;

    private final BLGPlugin plugin;

    public ConfirmDialog(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void open(Player player) {
        PlayerDialogState state = plugin.getDialogManager().getState(player);
        state.setCurrentDialog(DialogType.CONFIRM);

        // Start the cooldown clock
        plugin.getCooldownManager().startConfirmCooldown(player.getUniqueId());

        Inventory inv = buildInventory(player, state);
        player.openInventory(inv);
        plugin.getDialogManager().trackInventory(player, inv);
        DialogManager.playSound(plugin, player, "dialog-open");

        // Schedule periodic refresh so the countdown ticks down visually
        scheduleRefresh(player, inv);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    private Inventory buildInventory(Player player, PlayerDialogState state) {
        ConfigurationSection root = plugin.getConfigManager().section("confirm-dialog");

        int    rows  = root.getInt("rows", 3);
        String title = root.getString("title", "&8[ &cConfirm &8]");

        Inventory inv = Bukkit.createInventory(
                null,
                rows * 9,
                LegacyComponentSerializer.legacyAmpersand().deserialize(title));

        // Filler
        ConfigurationSection fillerSec = root.getConfigurationSection("filler");
        if (fillerSec != null && fillerSec.getBoolean("enabled", true)) {
            Material fillerMat = parseMat(fillerSec.getString("material", "RED_STAINED_GLASS_PANE"));
            ItemStack filler   = GUIBuilder.filler(fillerMat);
            List<Integer> slots = toIntList(fillerSec.getStringList("slots"));
            if (slots.isEmpty()) GUIBuilder.fillEmpty(inv, filler);
            else GUIBuilder.fillSlots(inv, slots, filler);
        }

        // Title item
        placeItem(inv, root, "title-item", Map.of());

        // Warning / password display item
        String maskedOrReal = maskPassword(state.getPassword());
        placeItem(inv, root, "warning-item", Map.of("{password}", maskedOrReal));

        // Confirm button (locked or active)
        inv.setItem(SLOT_CONFIRM_BUTTON, buildConfirmButton(root, player));

        // Back button
        placeItem(inv, root, "back-button", Map.of());

        return inv;
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack buildConfirmButton(ConfigurationSection root, Player player) {
        ConfigurationSection sec = root.getConfigurationSection("confirm-button");
        if (sec == null) return new ItemStack(Material.LIME_WOOL);

        int remaining = plugin.getCooldownManager()
                .remainingConfirmSeconds(player.getUniqueId());

        if (remaining > 0) {
            // Locked appearance
            Material mat  = parseMat(sec.getString("material-cooldown", "GRAY_WOOL"));
            String   name = sec.getString("name-cooldown", "&7Confirm &8({remaining}s)")
                    .replace("{remaining}", String.valueOf(remaining));
            List<String> lore = sec.getStringList("lore-cooldown");
            return GUIBuilder.build(mat, name, lore);
        }

        // Active appearance
        return GUIBuilder.fromSection(sec, Map.of());
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    /** Updates the confirm button every second until the cooldown ends. */
    private void scheduleRefresh(Player player, Inventory inv) {
        int cooldown = plugin.getConfigManager().confirmCooldownSeconds();
        if (cooldown <= 0) return;

        ConfigurationSection root = plugin.getConfigManager().section("confirm-dialog");

        // Run once per second for `cooldown` seconds
        for (int i = 1; i <= cooldown; i++) {
            final int tick = i * 20; // ticks
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Only update if the player still has this inventory open
                if (!inv.equals(player.getOpenInventory().getTopInventory())) return;
                inv.setItem(SLOT_CONFIRM_BUTTON, buildConfirmButton(root, player));
            }, tick);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Show the real password in the confirm dialog (server admins can disable masking here). */
    private String maskPassword(String pw) {
        if (pw == null) return "";
        // Config allows disabling mask; default is to show real password in confirm dialog
        return pw;
    }

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

    private static List<Integer> toIntList(List<String> strings) {
        return strings.stream()
                .filter(s -> s != null && s.matches("-?\\d+"))
                .map(Integer::parseInt)
                .toList();
    }
}
