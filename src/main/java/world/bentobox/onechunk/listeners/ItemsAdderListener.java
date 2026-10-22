package world.bentobox.onechunk.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import world.bentobox.onechunk.OneChunk;

/**
 * Handles ItemsAdderLoadDataEvent which fired when ItemsAdder loaded its data or reload its data
 *
 * @author Teenkung123
 */
public class ItemsAdderListener implements Listener {

    private final OneChunk addon;
    public ItemsAdderListener(OneChunk addon) {
        this.addon = addon;
    }

    /**
     * handle ItemsAdderLoadDataEvent then reload the addon if it's get triggered
     * @param e - ItemsAdderLoadDataEvent
     */
    @EventHandler
    public void onLoad(ItemsAdderLoadDataEvent e) {
        addon.loadData();
    }

}
