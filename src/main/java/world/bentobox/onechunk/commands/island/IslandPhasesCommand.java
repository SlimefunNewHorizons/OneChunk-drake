package world.bentobox.onechunk.commands.island;

import java.util.List;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.dataobjects.OneBlockIslands;
import world.bentobox.onechunk.mining.OneChunkBiome;
import world.bentobox.onechunk.panels.PhasesPanel;

public class IslandPhasesCommand extends CompositeCommand {

    private OneChunk addon;

    public IslandPhasesCommand(CompositeCommand islandCommand, String label, String[] aliases)
    {
        super(islandCommand, label, aliases);
    }

    @Override
    public void setup() {
        setDescription("onechunk.commands.phases.description");
        setOnlyPlayer(true);
        // Permission
        setPermission("phases");
        addon = getAddon();
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        if (addon.getSettings() != null && addon.getSettings().isOneChunkEnabled()) {
            int per = addon.getSettings().getOneChunkChunksPerBiome();
            StringBuilder biomes = new StringBuilder();
            OneChunkBiome[] all = OneChunkBiome.values();
            for (int i = 0; i < all.length; i++) {
                if (i > 0) {
                    biomes.append(" <dark_gray>»</dark_gray> ");
                }
                biomes.append("<yellow>").append(all[i].displayName()).append("</yellow>");
            }

            Island island = this.addon.getIslandsManager().getIsland(this.getWorld(), user);
            if (island != null) {
                OneBlockIslands obi = addon.getOneBlocksIsland(island);
                int chunkIndex = obi.getChunksCleared() + 1;
                OneChunkBiome current = OneChunkBiome.forChunk(obi.getChunksCleared(), per);
                String msg = user.getTranslationOrNothing("onechunk.commands.phases.onechunk-cycle",
                        "[cycle]", biomes.toString(),
                        "[chunk]", String.valueOf(chunkIndex),
                        "[biome]", current.displayName(),
                        "[per]", String.valueOf(per));
                if (msg.isEmpty()) {
                    user.sendMessage("<gold>OneChunk Biome Cycle</gold> <gray>(every " + per + " chunks):</gray> " + biomes);
                    user.sendMessage("<gold>Current Island:</gold> <green>Chunk #" + chunkIndex + "</green> <gray>(" + current.displayName() + ")</gray>");
                } else {
                    user.sendMessage(msg);
                }
            } else {
                String msg = user.getTranslationOrNothing("onechunk.commands.phases.onechunk-cycle-generic",
                        "[cycle]", biomes.toString(),
                        "[per]", String.valueOf(per));
                if (msg.isEmpty()) {
                    user.sendMessage("<gold>OneChunk Biome Cycle</gold> <gray>(every " + per + " chunks):</gray> " + biomes);
                } else {
                    user.sendMessage(msg);
                }
            }
            return true;
        }
        PhasesPanel.openPanel(this.addon, this.getWorld(), user);
        return true;
    }
}
