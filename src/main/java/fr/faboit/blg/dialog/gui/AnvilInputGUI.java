package fr.faboit.blg.dialog.gui;

import fr.faboit.blg.BLGPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
// InventoryType used from org.bukkit.event.inventory (standard Bukkit location)

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Uses the vanilla Anvil rename field as a single-line text input.
 *
 * A Paper plugin cannot open a native text-field dialog (those require
 * client-side resource packs or the experimental Dialog API added in
 * snapshot 25w14a which is not yet in stable Paper).  The Anvil trick is
 * the most UX-friendly alternative available in all stable Paper versions:
 * the player types the password in the "name" field and clicks the output
 * slot to confirm.
 *
 * XP cost is suppressed via {@link PrepareAnvilEvent}.
 */
public final class AnvilInputGUI implements Listener {

    private static final int SLOT_INPUT  = 0;
    private static final int SLOT_RESULT = 2;

    private final BLGPlugin plugin;

    /** Sessions keyed by player UUID. */
    private final Map<UUID, AnvilSession> sessions = new HashMap<>();

    /** UUIDs whose inventory we are closing programmatically (avoid double-callback). */
    private final java.util.Set<UUID> closingProgrammatically =
            new java.util.HashSet<>();

    public AnvilInputGUI(BLGPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Open an anvil "text field" for {@code player}.
     *
     * @param player       the player
     * @param configKey    key under {@code anvil-input.*} in config.yml
     * @param onConfirm    called (main thread) with the typed text when the
     *                     player clicks the result slot
     * @param onCancel     called (main thread) when the inventory is closed
     *                     without confirming (may be null)
     */
    public void open(Player player, String configKey,
                     Consumer<String> onConfirm, Runnable onCancel) {

        var cfg = plugin.getConfigManager();
        String path  = "anvil-input." + configKey;
        String title = cfg.raw().getString(path + ".title", "&8Text Input");
        String matName = cfg.raw().getString(path + ".item-material", "PAPER");
        String hint  = cfg.raw().getString(path + ".item-name", "&7Type here...");
        List<String> lore = cfg.raw().getStringList(path + ".item-lore");

        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.PAPER;

        AnvilSession session = new AnvilSession(onConfirm, onCancel);
        sessions.put(player.getUniqueId(), session);

        final Material finalMat = mat;
        final String finalTitle = title;
        final String finalHint = hint;
        final List<String> finalLore = lore;

        // Must open on main thread
        Runnable open = () -> openInventory(player, finalMat, finalHint, finalLore, finalTitle);
        if (Bukkit.isPrimaryThread()) {
            open.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, open);
        }
    }

    /** Remove a pending session without triggering the cancel callback. */
    public void clearSession(UUID uuid) {
        sessions.remove(uuid);
    }

    /** @return true if the player currently has an open anvil input session. */
    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void openInventory(Player player, Material material,
                                String hint, List<String> lore, String title) {
        Inventory inv = Bukkit.createInventory(
                null,
                InventoryType.ANVIL,
                LegacyComponentSerializer.legacyAmpersand().deserialize(title));

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(hint)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            if (!lore.isEmpty()) {
                meta.lore(lore.stream()
                        .map(l -> LegacyComponentSerializer.legacyAmpersand().deserialize(l)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                        .toList());
            }
            item.setItemMeta(meta);
        }
        inv.setItem(SLOT_INPUT, item);
        player.openInventory(inv);
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (!sessions.containsKey(player.getUniqueId())) return;

        // Remove XP cost so clicking the result is free
        AnvilInventory anvil = event.getInventory();
        anvil.setRepairCost(0);
        anvil.setMaximumRepairCost(Integer.MAX_VALUE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        AnvilSession session = sessions.get(uuid);
        if (session == null) return;

        // Only handle clicks in our tracked anvil inventories
        if (event.getInventory() == null) return;

        event.setCancelled(true);

        if (event.getRawSlot() == SLOT_RESULT) {
            // Player confirmed input
            ItemStack result = event.getInventory().getItem(SLOT_RESULT);
            String text = extractName(result);

            sessions.remove(uuid);
            closingProgrammatically.add(uuid);
            player.closeInventory();

            Consumer<String> callback = session.onConfirm();
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(text));
        }
        // Slots 0 and 1: just cancel to keep items in place
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        // Skip if we triggered this close ourselves
        if (closingProgrammatically.remove(uuid)) return;

        AnvilSession session = sessions.remove(uuid);
        if (session != null && session.onCancel() != null) {
            Bukkit.getScheduler().runTask(plugin, session.onCancel());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extractName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        var displayName = item.getItemMeta().displayName();
        if (displayName == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(displayName);
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private record AnvilSession(Consumer<String> onConfirm, Runnable onCancel) {}
}
