package fr.faboit.blg;

import fr.faboit.blg.authme.AuthMeWrapper;
import fr.faboit.blg.command.BLGReloadCommand;
import fr.faboit.blg.command.OpenAutoCommand;
import fr.faboit.blg.command.OpenLoginCommand;
import fr.faboit.blg.command.OpenRegisterCommand;
import fr.faboit.blg.config.ConfigManager;
import fr.faboit.blg.cooldown.CooldownManager;
import fr.faboit.blg.dialog.DialogManager;
import fr.faboit.blg.listener.ChatListener;
import fr.faboit.blg.listener.InventoryListener;
import fr.faboit.blg.listener.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

/**
 * BetterLoginGui – Main plugin class.
 *
 * Wires all components together; does NOT perform any authentication logic
 * itself. Authentication is delegated entirely to AuthMe.
 */
public final class BLGPlugin extends JavaPlugin {

    private static BLGPlugin instance;

    private ConfigManager configManager;
    private DialogManager dialogManager;
    private AuthMeWrapper authMeWrapper;
    private CooldownManager cooldownManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default configuration
        saveDefaultConfig();

        // Initialize components
        this.configManager   = new ConfigManager(this);
        this.cooldownManager = new CooldownManager(this);
        this.authMeWrapper   = new AuthMeWrapper(this);
        this.dialogManager   = new DialogManager(this);

        // Register commands
        registerCommand("openlogin",    new OpenLoginCommand(this));
        registerCommand("openregister", new OpenRegisterCommand(this));
        registerCommand("openauto",     new OpenAutoCommand(this));
        registerCommand("blgreload",    new BLGReloadCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        getLogger().info("BetterLoginGui v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (dialogManager != null) {
            dialogManager.closeAllDialogs();
        }
        getLogger().info("BetterLoginGui disabled.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd == null) {
            getLogger().log(Level.WARNING, "Command /{0} not found in plugin.yml!", name);
            return;
        }
        cmd.setExecutor(executor);
    }

    /** Reload configuration and refresh all managers. */
    public void reloadPlugin() {
        reloadConfig();
        configManager.reload();
        dialogManager.closeAllDialogs();
        getLogger().info("BLG reloaded.");
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static BLGPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DialogManager getDialogManager() {
        return dialogManager;
    }

    public AuthMeWrapper getAuthMeWrapper() {
        return authMeWrapper;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
}
