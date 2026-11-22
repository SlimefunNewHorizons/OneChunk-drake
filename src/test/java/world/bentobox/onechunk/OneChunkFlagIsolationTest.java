package world.bentobox.onechunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import world.bentobox.bentobox.api.flags.Flag;

/**
 * Ensures OneChunk-specific flags are strictly scoped to the OneChunk GameModeAddon so they never leak into
 * BSkyBlock, AOneBlock, or other BentoBox settings panels (ChunkBlock v1.5.1 best practice).
 */
class OneChunkFlagIsolationTest {

    @Test
    void oneChunkFlagsAreScopedOnlyToOneChunkGameMode() {
        OneChunk addon = new OneChunk();
        List<Flag> flags = List.of(
                addon.ONECHUNK_START_SAFETY,
                addon.ONECHUNK_BOSSBAR,
                addon.ONECHUNK_ACTIONBAR,
                addon.ONECHUNK_MAGIC_BLOCK);

        for (Flag flag : flags) {
            assertSame(addon, flag.getAddon(), flag.getID() + " must declare OneChunk as its owning addon");
            assertEquals(Set.of(addon), flag.getGameModes(),
                    flag.getID() + " must only appear in OneChunk settings panels");
        }
    }
}
