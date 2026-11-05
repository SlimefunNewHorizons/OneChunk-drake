package world.bentobox.onechunk.listeners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.Settings;

class DimensionEntryListenerTest {
    private final World overworld = mock(World.class);
    private final World nether = mock(World.class);
    private final World end = mock(World.class);
    private final Player player = mock(Player.class);
    private final Settings settings = new Settings();
    private DimensionEntryListener listener;

    @BeforeEach
    void setUp() {
        OneChunk addon = mock(OneChunk.class);
        when(addon.getSettings()).thenReturn(settings);
        when(addon.getNetherWorld()).thenReturn(nether);
        when(addon.getEndWorld()).thenReturn(end);
        settings.setSafeDimensionEntry(true);
        when(player.teleportAsync(any(Location.class), any(TeleportCause.class)))
                .thenReturn(CompletableFuture.completedFuture(true));
        listener = new DimensionEntryListener(addon);
        for (World world : new World[] {nether, end}) {
            when(world.isChunkLoaded(0, 0)).thenReturn(true);
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            WorldBorder border = mock(WorldBorder.class);
            when(world.getWorldBorder()).thenReturn(border);
            when(border.isInside(any())).thenAnswer(call -> {
                Location at = call.getArgument(0);
                return at.getX() >= 0 && at.getX() < 16 && at.getZ() >= 0 && at.getZ() < 16;
            });
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
                Block block = mock(Block.class);
                when(block.getType()).thenReturn((int) call.getArgument(1) == 64 ? Material.NETHERRACK : Material.AIR);
                return block;
            });
        }
    }

    private PlayerTeleportEvent teleport(World world, double x, double y, double z) {
        return new PlayerTeleportEvent(player, new Location(overworld, -4096, 200, 8192),
                new Location(world, x, y, z, 37, 12), TeleportCause.COMMAND);
    }

    @ParameterizedTest
    @EnumSource(value = World.Environment.class, names = {"NETHER", "THE_END"})
    void distantIslandArrivesInsideTheSharedChunk(World.Environment environment) {
        World destination = environment == World.Environment.NETHER ? nether : end;
        PlayerTeleportEvent event = teleport(destination, -4096, 200, 8192);
        listener.onTeleport(event);
        assertFalse(event.isCancelled());
        assertEquals(destination, event.getTo().getWorld());
        assertEquals(1.5, event.getTo().getX());
        assertEquals(65, event.getTo().getY());
        assertEquals(1.5, event.getTo().getZ());
        assertEquals(37, event.getTo().getYaw());
        assertEquals(12, event.getTo().getPitch());
        verify(destination, never()).getChunkAt(anyInt(), anyInt());
        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    void portalSearchCannotOverrideSafeArrival() {
        PlayerPortalEvent event = new PlayerPortalEvent(player, new Location(overworld, 0, 80, 8192),
                new Location(nether, 100, 70, 100), TeleportCause.NETHER_PORTAL, 128, true, 16);
        listener.onPortal(event);
        assertTrue(event.isCancelled());
        assertEquals(65, event.getTo().getY());
        assertEquals(0, event.getSearchRadius());
        assertFalse(event.getCanCreatePortal());
        assertEquals(0, event.getCreationRadius());
        verify(player).teleportAsync(event.getTo(), TeleportCause.PLUGIN);
    }

    @Test
    void alreadySafeDestinationIsPreserved() {
        PlayerTeleportEvent event = teleport(nether, 8.5, 65, 8.5);
        Location original = event.getTo();
        listener.onTeleport(event);
        assertSame(original, event.getTo());
        assertFalse(event.isCancelled());
    }

    @Test
    void emptyPlatformCancelsInsteadOfSendingPlayerIntoVoid() {
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(nether.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        PlayerTeleportEvent event = teleport(nether, 0, 310, 800);
        listener.onTeleport(event);
        assertTrue(event.isCancelled());
        verify(player).sendMessage(contains("no safe arrival"));
    }

    @Test
    void unloadedPlatformCancelsWithoutLoadingChunks() {
        when(nether.isChunkLoaded(0, 0)).thenReturn(false);
        PlayerTeleportEvent event = teleport(nether, 0, 310, 800);
        listener.onTeleport(event);
        assertTrue(event.isCancelled());
        verify(nether, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(nether, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void unsafeFloorAndObstructedHeadroomAreRejected() {
        Block magma = mock(Block.class);
        when(magma.getType()).thenReturn(Material.MAGMA_BLOCK);
        when(nether.getBlockAt(1, 64, 1)).thenReturn(magma);
        Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        when(nether.getBlockAt(1, 66, 2)).thenReturn(stone);
        PlayerTeleportEvent event = teleport(nether, 0, 310, 800);
        listener.onTeleport(event);
        assertFalse(event.isCancelled());
        assertEquals(3.5, event.getTo().getZ());
    }

    @Test
    void returnToOverworldAndOtherModalitiesAreUntouched() {
        PlayerTeleportEvent event = teleport(overworld, 0, 310, 800);
        Location original = event.getTo();
        listener.onTeleport(event);
        assertSame(original, event.getTo());
        assertFalse(event.isCancelled());
        verifyNoInteractions(overworld);
    }

    @Test
    void configurationSwitchDefaultsToPreviousBehavior() {
        assertFalse(new Settings().isSafeDimensionEntry());
        settings.setSafeDimensionEntry(false);
        PlayerTeleportEvent event = teleport(nether, 0, 310, 800);
        Location original = event.getTo();
        listener.onTeleport(event);
        assertSame(original, event.getTo());
        verifyNoInteractions(nether);
    }

    @Test
    void customGeneratorIsUntouched() {
        settings.setUseOwnGenerator(true);
        PlayerTeleportEvent event = teleport(nether, 0, 310, 800);
        Location original = event.getTo();
        listener.onTeleport(event);
        assertSame(original, event.getTo());
        verifyNoInteractions(nether);
    }

    @Test
    void nullDestinationIsUntouched() {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, new Location(overworld, 0, 65, 0),
                null, TeleportCause.PLUGIN);
        listener.onTeleport(event);
        assertNull(event.getTo());
        assertFalse(event.isCancelled());
    }
}
