package world.bentobox.onechunk.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.dataobjects.OneBlockIslands;
import world.bentobox.bentobox.api.events.island.IslandInfoEvent;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;

public class InfoListener implements Listener {

    final OneChunk addon;

    /**
     * Add info to the info command
     * @param addon - OneChunk
     */
    public InfoListener(OneChunk addon) {
        this.addon = addon;
    }

    /**
     * Save island on player quitting
     * @param e - event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInfo(IslandInfoEvent e) {
        // Check if island is in OneChunk
        if (!addon.equals(e.getAddon())) return;
        User user = e.getPlayerUUID() == null ? User.getInstance(Bukkit.getConsoleSender()) : User.getInstance(e.getPlayerUUID());
        @NonNull
        OneBlockIslands is = addon.getOneBlocksIsland(e.getIsland());
        if (is.getBlockNumber() == 0) {
            return;
        }
        user.sendMessage("onechunk.commands.info.count", TextVariables.NUMBER, String.valueOf(is.getBlockNumber()), 
                TextVariables.NAME, is.getPhaseName(),
                "[lifetime]", String.valueOf(is.getLifetime())
                );
        
    }

}
