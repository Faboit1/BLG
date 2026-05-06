package fr.faboit.blg.config;

import fr.faboit.blg.BLGPlugin;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Typed, null-safe wrapper around the plugin's config.yml.
 *
 * All keys are read through this class so that call-sites never
 * have to know config paths.  Default values are always provided.
 */
public final class ConfigManager {

    private final BLGPlugin plugin;
    private FileConfiguration cfg;

    public ConfigManager(BLGPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.cfg = plugin.getConfig();
    }

    // ── General ──────────────────────────────────────────────────────────────

    public boolean isDebug()              { return cfg.getBoolean("general.debug",                false); }
    public boolean closeOnWorldChange()   { return cfg.getBoolean("general.close-on-world-change", true); }
    public boolean closeOnDisconnect()    { return cfg.getBoolean("general.close-on-disconnect",   true); }
    public boolean asyncAuthmeCheck()     { return cfg.getBoolean("general.async-authme-check",    true); }
    public boolean passwordMaskEnabled()  { return cfg.getBoolean("general.password-field-mask",   true); }

    // ── AuthMe ────────────────────────────────────────────────────────────────

    public boolean useAuthMeApi()         { return cfg.getBoolean("authme.use-api", true); }
    public String  authmeCheckCommand()   { return cfg.getString("authme.check-command",
                                                "authme check {player}"); }
    public String  loginCommand()         { return cfg.getString("authme.commands.login",
                                                "login {password}"); }
    public String  registerCommand()      { return cfg.getString("authme.commands.register",
                                                "register {password} {confirm}"); }

    // ── Error detection ───────────────────────────────────────────────────────

    public boolean errorDetectionEnabled() { return cfg.getBoolean("error-detection.enabled", true); }

    public List<String> errorPatterns()  {
        return cfg.getStringList("error-detection.patterns");
    }

    public List<String> errorPrefixes()  {
        return cfg.getStringList("error-detection.prefixes");
    }

    // ── Cooldown ──────────────────────────────────────────────────────────────

    public int confirmCooldownSeconds()   { return cfg.getInt("cooldown.confirm-seconds", 5); }

    // ── Messages ──────────────────────────────────────────────────────────────

    public String prefix()                { return cfg.getString("messages.prefix", "&8[&bBLG&8] "); }

    public String msg(String subPath)     {
        String v = cfg.getString("messages." + subPath);
        return v != null ? v : "";
    }

    // ── Sounds ────────────────────────────────────────────────────────────────

    public boolean soundsEnabled()        { return cfg.getBoolean("sounds.enabled", true); }

    /** Returns the Sound enum for the given key, or null if disabled/invalid. */
    public Sound sound(String key) {
        if (!soundsEnabled()) return null;
        String name = cfg.getString("sounds." + key + ".sound");
        if (name == null) return null;
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Invalid sound ''{0}'' at sounds.{1}.sound", new Object[]{name, key});
            return null;
        }
    }

    public float soundVolume(String key)  { return (float) cfg.getDouble("sounds." + key + ".volume", 0.7); }
    public float soundPitch(String key)   { return (float) cfg.getDouble("sounds." + key + ".pitch",  1.0); }

    // ── Config sections ───────────────────────────────────────────────────────

    /**
     * Returns the ConfigurationSection at the given path, or an empty
     * dummy section if the path is missing.
     */
    public ConfigurationSection section(String path) {
        ConfigurationSection s = cfg.getConfigurationSection(path);
        if (s == null) {
            plugin.getLogger().log(Level.WARNING, "Missing config section: {0}", path);
            // Return a detached (never-null) section so callers don't NPE
            return cfg.createSection("__missing__." + path);
        }
        return s;
    }

    public FileConfiguration raw() {
        return cfg;
    }
}
