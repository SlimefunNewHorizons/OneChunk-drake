package world.bentobox.onechunk.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import world.bentobox.onechunk.OneChunk;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;

public class JoinLeaveListener implements Listener {

    final OneChunk addon;

    /**
     * Save island on player quitting
     * @param addon - OneChunk
     */
    public JoinLeaveListener(OneChunk addon) {
        this.addon = addon;
    }

    /**
     * Save island on player quitting
     * @param e - event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent e) {
        Island island = addon.getIslands().getIsland(addon.getOverWorld(), e.getPlayer().getUniqueId());
        if (island != null) {
            addon.getBlockListener().saveIsland(island).thenAccept(r -> {
                if (Boolean.FALSE.equals(r)) {
                    addon.logError("Could not save OneChunk island at " + Util.xyz(island.getCenter().toVector()) + " to database " + island.getUniqueId());
                }
            });
        }
    }

}
