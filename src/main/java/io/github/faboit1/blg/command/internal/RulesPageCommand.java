package io.github.faboit1.blg.command.internal;

import io.github.faboit1.blg.BLGPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Internal command: {@code /blg_rules_page <page>}
 *
 * <p>Triggered when the player clicks a page-navigation button (Previous /
 * Next) on the rules dialog.  Updates the player's current page in the
 * {@link io.github.faboit1.blg.flow.FlowManager} so the spam task will
 * immediately render the new page on its next tick.
 */
public class RulesPageCommand implements CommandExecutor {

    private final BLGPlugin plugin;

    public RulesPageCommand(BLGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (args.length < 1) {
            return true;
        }

        int requestedPage;
        try {
            requestedPage = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            return true;
        }

        int totalPages = plugin.getFlowManager().getTotalPages();
        // Clamp to valid range
        requestedPage = Math.max(0, Math.min(requestedPage, totalPages - 1));
        plugin.getFlowManager().setRulesPage(player, requestedPage);

        return true;
    }
}
