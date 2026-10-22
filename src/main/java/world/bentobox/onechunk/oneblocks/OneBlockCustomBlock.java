package world.bentobox.onechunk.oneblocks;

import org.bukkit.block.Block;

import world.bentobox.onechunk.OneChunk;

/**
 * Represents a custom block with custom executable
 *
 * @author HSGamer
 */
public interface OneBlockCustomBlock {
    /**
     * Executes the custom logic
     *
     * @param block the block
     */
    void execute(OneChunk addon, Block block);
}
