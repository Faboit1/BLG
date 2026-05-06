package fr.faboit.blg.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility helpers for building {@link ItemStack}s from configuration sections
 * and filling {@link Inventory} backgrounds.
 */
public final class GUIBuilder {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private GUIBuilder() {}

    // ── ItemStack builders ────────────────────────────────────────────────────

    /**
     * Build an ItemStack from a config section.
     *
     * Expected keys inside the section:
     * <pre>
     *   material: MATERIAL_NAME
     *   name:     "&eDisplay Name"
     *   lore:
     *     - "&7Line 1"
     * </pre>
     *
     * @param section      config section (may be null → returns STONE placeholder)
     * @param replacements placeholder map, e.g. {"{player}", "Steve"}
     */
    public static ItemStack fromSection(ConfigurationSection section,
                                        Map<String, String> replacements) {
        if (section == null) return new ItemStack(Material.STONE);

        Material mat = parseMaterial(section.getString("material", "STONE"));
        String   rawName = section.getString("name", "");
        List<String> rawLore = section.getStringList("lore");

        return build(mat, replace(rawName, replacements),
                rawLore.stream()
                        .map(l -> replace(l, replacements))
                        .collect(Collectors.toList()));
    }

    /** Build from explicit material + name + lore strings (legacy colour codes). */
    public static ItemStack build(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta  meta = Objects.requireNonNull(item.getItemMeta());

        meta.displayName(color(name));
        meta.lore(lore.stream().map(GUIBuilder::color).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    /** Build a simple named item with no lore. */
    public static ItemStack build(Material material, String name) {
        return build(material, name, List.of());
    }

    /** Filler / border item – invisible display name. */
    public static ItemStack filler(Material material) {
        return build(material, " ");
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    /**
     * Fill every empty slot of {@code inventory} with {@code fillerItem}.
     */
    public static void fillEmpty(Inventory inventory, ItemStack fillerItem) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, fillerItem);
            }
        }
    }

    /**
     * Fill specific slots with a filler item.
     */
    public static void fillSlots(Inventory inventory, List<Integer> slots, ItemStack fillerItem) {
        for (int slot : slots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, fillerItem);
            }
        }
    }

    // ── Text helpers ──────────────────────────────────────────────────────────

    /** Deserialize a legacy {@code &}-colour-coded string to a Component. */
    public static Component color(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return LEGACY.deserialize(text);
    }

    /** Apply all placeholder replacements to a string. */
    public static String replace(String text, Map<String, String> replacements) {
        if (text == null) return "";
        if (replacements == null || replacements.isEmpty()) return text;
        for (Map.Entry<String, String> e : replacements.entrySet()) {
            text = text.replace(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        return text;
    }

    /** Apply replacements to a list of strings. */
    public static List<String> replaceAll(List<String> lines, Map<String, String> replacements) {
        List<String> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(replace(l, replacements));
        return out;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static Material parseMaterial(String name) {
        if (name == null) return Material.STONE;
        Material m = Material.matchMaterial(name);
        return m != null ? m : Material.STONE;
    }
}
