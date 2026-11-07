package world.bentobox.onechunk.listeners;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.dataobjects.OneBlockIslands;
import world.bentobox.onechunk.generators.ChunkGeneratorWorld;
import world.bentobox.onechunk.mining.OneChunkGenerator;
import world.bentobox.onechunk.mining.OneChunkManager;

/**
 * Ensures safe, generated arrivals and return portals across OneChunk Overworld, Nether, and End dimensions.
 * <p>
 * Supports both per-island Nether/End OneChunk columns ({@code nether.islands: true}, {@code end.islands: true})
 * and shared origin chunk {@code (0,0)} fallback, preventing {@code WORLD_BORDER}, void, roof ({@code Y=310}),
 * and {@code Multiverse-NetherPortals} coordinate-scaling failures.
 */
public final class DimensionEntryListener implements Listener {

    private final OneChunk addon;

    public DimensionEntryListener(OneChunk addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!isEnabled()) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        World world = to.getWorld();
        if (world != addon.getNetherWorld() && world != addon.getEndWorld()) {
            return;
        }
        guardDimensionArrival(event, world);
    }

    // Portal events have their own HandlerList and must be registered separately.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!isEnabled()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        World fromWorld = from != null ? from.getWorld() : null;
        World toWorld = to != null ? to.getWorld() : null;

        // Return trip: Nether/End -> Overworld (overrides Multiverse-NetherPortals 8x scaling).
        if (fromWorld != null && (fromWorld == addon.getNetherWorld() || fromWorld == addon.getEndWorld())
                && addon.getOverWorld() != null) {
            Location home = resolveOverworldReturn(event.getPlayer(), from);
            if (home != null) {
                completePortalTeleport(event, home);
                return;
            }
        }

        // Forward trip: Overworld -> Nether/End (even if another plugin rewrote or nulled event.getTo()).
        World targetDim = null;
        if (toWorld == addon.getNetherWorld() || toWorld == addon.getEndWorld()) {
            targetDim = toWorld;
        } else if (fromWorld != null && fromWorld == addon.getOverWorld()) {
            if (event.getCause() == TeleportCause.NETHER_PORTAL && addon.getSettings().isNetherGenerate()) {
                targetDim = addon.getNetherWorld();
            } else if ((event.getCause() == TeleportCause.END_PORTAL || event.getCause() == TeleportCause.END_GATEWAY)
                    && addon.getSettings().isEndGenerate()) {
                targetDim = addon.getEndWorld();
            }
        }
        if (targetDim == null) {
            return;
        }
        if (to == null || to.getWorld() != targetDim) {
            float yaw = from != null ? from.getYaw() : 0f;
            float pitch = from != null ? from.getPitch() : 0f;
            double x = from != null ? from.getX() : 8.5;
            double z = from != null ? from.getZ() : 8.5;
            event.setTo(new Location(targetDim, x, ChunkGeneratorWorld.PLATFORM_Y + 1, z, yaw, pitch));
        }
        if (guardDimensionArrival(event, targetDim) && !event.isCancelled()) {
            completePortalTeleport(event, event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!isEnabled() || addon.getOverWorld() == null) {
            return;
        }
        World current = event.getPlayer().getWorld();
        if (current != addon.getNetherWorld() && current != addon.getEndWorld()) {
            return;
        }
        Location home = resolveOverworldReturn(event.getPlayer(), event.getPlayer().getLocation());
        if (home != null) {
            event.setRespawnLocation(home);
        }
    }

    private void completePortalTeleport(PlayerPortalEvent event, Location destination) {
        event.setTo(destination);
        event.setSearchRadius(0);
        event.setCanCreatePortal(false);
        event.setCreationRadius(0);
        event.setCancelled(true);
        Player player = event.getPlayer();
        player.setFallDistance(0f);
        player.teleportAsync(destination, TeleportCause.PLUGIN)
                .exceptionally(failure -> {
                    addon.logWarning("OneChunk dimension arrival could not complete.");
                    return false;
                });
    }

    private boolean isEnabled() {
        return addon.getSettings() != null
                && addon.getSettings().isSafeDimensionEntry()
                && !addon.getSettings().isUseOwnGenerator();
    }

    private boolean isIslandDimension(World world) {
        if (world == addon.getNetherWorld()) {
            return addon.getSettings().isNetherIslands();
        }
        if (world == addon.getEndWorld()) {
            return addon.getSettings().isEndIslands();
        }
        return false;
    }

    private boolean guardDimensionArrival(PlayerTeleportEvent event, World world) {
        Location to = event.getTo();
        float yaw = to != null ? to.getYaw() : 0f;
        float pitch = to != null ? to.getPitch() : 0f;

        // Heal any legacy 16-block border before checking arrival containment.
        if (addon.getPlugin() != null && world.getWorldBorder() != null
                && world.getWorldBorder().getSize() > 0 && world.getWorldBorder().getSize() < 1000.0) {
            world.getWorldBorder().reset();
        }

        // 1. Per-island Nether/End OneChunk column arrival
        if (isIslandDimension(world)) {
            Optional<Island> islandOpt = resolveIsland(event.getPlayer(), event.getFrom(), to);
            if (islandOpt.isPresent()) {
                Island island = islandOpt.get();
                Location center = island.getCenter();
                if (center != null) {
                    OneChunkManager manager = addon.getOneChunkManager();
                    if (manager != null && addon.getBlockListener() != null && addon.getSettings().isOneChunkEnabled()) {
                        OneBlockIslands state = addon.getOneBlocksIsland(island);
                        manager.ensureDimensionGenerated(island, state, world);
                    }
                    int ox = (center.getBlockX() >> 4) << 4;
                    int oz = (center.getBlockZ() >> 4) << 4;
                    if (to != null && isSafeInIslandArea(to, ox, oz)) {
                        return true;
                    }
                    Location arrival = findSafeSpotInChunk(world, ox, oz, center.getBlockX(), center.getBlockZ(),
                            OneChunkManager.surfaceYForWorld(world, center), yaw, pitch);
                    if (arrival != null) {
                        event.setTo(arrival);
                        return true;
                    }
                }
            }
        }

        // 2. Shared origin chunk (0,0) fallback
        if (to != null && isSafeInChunk(to, 0, 0)) {
            return true;
        }
        if (!world.isChunkLoaded(0, 0) && addon.getPlugin() != null) {
            ChunkGeneratorWorld.ensureOriginPlatform(world);
        } else if (world.isChunkLoaded(0, 0) && addon.getPlugin() != null) {
            Block centerFloor = world.getBlockAt(8, ChunkGeneratorWorld.PLATFORM_Y, 8);
            if (centerFloor != null && !centerFloor.getType().isOccluding()) {
                ChunkGeneratorWorld.ensureOriginPlatform(world);
            }
        }
        if (world.isChunkLoaded(0, 0)) {
            for (int x = 1; x < 15; x++) {
                for (int z = 1; z < 15; z++) {
                    Location arrival = new Location(world, x + 0.5, ChunkGeneratorWorld.PLATFORM_Y + 1,
                            z + 0.5, yaw, pitch);
                    if (isSafeInChunk(arrival, 0, 0)) {
                        event.setTo(arrival);
                        return true;
                    }
                }
            }
            Location scanned = findSafeSpotInChunk(world, 0, 0, 8, 8, ChunkGeneratorWorld.PLATFORM_Y, yaw, pitch);
            if (scanned != null) {
                event.setTo(scanned);
                return true;
            }
        }
        event.setCancelled(true);
        if (event.getPlayer() != null) {
            event.getPlayer().sendMessage("OneChunk: the dimension has no safe arrival yet. Please try again later.");
        }
        return true;
    }

    private Optional<Island> resolveIsland(Player player, Location from, Location to) {
        if (addon.getIslands() == null) {
            return Optional.empty();
        }
        if (from != null && from.getWorld() != null && from.getWorld() == addon.getOverWorld()) {
            Optional<Island> fromIsland = addon.getIslands().getIslandAt(from);
            if (fromIsland.isPresent()) {
                return fromIsland;
            }
        }
        if (to != null && to.getWorld() != null) {
            Optional<Island> toIsland = addon.getIslands().getIslandAt(to);
            if (toIsland.isPresent()) {
                return toIsland;
            }
        }
        if (player != null && addon.getOverWorld() != null) {
            return Optional.ofNullable(addon.getIslands().getIsland(addon.getOverWorld(), player.getUniqueId()));
        }
        return Optional.empty();
    }

    private Location resolveOverworldReturn(Player player, Location from) {
        World overWorld = addon.getOverWorld();
        if (overWorld == null || addon.getIslands() == null) {
            return null;
        }
        Optional<Island> islandOpt = Optional.empty();
        if (from != null && from.getWorld() != null) {
            islandOpt = addon.getIslands().getIslandAt(from);
        }
        if (islandOpt.isEmpty() && player != null) {
            islandOpt = Optional.ofNullable(addon.getIslands().getIsland(overWorld, player.getUniqueId()));
        }
        if (islandOpt.isEmpty()) {
            return overWorld.getSpawnLocation();
        }
        Island island = islandOpt.get();
        Location spawn = island.getSpawnPoint(World.Environment.NORMAL);
        if (spawn != null && spawn.getWorld() == overWorld) {
            overWorld.getChunkAt(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4).load(true);
            if (isStandingSpotSafe(spawn)) {
                return spawn;
            }
        }
        Location center = island.getCenter();
        if (center == null) {
            return overWorld.getSpawnLocation();
        }
        int ox = (center.getBlockX() >> 4) << 4;
        int oz = (center.getBlockZ() >> 4) << 4;
        overWorld.getChunkAt(ox >> 4, oz >> 4).load(true);
        float yaw = from != null ? from.getYaw() : 0f;
        float pitch = from != null ? from.getPitch() : 0f;
        Location safe = findSafeSpotInChunk(overWorld, ox, oz, center.getBlockX(), center.getBlockZ(),
                center.getBlockY(), yaw, pitch);
        return safe != null ? safe : new Location(overWorld, center.getBlockX() + 0.5, center.getBlockY() + 1,
                center.getBlockZ() + 0.5, yaw, pitch);
    }

    private Location findSafeSpotInChunk(World world, int ox, int oz, int preferX, int preferZ, int nominalSurfaceY,
            float yaw, float pitch) {
        int top = Math.min(world.getMaxHeight() - 2, OneChunkGenerator.topY(nominalSurfaceY) + 2);
        int bottom = Math.max(world.getMinHeight() + 1, 1);
        // Check preferred center column first (top-down so mined columns land on current floor, not mid-air).
        for (int y = top; y >= bottom; y--) {
            Location candidate = new Location(world, preferX + 0.5, y, preferZ + 0.5, yaw, pitch);
            if (isStandingSpotSafe(candidate)) {
                return candidate;
            }
        }
        for (int x = ox + 1; x < ox + 15; x++) {
            for (int z = oz + 1; z < oz + 15; z++) {
                Location nominal = new Location(world, x + 0.5, nominalSurfaceY + 1, z + 0.5, yaw, pitch);
                if (isStandingSpotSafe(nominal)) {
                    return nominal;
                }
                for (int y = top; y >= bottom; y--) {
                    Location candidate = new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
                    if (isStandingSpotSafe(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafeInIslandArea(Location at, int ox, int oz) {
        int x = at.getBlockX();
        int z = at.getBlockZ();
        // Includes the 16x16 mining chunk [ox..ox+15] and the adjacent return pad [ox-4..ox-1].
        if (x < ox - 4 || x > ox + 15 || z < oz || z > oz + 15) {
            return false;
        }
        return isStandingSpotSafe(at);
    }

    private boolean isSafeInChunk(Location at, int ox, int oz) {
        int x = at.getBlockX();
        int z = at.getBlockZ();
        if (x < ox + 1 || x > ox + 14 || z < oz + 1 || z > oz + 14) {
            return false;
        }
        return isStandingSpotSafe(at);
    }

    private boolean isStandingSpotSafe(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return false;
        }
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1
                || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        // In the Nether, never allow arrivals on or above the bedrock roof (Y >= 120).
        if (world.getEnvironment() == World.Environment.NETHER && y >= 120) {
            return false;
        }
        WorldBorder border = world.getWorldBorder();
        if (border != null && !border.isInside(at)) {
            return false;
        }
        Block floorBlock = world.getBlockAt(x, y - 1, z);
        Block feetBlock = world.getBlockAt(x, y, z);
        Block headBlock = world.getBlockAt(x, y + 1, z);
        if (floorBlock == null || feetBlock == null || headBlock == null) {
            return false;
        }
        Material floor = floorBlock.getType();
        return floor.isOccluding() && floor != Material.MAGMA_BLOCK
                && feetBlock.getType().isAir()
                && headBlock.getType().isAir();
    }
}
