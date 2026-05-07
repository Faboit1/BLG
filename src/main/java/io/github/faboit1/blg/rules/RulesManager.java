package io.github.faboit1.blg.rules;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages the server rules system.
 *
 * <ul>
 *   <li>Loads {@code rules.txt} from the plugin data folder (with {@code &} colour support).</li>
 *   <li>On reload ({@link #reloadRules()}): backs up {@code rules.txt} → {@code rules.txt.old},
 *       detects changes by hash comparison, and invalidates all accepted records when changed.</li>
 *   <li>Persists per-player acceptance in {@code accepted-rules.yml} so players only need to
 *       re-accept when the rules actually change.</li>
 * </ul>
 */
public class RulesManager {

    private final BLGPlugin plugin;

    /** Raw lines read from rules.txt (colour codes NOT yet translated). */
    private List<String> rawLines = new ArrayList<>();

    /**
     * Hash of the current rules content – used to detect changes and to
     * compare against what individual players last accepted.
     */
    private String currentHash = "";

    /** playerName (lower-case) → hash they last accepted. */
    private final Map<String, String> acceptedHashes = new HashMap<>();

    private File acceptedFile;

    public RulesManager(BLGPlugin plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        acceptedFile = new File(plugin.getDataFolder(), "accepted-rules.yml");

        // Copy default rules.txt on first run
        File rulesFile = new File(plugin.getDataFolder(), "rules.txt");
        if (!rulesFile.exists()) {
            plugin.saveResource("rules.txt", false);
        }

        loadRulesFile();
        loadAccepted();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Reloads {@code rules.txt}, backs it up to {@code rules.txt.old}, and
     * invalidates all acceptance records when the content changed.
     *
     * @return {@code true} if the rules content changed
     */
    public boolean reloadRules() {
        File dataDir   = plugin.getDataFolder();
        File rulesFile = new File(dataDir, "rules.txt");

        if (!rulesFile.exists()) {
            plugin.getLogger().warning("rules.txt not found – nothing to reload.");
            return false;
        }

        String previousHash = this.currentHash;

        // Back up rules.txt → rules.txt.old (replace if already exists)
        File oldFile = new File(dataDir, "rules.txt.old");
        if (oldFile.exists()) {
            oldFile.delete();
        }
        try {
            Files.copy(rulesFile.toPath(), oldFile.toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not back up rules.txt to rules.txt.old", e);
        }

        loadRulesFile();

        boolean changed = !currentHash.equals(previousHash) && !previousHash.isEmpty();
        if (changed) {
            // Wipe all stored acceptances so every player must re-accept on next join
            acceptedHashes.clear();
            saveAccepted();
            plugin.getLogger().info("Rules changed – all players will need to re-accept on next join.");
        }
        return changed;
    }

    /**
     * Returns {@code true} when the given player must accept the current rules
     * before proceeding (rules feature enabled, non-empty rules, and player has
     * not yet accepted the current version).
     */
    public boolean needsToAccept(Player player) {
        if (!plugin.getConfig().getBoolean("rules.enabled", false)) {
            return false;
        }
        if (rawLines.isEmpty()) {
            return false;
        }
        String accepted = acceptedHashes.get(player.getName().toLowerCase(Locale.ROOT));
        return !currentHash.equals(accepted);
    }

    /**
     * Records that the given player has accepted the current rules version.
     */
    public void markAccepted(Player player) {
        acceptedHashes.put(player.getName().toLowerCase(Locale.ROOT), currentHash);
        saveAccepted();
    }

    /**
     * Returns all rules lines with {@code &} colour codes translated.
     * Comment lines (starting with {@code #}) are excluded.
     */
    public List<String> getFormattedLines() {
        List<String> out = new ArrayList<>(rawLines.size());
        for (String line : rawLines) {
            out.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return out;
    }

    /** Number of rules lines (excluding comments). */
    public int getLineCount() {
        return rawLines.size();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void loadRulesFile() {
        File rulesFile = new File(plugin.getDataFolder(), "rules.txt");
        try {
            List<String> all = Files.readAllLines(rulesFile.toPath(), StandardCharsets.UTF_8);
            rawLines = new ArrayList<>();
            for (String line : all) {
                // Skip comment lines
                if (!line.trim().startsWith("#")) {
                    rawLines.add(line);
                }
            }
            currentHash = sha256(String.join("\n", rawLines));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read rules.txt", e);
            rawLines = new ArrayList<>();
            currentHash = "";
        }
    }

    /** Computes a stable SHA-256 hex digest of the given content. */
    private static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by the Java SE spec – should never happen
            return String.valueOf(content.hashCode());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadAccepted() {
        if (!acceptedFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(acceptedFile);
        Object raw = cfg.get("accepted");
        if (raw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String k && entry.getValue() instanceof String v) {
                    acceptedHashes.put(k, v);
                }
            }
        }
    }

    private void saveAccepted() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("accepted", new HashMap<>(acceptedHashes));
        try {
            cfg.save(acceptedFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save accepted-rules.yml", e);
        }
    }
}
