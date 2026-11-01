package world.bentobox.onechunk.commands.island;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import world.bentobox.bentobox.api.addons.AddonDescription;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.onechunk.CommonTestSetup;
import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.Settings;
import world.bentobox.onechunk.dataobjects.OneBlockIslands;
import world.bentobox.onechunk.oneblocks.OneBlocksManager;

/**
 * Test class
 */
class IslandPhasesCommandTest extends CommonTestSetup {

    @Mock
    private CompositeCommand ac;
    @Mock
    private User user;
    @Mock
    private OneChunk addon;
    @Mock
    private OneBlocksManager obm;
    @Mock
    private Island island;
    @Mock
    private OneBlockIslands oneBlockIsland;

    private Settings settings;
    private IslandPhasesCommand ipc;

    /**
     */
    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        when(ac.getAddon()).thenReturn(addon);
        when(obm.getBlockProbs()).thenReturn(new TreeMap<>());
        when(addon.getOneBlockManager()).thenReturn(obm);
        when(addon.getIslandsManager()).thenReturn(im);
        AddonDescription desc = new AddonDescription.Builder("name", "", "").build();
        when(addon.getDescription()).thenReturn(desc);

        // Settings
        settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);
        ipc = new IslandPhasesCommand(ac, settings.getPhasesCommand().split(" ")[0],
                settings.getPhasesCommand().split(" "));
    }

    /**
     */
    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test method for {@link world.bentobox.onechunk.commands.island.IslandPhasesCommand#IslandPhasesCommand(world.bentobox.bentobox.api.commands.CompositeCommand, java.lang.String, java.lang.String[])}.
     */
    @Test
    void testIslandPhasesCommand() {
        assertNotNull(ipc);
    }

    /**
     * Test method for {@link world.bentobox.onechunk.commands.island.IslandPhasesCommand#setup()}.
     */
    @Test
    void testSetup() {
        assertEquals("phases", ipc.getPermission());
        assertEquals("onechunk.commands.phases.description", ipc.getDescription());
        assertTrue(ipc.isOnlyPlayer());
    }

    /**
     * Test execute in legacy mode when there are no phases.
     */
    @Test
    void testExecuteLegacyNoPhases() {
        settings.setOneChunkEnabled(false);
        ipc.execute(user, "", List.of());
        verify(addon).logError("There are no available phases for selection!");
        verify(user).sendMessage("no-phases",
                TextVariables.GAMEMODE, this.addon.getDescription().getName());
    }

    /**
     * Test execute in OneChunk mode with an existing island.
     */
    @Test
    void testExecuteOneChunkModeWithIsland() {
        settings.setOneChunkEnabled(true);
        when(im.getIsland(any(), any(User.class))).thenReturn(island);
        when(addon.getOneBlocksIsland(island)).thenReturn(oneBlockIsland);
        when(oneBlockIsland.getChunksCleared()).thenReturn(2);
        when(user.getTranslationOrNothing(anyString(), any(String[].class))).thenReturn("");

        assertTrue(ipc.execute(user, "phases", List.of()));
        verify(user).sendMessage(contains("OneChunk Biome Cycle"));
        verify(user).sendMessage(contains("Chunk #3"));
    }

    /**
     * Test execute in OneChunk mode without an island.
     */
    @Test
    void testExecuteOneChunkModeWithoutIsland() {
        settings.setOneChunkEnabled(true);
        when(im.getIsland(any(), any(User.class))).thenReturn(null);
        when(user.getTranslationOrNothing(anyString(), any(String[].class))).thenReturn("");

        assertTrue(ipc.execute(user, "phases", List.of()));
        verify(user).sendMessage(contains("OneChunk Biome Cycle"));
    }
}
