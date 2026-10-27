package world.bentobox.onechunk.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.eclipse.jdt.annotation.NonNull;

import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.dataobjects.OneBlockIslands;
import world.bentobox.onechunk.mining.OneChunkBiome;
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
     * Add info to the info command
     * @param e - event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInfo(IslandInfoEvent e) {
        // Check if island is in OneChunk
        if (!addon.equals(e.getAddon())) return;
        User user = e.getPlayerUUID() == null ? User.getInstance(Bukkit.getConsoleSender()) : User.getInstance(e.getPlayerUUID());
        @NonNull
        OneBlockIslands is = addon.getOneBlocksIsland(e.getIsland());

        if (addon.getSettings() != null && addon.getSettings().isOneChunkEnabled()) {
            int chunkIndex = is.getChunksCleared() + 1;
            int per = addon.getSettings().getOneChunkChunksPerBiome();
            OneChunkBiome biome = OneChunkBiome.forChunk(is.getChunksCleared(), per);
            int solid = Math.max(1, is.getSolidInChunk());
            int broken = is.getBrokenInChunk();
            double clearFraction = addon.getSettings().getOneChunkClearFraction();
            int targetBlocks = (int) Math.ceil(solid * clearFraction);
            int percent = (int) Math.min(100, Math.round(((double) broken / targetBlocks) * 100));

            String msg = user.getTranslationOrNothing("onechunk.commands.info.onechunk-count",
                    "[chunk]", String.valueOf(chunkIndex),
                    "[biome]", biome.displayName(),
                    "[broken]", String.valueOf(broken),
                    "[target]", String.valueOf(targetBlocks),
                    "[total-cleared]", String.valueOf(is.getChunksCleared()),
                    "[lifetime]", String.valueOf(is.getLifetime()),
                    "[percent]", percent + "%");
            if (msg.isEmpty()) {
                user.sendMessage("onechunk.commands.info.count",
                        TextVariables.NUMBER, String.valueOf(chunkIndex),
                        TextVariables.NAME, biome.displayName(),
                        "[lifetime]", String.valueOf(is.getLifetime()));
            } else {
                user.sendMessage(msg);
            }
            return;
        }

        if (is.getBlockNumber() == 0) {
            return;
        }
        user.sendMessage("onechunk.commands.info.count", TextVariables.NUMBER, String.valueOf(is.getBlockNumber()), 
                TextVariables.NAME, is.getPhaseName(),
                "[lifetime]", String.valueOf(is.getLifetime())
                );
        
    }

}
