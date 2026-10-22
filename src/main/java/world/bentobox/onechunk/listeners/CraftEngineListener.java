package world.bentobox.onechunk.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import world.bentobox.onechunk.OneChunk;

/**
 * Handles CraftEngineReloadEvent which is fired when CraftEngine loads or reloads its data
 */
public class CraftEngineListener implements Listener {

    private final OneChunk addon;

    public CraftEngineListener(OneChunk addon) {
        this.addon = addon;
    }

    /**
     * Handle CraftEngineReloadEvent and reload the addon when triggered
     * @param e - CraftEngineReloadEvent
     */
    @EventHandler
    public void onReload(CraftEngineReloadEvent e) {
        addon.loadData();
    }
}
