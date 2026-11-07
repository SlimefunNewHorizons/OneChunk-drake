package world.bentobox.onechunk.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;
import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.Settings;
import world.bentobox.onechunk.dataobjects.OneBlockIslands;
import world.bentobox.onechunk.generators.ChunkGeneratorWorld;
import world.bentobox.onechunk.listeners.BlockListener;
import world.bentobox.onechunk.listeners.DimensionEntryListener;

/**
 * Regression suite for Ticket #594:
 * Verifies Nether and End full-column generation, legacy 16x16 world border healing,
 * safe arrival from roof/distant coordinates (e.g. -3, 310, 800), return portal pad placement,
 * and Overworld return routing without Multiverse 8x coordinate scaling.
 */
class OneChunkDimensionGenerationTest {

    private ServerMock server;
    private WorldMock overworld;
    private WorldMock nether;
    private WorldMock end;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();

        overworld = spy(new WorldMock(Material.AIR, -64, 320, 64));
        overworld.setName("onechunk_world");
        overworld.setEnvironment(World.Environment.NORMAL);
        doReturn(mock(LivingEntity.class)).when(overworld).spawnEntity(any(Location.class), any(EntityType.class));
        server.addWorld(overworld);

        nether = spy(new WorldMock(Material.AIR, 0, 256, 32));
        nether.setName("onechunk_world_nether");
        nether.setEnvironment(World.Environment.NETHER);
        doReturn(mock(LivingEntity.class)).when(nether).spawnEntity(any(Location.class), any(EntityType.class));
        server.addWorld(nether);

        end = spy(new WorldMock(Material.AIR, 0, 256, 0));
        end.setName("onechunk_world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        doReturn(mock(LivingEntity.class)).when(end).spawnEntity(any(Location.class), any(EntityType.class));
        server.addWorld(end);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void netherAndEndIslandArrivalHealsMicroBorderGeneratesColumnAndAvoidsRoof() {
        // Simulate legacy 16x16 micro-border that killed Jack at (-3, 310, 800)
        nether.getWorldBorder().setCenter(8, 8);
        nether.getWorldBorder().setSize(16.0);
        end.getWorldBorder().setCenter(8, 8);
        end.getWorldBorder().setSize(16.0);

        Settings settings = new Settings();
        settings.setOneChunkDepth(0);

        OneChunk addon = mock(OneChunk.class);
        when(addon.getPlugin()).thenReturn(mock(BentoBox.class));
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getOverWorld()).thenReturn(overworld);
        when(addon.getNetherWorld()).thenReturn(nether);
        when(addon.getEndWorld()).thenReturn(end);
        when(addon.getBlockListener()).thenReturn(mock(BlockListener.class));

        Island island = mock(Island.class);
        Location center = new Location(overworld, 8, 80, 808);
        when(island.getCenter()).thenReturn(center);
        when(island.getUniqueId()).thenReturn("jack-island-800");

        OneBlockIslands state = new OneBlockIslands("jack-island-800");
        when(addon.getOneBlocksIsland(island)).thenReturn(state);

        IslandsManager islands = mock(IslandsManager.class);
        when(addon.getIslands()).thenReturn(islands);
        when(islands.getIslandAt(any(Location.class))).thenReturn(Optional.of(island));

        OneChunkManager manager = new OneChunkManager(addon);
        when(addon.getOneChunkManager()).thenReturn(manager);

        DimensionEntryListener entryListener = new DimensionEntryListener(addon);
        Player player = spy(server.addPlayer("Jack"));
        doReturn(java.util.concurrent.CompletableFuture.completedFuture(true))
                .when(player).teleportAsync(any(Location.class), any(TeleportCause.class));
        when(islands.getIsland(overworld, player.getUniqueId())).thenReturn(island);

        try (var persistence = mockStatic(IslandsManager.class)) {
            // Exact incident reproduction: arriving at (-3, 310, 800) in onechunk_world_nether
            PlayerTeleportEvent netherEvent = new PlayerTeleportEvent(
                    player,
                    new Location(overworld, 8, 81, 808),
                    new Location(nether, -3, 310, 800, 90f, 0f),
                    TeleportCause.NETHER_PORTAL);
            entryListener.onTeleport(netherEvent);

            assertFalse(netherEvent.isCancelled(), "Island arrival in Nether must succeed safely");
            assertTrue(nether.getWorldBorder().getSize() >= 1000.0, "Legacy 16x16 border must be reset");
            Location arrivedNether = netherEvent.getTo();
            assertNotNull(arrivedNether);
            assertEquals(nether, arrivedNether.getWorld());
            assertEquals(65.0, arrivedNether.getY(), "Must land on Y=65 platform, never roof Y=310");
            assertTrue(arrivedNether.getBlockX() >= 0 && arrivedNether.getBlockX() <= 15);
            assertTrue(arrivedNether.getBlockZ() >= 800 && arrivedNether.getBlockZ() <= 815);
            assertTrue(nether.getWorldBorder().isInside(arrivedNether));

            // Full 16x16 Nether column generated from Y=0 bedrock to Y=64 surface + return portal pad at X=-3
            assertEquals(Material.BEDROCK, nether.getBlockAt(8, 0, 808).getType());
            assertTrue(nether.getBlockAt(8, 64, 808).getType().isOccluding());
            assertEquals(Material.NETHER_PORTAL, nether.getBlockAt(-3, 66, 807).getType());
            assertTrue(state.getNetherSolidInChunk() >= 256 * 64);

            // End arrival at distant island (8, 808)
            PlayerTeleportEvent endEvent = new PlayerTeleportEvent(
                    player,
                    new Location(overworld, 8, 81, 808),
                    new Location(end, -3, 200, 800),
                    TeleportCause.END_PORTAL);
            entryListener.onTeleport(endEvent);

            assertFalse(endEvent.isCancelled(), "Island arrival in The End must succeed safely");
            assertTrue(end.getWorldBorder().getSize() >= 1000.0);
            Location arrivedEnd = endEvent.getTo();
            assertNotNull(arrivedEnd);
            assertEquals(end, arrivedEnd.getWorld());
            assertEquals(65.0, arrivedEnd.getY());
            assertEquals(Material.BEDROCK, end.getBlockAt(8, 0, 808).getType());
            assertEquals(Material.END_STONE, end.getBlockAt(8, 64, 808).getType());
            assertEquals(Material.END_PORTAL, end.getBlockAt(-3, 65, 808).getType());
            assertTrue(state.getEndSolidInChunk() >= 256 * 64);

            // Return portal from Nether (8, 65, 808) -> Overworld island (NOT 8x scaled to Z=6464!)
            overworld.getBlockAt(8, 80, 808).setType(Material.GRASS_BLOCK);
            when(island.getSpawnPoint(World.Environment.NORMAL)).thenReturn(new Location(overworld, 8.5, 81, 808.5));
            PlayerPortalEvent returnEvent = new PlayerPortalEvent(
                    player,
                    new Location(nether, -3, 66, 807),
                    new Location(overworld, -24, 81, 6456),
                    TeleportCause.NETHER_PORTAL, 128, true, 16);
            entryListener.onPortal(returnEvent);

            assertTrue(returnEvent.isCancelled(), "Portal search must be cancelled in favor of direct island teleport");
            assertEquals(overworld, returnEvent.getTo().getWorld());
            assertEquals(8.5, returnEvent.getTo().getX());
            assertEquals(81.0, returnEvent.getTo().getY());
            assertEquals(808.5, returnEvent.getTo().getZ());
        }
    }

    @Test
    void ensureOriginPlatformHealsEmptyOriginChunkInNetherAndEnd() {
        assertEquals(Material.AIR, nether.getBlockAt(8, ChunkGeneratorWorld.PLATFORM_Y, 8).getType());
        ChunkGeneratorWorld.ensureOriginPlatform(nether);
        assertEquals(Material.BEDROCK, nether.getBlockAt(8, 0, 8).getType());
        assertEquals(Material.NETHERRACK, nether.getBlockAt(8, ChunkGeneratorWorld.PLATFORM_Y, 8).getType());

        ChunkGeneratorWorld.ensureOriginPlatform(end);
        assertEquals(Material.BEDROCK, end.getBlockAt(8, 0, 8).getType());
        assertEquals(Material.END_STONE, end.getBlockAt(8, ChunkGeneratorWorld.PLATFORM_Y, 8).getType());
    }

    @Test
    void dimensionBiomeRotationCyclesCleanly() {
        assertEquals(OneChunkDimensionBiome.NETHER_WASTES,
                OneChunkDimensionBiome.forDimension(World.Environment.NETHER, 0, 3));
        assertEquals(OneChunkDimensionBiome.CRIMSON_FOREST,
                OneChunkDimensionBiome.forDimension(World.Environment.NETHER, 3, 3));
        assertEquals(OneChunkDimensionBiome.WARPED_FOREST,
                OneChunkDimensionBiome.forDimension(World.Environment.NETHER, 6, 3));
        assertEquals(OneChunkDimensionBiome.SOUL_SAND_VALLEY,
                OneChunkDimensionBiome.forDimension(World.Environment.NETHER, 9, 3));

        assertEquals(OneChunkDimensionBiome.END_BARRENS,
                OneChunkDimensionBiome.forDimension(World.Environment.THE_END, 0, 3));
        assertEquals(OneChunkDimensionBiome.CHORUS_HIGHLANDS,
                OneChunkDimensionBiome.forDimension(World.Environment.THE_END, 3, 3));
    }
}
