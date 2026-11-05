package world.bentobox.onechunk.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.world.WorldMock;

import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.Settings;
import world.bentobox.onechunk.dataobjects.OneBlockIslands;

/** Regression for #567: exercise configuration, generation and depletion together. */
class OneChunkDepthTest {
    private World world;

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
        WorldMock columnWorld = spy(new WorldMock(Material.AIR, -64, 320, -64));
        columnWorld.setName("depth-regression");
        // MockBukkit does not implement the unrelated mob despawn policy.
        doReturn(mock(LivingEntity.class)).when(columnWorld).spawnEntity(any(Location.class), any(EntityType.class));
        server.addWorld(columnWorld);
        world = columnWorld;
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @ParameterizedTest
    @CsvSource({ "0, -64", "12, 68", "384, -64" })
    void generatedColumnAndDepletionUseTheSameDepth(int configuredDepth, int expectedFloor) {
        assertEquals(-64, world.getMinHeight());
        Settings settings = new Settings();
        settings.setOneChunkDepth(configuredDepth);
        OneChunk addon = mock(OneChunk.class);
        when(addon.getSettings()).thenReturn(settings);
        Island island = mock(Island.class);
        when(island.getCenter()).thenReturn(new Location(world, 40, 80, 40));
        when(island.getUniqueId()).thenReturn("depth-regression");
        OneBlockIslands state = new OneBlockIslands("depth-regression");
        OneChunkManager manager = new OneChunkManager(addon);
        world.getBlockAt(31, 70, 40).setType(Material.DIAMOND_BLOCK);

        // Keep this test entirely in memory; generation must never persist a fixture island.
        try (var persistence = mockStatic(IslandsManager.class)) {
            manager.generate(island, state);
        }

        for (int x = 32; x < 48; x++) {
            for (int z = 32; z < 48; z++) {
                assertEquals(Material.BEDROCK, world.getBlockAt(x, expectedFloor, z).getType());
                assertTrue(OneChunkGenerator.isMinable(world.getBlockAt(x, expectedFloor + 1, z).getType()),
                        "Every column must contain terrain immediately above its floor");
            }
        }
        assertEquals(Material.DIAMOND_BLOCK, world.getBlockAt(31, 70, 40).getType());
        assertEquals(state.getSolidInChunk(), manager.getRemainingSolid(island));
        assertTrue(state.getSolidInChunk() >= 256 * (80 - expectedFloor));

        world.getBlockAt(32, expectedFloor + 1, 32).setType(Material.AIR);
        assertEquals(state.getSolidInChunk() - 1, manager.getRemainingSolid(island),
                "Mining at the bottom must count towards progression, including below Y=0");
        if (expectedFloor > world.getMinHeight()) {
            assertEquals(Material.AIR, world.getBlockAt(40, expectedFloor - 1, 40).getType(),
                    "An explicit shallow depth must remain supported");
        }
    }
}
