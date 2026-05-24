package io.github.faboit1.blg;

import io.github.faboit1.blg.auth.AuthMeHook;
import io.github.faboit1.blg.auth.GeyserHook;
import io.github.faboit1.blg.command.OpenAutoCommand;
import io.github.faboit1.blg.command.OpenLoginCommand;
import io.github.faboit1.blg.command.OpenPreLoginCommand;
import io.github.faboit1.blg.command.OpenRegisterCommand;
import io.github.faboit1.blg.command.ReloadCommand;
import io.github.faboit1.blg.command.UpdateRulesCommand;
import io.github.faboit1.blg.command.internal.AutoChoiceCommand;
import io.github.faboit1.blg.command.internal.LoginChoiceCommand;
import io.github.faboit1.blg.command.internal.LoginSubmitCommand;
import io.github.faboit1.blg.command.internal.RegisterChoiceCommand;
import io.github.faboit1.blg.command.internal.RegisterSubmitCommand;
import io.github.faboit1.blg.command.internal.RulesAcceptCommand;
import io.github.faboit1.blg.command.internal.RulesLeaveCommand;
import io.github.faboit1.blg.command.internal.RulesPageCommand;
import io.github.faboit1.blg.dialog.DialogManager;
import io.github.faboit1.blg.flow.FlowManager;
import io.github.faboit1.blg.listener.DialogResponseListener;
import io.github.faboit1.blg.listener.PlayerJoinListener;
import io.github.faboit1.blg.listener.PlayerQuitListener;
import io.github.faboit1.blg.rules.RulesManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BetterLoginGui – a modern dialog-based UI layer over AuthMe.
 *
 * <p>This plugin does NOT handle authentication. It only:
 * <ul>
 *   <li>Sends Minecraft Dialog API packets to players as a welcome/rules/login/register UI</li>
 *   <li>Forwards submitted credentials to AuthMe by dispatching commands</li>
 *   <li>Uses the AuthMe API (soft-dep) to decide whether to show login or register</li>
 *   <li>Manages a server-rules acceptance system backed by {@code rules.txt}</li>
 * </ul>
 */
public class BLGPlugin extends JavaPlugin {

    private DialogManager dialogManager;
    private AuthMeHook    authMeHook;
    private GeyserHook    geyserHook;
    private RulesManager  rulesManager;
    private FlowManager   flowManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Initialise sub-systems (order matters: rules before flow before dialogs)
        this.rulesManager  = new RulesManager(this);
        this.flowManager   = new FlowManager(this);
        this.dialogManager = new DialogManager(this);
        this.authMeHook    = new AuthMeHook(this);
        this.geyserHook    = new GeyserHook(this);

        // Register commands
        registerCommand("openlogin",        new OpenLoginCommand(this));
        registerCommand("openregister",     new OpenRegisterCommand(this));
        registerCommand("openauto",         new OpenAutoCommand(this));
        registerCommand("openprelogin",     new OpenPreLoginCommand(this));
        registerCommand("updaterules",      new UpdateRulesCommand(this));
        registerCommand("blgreload",         new ReloadCommand(this));
        registerCommand("blg_login_submit",    new LoginSubmitCommand(this));
        registerCommand("blg_register_submit", new RegisterSubmitCommand(this));
        registerCommand("blg_login_choice",    new LoginChoiceCommand(this));
        registerCommand("blg_register_choice", new RegisterChoiceCommand(this));
        registerCommand("blg_auto_choice",     new AutoChoiceCommand(this));
        registerCommand("blg_rules_accept",    new RulesAcceptCommand(this));
        registerCommand("blg_rules_leave",     new RulesLeaveCommand(this));
        registerCommand("blg_rules_page",      new RulesPageCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new DialogResponseListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);

        if (isDebugMode()) {
            getLogger().info("[DEBUG] Debug mode is ON.");
            getLogger().info("[DEBUG] velocity-backend = " + getConfig().getBoolean("velocity-backend", false));
            getLogger().info("[DEBUG] autojoinlogingui  = " + getConfig().getBoolean("autojoinlogingui",
                    getConfig().getBoolean("auto-join-login-gui",
                            getConfig().getBoolean("auto-open-on-join", false))));
            getLogger().info("[DEBUG] skip-online-mode-players = "
                    + getConfig().getBoolean("skip-online-mode-players", true));
            getLogger().info("[DEBUG] join-dialog-delay-ticks  = "
                    + getConfig().getLong("join-dialog-delay-ticks", 20L));
            getLogger().info("[DEBUG] join-choice-timeout-ticks = "
                    + getConfig().getLong("join-choice-timeout-ticks", 160L));
            getLogger().info("[DEBUG] join-choice-spam-interval-ticks = "
                    + getConfig().getLong("join-choice-spam-interval-ticks", 2L));
        }

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

    public GeyserHook getGeyserHook() {
        return geyserHook;
    }

    public RulesManager getRulesManager() {
        return rulesManager;
    }

    public FlowManager getFlowManager() {
        return flowManager;
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
     * Returns {@code true} when debug mode is enabled in config.
     */
    public boolean isDebugMode() {
        return getConfig().getBoolean("debug", false);
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
