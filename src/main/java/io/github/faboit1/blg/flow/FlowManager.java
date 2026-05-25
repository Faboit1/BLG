package io.github.faboit1.blg.flow;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the per-player login flow and dialog tasks.
 *
 * <h2>Flow stages</h2>
 * <ol>
 *   <li><b>RULES</b> – player must accept the current rules (if enabled and not
 *       yet accepted).  The rules dialog is sent <em>once</em> with the Accept
 *       button grayed out (disabled via the Paper Dialog API).  After the
 *       configured wait time a single scheduled task re-sends the dialog with
 *       the Accept button enabled.  No repeated spam is used, so the player can
 *       freely scroll the rules text.</li>
 *   <li><b>CHOICE</b> – player sees a one-button "Open Login" or
 *       "Open Register" dialog (based on AuthMe registration state), spammed
 *       every 100 ms until clicked or until the timeout is reached.</li>
 *   <li><b>AUTH</b> – the actual login or register dialog has been opened.  No
 *       more spam; AuthMe takes over from here.</li>
 * </ol>
 *
 * <p>All tasks are cancelled automatically when the player quits or when
 * {@link #stopFlow(Player)} is called explicitly.
 */
public class FlowManager {

    /** How often (in ticks) to re-send the choice dialog.  2 ticks ≈ 100 ms. */
    private static final long DEFAULT_SPAM_INTERVAL_TICKS = 2L;

    /** Default number of rules lines shown per page when the page system is enabled. */
    private static final int DEFAULT_LINES_PER_PAGE = 10;

    private final BLGPlugin plugin;

    /** Active choice-stage spam task per player. */
    private final Map<UUID, BukkitTask> spamTasks = new ConcurrentHashMap<>();

    /**
     * Delayed task that re-sends the rules dialog with the Accept button
     * enabled after the mandatory wait time elapses.  One entry per player,
     * cancelled on quit or flow stop.
     */
    private final Map<UUID, BukkitTask> unlockTasks = new ConcurrentHashMap<>();

    /**
     * The login/register action the player intended when the rules dialog
     * interrupted them.  Populated by the choice-button commands; consumed and
     * cleared by {@link RulesAcceptCommand} after the player accepts.
     */
    private final Map<UUID, PendingAction> pendingActions = new ConcurrentHashMap<>();

    /** System-time (ms) when the rules dialog was first shown to this player. */
    private final Map<UUID, Long> rulesShownAt = new ConcurrentHashMap<>();

    /** Current rules page index (0-based) per player. */
    private final Map<UUID, Integer> rulesPage = new ConcurrentHashMap<>();

    public FlowManager(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Starts the login flow for a player by showing the choice dialog
     * immediately.  Rules are shown only when the player clicks Login or
     * Register and has not yet accepted the current rules version.
     */
    public void startFlow(Player player) {
        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] startFlow for " + player.getName());
        }
        stopFlow(player); // cancel any previous task first
        startChoiceStage(player);
    }

    /**
     * Starts a direct login flow that skips the choice-dialog stage and opens
     * the actual login or register dialog immediately.  Used when
     * {@code open-before-ingame: true} is set in config.
     */
    public void startDirectFlow(Player player) {
        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] startDirectFlow for " + player.getName());
        }
        stopFlow(player);
        plugin.getDialogManager().openAutoAuthDialog(player);
    }

    /**
     * Begins the rules stage for a player.
     *
     * <p>The rules dialog is sent <em>once</em> immediately with the Accept
     * button disabled (grayed out via the Paper Dialog API).  A single
     * {@code runTaskLater} task is scheduled to re-send the dialog with the
     * Accept button enabled after {@code rules.wait-seconds} have elapsed.
     * No periodic spam is used, so players can freely scroll the rules text.
     */
    public void startRulesStage(Player player) {
        stopFlow(player);
        rulesShownAt.putIfAbsent(player.getUniqueId(), System.currentTimeMillis());
        rulesPage.putIfAbsent(player.getUniqueId(), 0);

        int waitSeconds = plugin.getConfig().getInt("rules.wait-seconds", 15);
        int page        = rulesPage.getOrDefault(player.getUniqueId(), 0);
        boolean canAct  = (waitSeconds <= 0);

        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] startRulesStage for " + player.getName()
                    + " | page=" + page + " | waitSeconds=" + waitSeconds + " | canAct=" + canAct);
        }

        // Send dialog once – Accept button is disabled until the wait elapses
        plugin.getDialogManager().openRulesDialog(player, page, canAct);

        if (!canAct) {
            // Schedule a single task to re-enable the Accept button after the wait
            BukkitTask unlock = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                unlockTasks.remove(player.getUniqueId());
                if (!player.isOnline()) return;
                int currentPage = rulesPage.getOrDefault(player.getUniqueId(), 0);
                plugin.getDialogManager().openRulesDialog(player, currentPage, true);
            }, (long) waitSeconds * 20L);
            unlockTasks.put(player.getUniqueId(), unlock);
        }
    }

    /**
     * Begins the on-join choice-dialog spam stage.
     *
     * <p>A one-button "Open Login" or "Open Register" dialog is chosen from
     * current AuthMe registration state and re-opened repeatedly until clicked
     * or timeout.
     */
    public void startChoiceStage(Player player) {
        stopFlow(player);
        long timeoutTicks = plugin.getConfig().getLong("join-choice-timeout-ticks", 160L);
        if (timeoutTicks < 1L) {
            timeoutTicks = 160L;
        }
        long spamIntervalTicks = plugin.getConfig().getLong("join-choice-spam-interval-ticks",
                DEFAULT_SPAM_INTERVAL_TICKS);
        if (spamIntervalTicks < 1L) {
            spamIntervalTicks = DEFAULT_SPAM_INTERVAL_TICKS;
        }
        long timeoutMillis = timeoutTicks * 50L;
        long shownAt = System.currentTimeMillis();
        boolean authMeHooked = plugin.getAuthMeHook().isHooked();

        if (plugin.isDebugMode()) {
            plugin.getLogger().info("[DEBUG] startChoiceStage for " + player.getName()
                    + " | timeoutTicks=" + timeoutTicks
                    + " | spamIntervalTicks=" + spamIntervalTicks
                    + " | authMeHooked=" + authMeHooked);
        }

        final long finalSpamIntervalTicks = spamIntervalTicks;
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopFlow(player);
                return;
            }
            if (System.currentTimeMillis() - shownAt >= timeoutMillis) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info("[DEBUG] Choice dialog timed out for " + player.getName());
                }
                stopFlow(player);
                return;
            }
            boolean needsRegistration =
                    authMeHooked && !plugin.getAuthMeHook().isRegistered(player);
            if (needsRegistration) {
                plugin.getDialogManager().openRegisterChoiceDialog(player);
            } else {
                plugin.getDialogManager().openLoginChoiceDialog(player);
            }
        }, finalSpamIntervalTicks, finalSpamIntervalTicks);

        spamTasks.put(player.getUniqueId(), task);
    }

    /**
     * Stops all active flow tasks for the player and cleans up state.
     * Safe to call even if no flow is active.
     */
    public void stopFlow(Player player) {
        BukkitTask task = spamTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        BukkitTask unlock = unlockTasks.remove(player.getUniqueId());
        if (unlock != null) unlock.cancel();
        // Do NOT remove rulesShownAt here – we need it to stay accurate if
        // the stage restarts (e.g. page navigation).  Full state is cleared
        // in clearPlayer() when the player exits the rules stage entirely.
    }

    /**
     * Fully clears all state for a player (call on quit or on flow completion).
     */
    public void clearPlayer(Player player) {
        stopFlow(player);
        rulesShownAt.remove(player.getUniqueId());
        rulesPage.remove(player.getUniqueId());
        pendingActions.remove(player.getUniqueId());
    }

    /**
     * Returns {@code true} if the player has been on the rules screen long
     * enough to act (accept / leave).
     */
    public boolean canActOnRules(Player player) {
        Long shownAt    = rulesShownAt.get(player.getUniqueId());
        if (shownAt == null) return false;
        int waitSeconds = plugin.getConfig().getInt("rules.wait-seconds", 15);
        return System.currentTimeMillis() - shownAt >= (long) waitSeconds * 1000L;
    }

    /** Returns {@code true} if the player currently has an active flow task. */
    public boolean isInFlow(Player player) {
        return spamTasks.containsKey(player.getUniqueId());
    }

    /** Updates the rules page for this player (used by page-nav commands). */
    public void setRulesPage(Player player, int page) {
        rulesPage.put(player.getUniqueId(), page);
        // Resend the dialog immediately with the new page content, preserving
        // the current canAct state so the disabled/enabled button stays correct.
        boolean canAct = canActOnRules(player);
        plugin.getDialogManager().openRulesDialog(player, page, canAct);
    }

    /** Returns the current rules page index (0-based) for this player. */
    public int getRulesPage(Player player) {
        return rulesPage.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Returns the total number of pages for the current rules content based on
     * the configured lines-per-page value.
     */
    public int getTotalPages() {
        int lineCount    = plugin.getRulesManager().getLineCount();
        int linesPerPage = plugin.getConfig().getInt("rules.pages.lines-per-page", DEFAULT_LINES_PER_PAGE);
        if (linesPerPage < 1) linesPerPage = DEFAULT_LINES_PER_PAGE;
        return Math.max(1, (int) Math.ceil((double) lineCount / linesPerPage));
    }

    /**
     * Returns the subset of formatted rules lines that should be shown on the
     * given page.
     */
    public List<String> getLinesForPage(int page) {
        List<String> all         = plugin.getRulesManager().getFormattedLines();
        boolean pagesEnabled     = plugin.getConfig().getBoolean("rules.pages.enabled", false);
        if (!pagesEnabled) {
            return all;
        }
        int linesPerPage = plugin.getConfig().getInt("rules.pages.lines-per-page", DEFAULT_LINES_PER_PAGE);
        if (linesPerPage < 1) linesPerPage = DEFAULT_LINES_PER_PAGE;
        int totalPages = Math.max(1, (int) Math.ceil((double) all.size() / linesPerPage));
        // Clamp page to valid range
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = safePage * linesPerPage;
        int to   = Math.min(from + linesPerPage, all.size());
        return all.subList(from, to);
    }

    /**
     * Records what action the player intended (LOGIN or REGISTER) before the
     * rules dialog interrupted them.  Consumed by {@code RulesAcceptCommand}.
     */
    public void setPendingAction(Player player, PendingAction action) {
        pendingActions.put(player.getUniqueId(), action);
    }

    /**
     * Returns the pending action for this player, or {@code null} if none is
     * stored (e.g. the rules were shown at join rather than after a button
     * click).
     */
    public PendingAction getPendingAction(Player player) {
        return pendingActions.get(player.getUniqueId());
    }

    /** Removes the pending action for this player. */
    public void clearPendingAction(Player player) {
        pendingActions.remove(player.getUniqueId());
    }
}
