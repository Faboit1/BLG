package io.github.faboit1.blg;

import io.github.faboit1.blg.auth.AuthMeHook;
import io.github.faboit1.blg.command.OpenAutoCommand;
import io.github.faboit1.blg.command.OpenLoginCommand;
import io.github.faboit1.blg.command.OpenRegisterCommand;
import io.github.faboit1.blg.dialog.DialogManager;
import io.github.faboit1.blg.listener.DialogResponseListener;
import io.github.faboit1.blg.listener.PlayerJoinListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BetterLoginGui – a modern dialog-based UI layer over AuthMe.
 *
 * <p>This plugin does NOT handle authentication. It only:
 * <ul>
 *   <li>Sends Minecraft Dialog API packets to players as a login/register UI</li>
 *   <li>Forwards submitted credentials to AuthMe by dispatching commands</li>
 *   <li>Uses the AuthMe API (soft-dep) to decide whether to show login or register</li>
 * </ul>
 */
public class BLGPlugin extends JavaPlugin {

    private DialogManager dialogManager;
    private AuthMeHook authMeHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Initialise sub-systems
        this.dialogManager = new DialogManager(this);
        this.authMeHook    = new AuthMeHook(this);

        // Register commands
        registerCommand("openlogin",        new OpenLoginCommand(this));
        registerCommand("openregister",     new OpenRegisterCommand(this));
        registerCommand("openauto",         new OpenAutoCommand(this));
        registerCommand("blg_login_submit", new io.github.faboit1.blg.command.internal.LoginSubmitCommand(this));
        registerCommand("blg_register_submit", new io.github.faboit1.blg.command.internal.RegisterSubmitCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new DialogResponseListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        getLogger().info("BetterLoginGui enabled – dialog UI layer over AuthMe.");
    }

    @Override
    public void onDisable() {
        getLogger().info("BetterLoginGui disabled.");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public DialogManager getDialogManager() {
        return dialogManager;
    }

    public AuthMeHook getAuthMeHook() {
        return authMeHook;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Binds a {@link org.bukkit.command.CommandExecutor} to a command defined in plugin.yml.
     */
    private void registerCommand(String name,
                                 org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' not found in plugin.yml – skipping.");
            return;
        }
        cmd.setExecutor(executor);
    }

    /**
     * Returns a config string with legacy {@code &} colour codes translated.
     */
    public String cfg(String path) {
        String raw = getConfig().getString(path, "");
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * Shorthand: prefix + message from config.
     */
    public String msg(String key) {
        return cfg("messages.prefix") + cfg("messages." + key);
    }
}
