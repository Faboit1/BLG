package io.github.faboit1.blg.flow;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the per-player login flow and dialog-spam tasks.
 *
 * <h2>Flow stages</h2>
 * <ol>
 *   <li><b>RULES</b> – player must accept the current rules (if enabled and not
 *       yet accepted).  A dialog is spammed every 100 ms with a 15-second
 *       countdown before the Accept / Leave buttons become effective.</li>
 *   <li><b>CHOICE</b> – player sees a one-button "Register / Login" dialog
 *       spammed every 100 ms until clicked or until the timeout is reached.</li>
 *   <li><b>AUTH</b> – the actual login or register dialog has been opened.  No
 *       more spam; AuthMe takes over from here.</li>
 * </ol>
 *
 * <p>All spam tasks are cancelled automatically when the player quits or when
 * {@link #stopFlow(Player)} is called explicitly.
 */
public class FlowManager {

    /** How often (in ticks) to re-send the current dialog.  2 ticks ≈ 100 ms. */
    private static final long SPAM_INTERVAL_TICKS = 2L;

    /** Default number of rules lines shown per page when the page system is enabled. */
    private static final int DEFAULT_LINES_PER_PAGE = 10;

    private final BLGPlugin plugin;

    /** Active spam task per player. */
    private final Map<UUID, BukkitTask> spamTasks = new ConcurrentHashMap<>();

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
     * Starts the login flow for a player.  Begins with the rules stage if rules
     * need to be accepted, otherwise skips straight to the choice stage.
     */
    public void startFlow(Player player) {
        stopFlow(player); // cancel any previous task first

        if (plugin.getRulesManager().needsToAccept(player)) {
            startRulesStage(player);
        } else {
            startChoiceStage(player);
        }
    }

    /**
     * Begins the rules-spam stage.  Called either by {@link #startFlow} or by
     * a page-navigation command after the stage is already active (to restart
     * the task with the updated page).
     */
    public void startRulesStage(Player player) {
        stopFlow(player);
        rulesShownAt.putIfAbsent(player.getUniqueId(), System.currentTimeMillis());
        rulesPage.putIfAbsent(player.getUniqueId(), 0);

        int waitSeconds = plugin.getConfig().getInt("rules.wait-seconds", 15);

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopFlow(player);
                return;
            }
            Long shownAt = rulesShownAt.get(player.getUniqueId());
            if (shownAt == null) {
                // Should never happen, but guard against it to avoid incorrect canAct state
                stopFlow(player);
                return;
            }
            long elapsed    = System.currentTimeMillis() - shownAt;
            boolean canAct  = elapsed >= (long) waitSeconds * 1000L;
            int secondsLeft = canAct ? 0 : (int) Math.max(0, waitSeconds - elapsed / 1000L);
            int page        = rulesPage.getOrDefault(player.getUniqueId(), 0);
            plugin.getDialogManager().openRulesDialog(player, page, canAct, secondsLeft);
        }, SPAM_INTERVAL_TICKS, SPAM_INTERVAL_TICKS);

        spamTasks.put(player.getUniqueId(), task);
    }

    /**
     * Begins the unified on-join choice-dialog spam stage.
     *
     * <p>The same one-button dialog is shown regardless of registration state.
     * Clicking that button stops spam and opens the real auth dialog selected
     * automatically by AuthMe state.
     */
    public void startChoiceStage(Player player) {
        stopFlow(player);
        long timeoutTicks = plugin.getConfig().getLong("join-choice-timeout-ticks", 160L);
        if (timeoutTicks < 1L) {
            timeoutTicks = 160L;
        }
        long timeoutMillis = timeoutTicks * 50L;
        long shownAt = System.currentTimeMillis();

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopFlow(player);
                return;
            }
            if (System.currentTimeMillis() - shownAt >= timeoutMillis) {
                stopFlow(player);
                return;
            }
            plugin.getDialogManager().openJoinAutoChoiceDialog(player);
        }, SPAM_INTERVAL_TICKS, SPAM_INTERVAL_TICKS);

        spamTasks.put(player.getUniqueId(), task);
    }

    /**
     * Stops all active flow tasks for the player and cleans up state.
     * Safe to call even if no flow is active.
     */
    public void stopFlow(Player player) {
        BukkitTask task = spamTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
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
}
