package world.bentobox.onechunk.listeners;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.generators.ChunkGeneratorWorld;

/** The built-in Nether/End contain only chunk (0,0), not terrain at island coordinates. */
public final class DimensionEntryListener implements Listener {
    private final OneChunk addon;

    public DimensionEntryListener(OneChunk addon) {
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        guard(event);
    }

    // Portal events have their own HandlerList and must be registered separately.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (guard(event) && !event.isCancelled()) {
            // Nether's destination is a portal SEARCH origin, not a final teleport.
            // Use a regular teleport: this platform intentionally has no exit portal.
            event.setSearchRadius(0);
            event.setCanCreatePortal(false);
            event.setCreationRadius(0);
            event.setCancelled(true);
            event.getPlayer().teleportAsync(event.getTo(), PlayerTeleportEvent.TeleportCause.PLUGIN)
                    .exceptionally(failure -> {
                        addon.logWarning("OneChunk dimension arrival could not complete.");
                        return false;
                    });
        }
    }

    private boolean guard(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (!addon.getSettings().isSafeDimensionEntry() || addon.getSettings().isUseOwnGenerator()
                || to == null || to.getWorld() == null) {
            return false;
        }
        World world = to.getWorld();
        if (world != addon.getNetherWorld() && world != addon.getEndWorld()) {
            return false;
        }
        if (safe(to)) {
            return true;
        }
        // Bounded read of an already loaded chunk: no world writes, chunk loads or data resets.
        if (world.isChunkLoaded(0, 0)) {
            for (int x = 1; x < 15; x++) {
                for (int z = 1; z < 15; z++) {
                    Location arrival = new Location(world, x + 0.5, ChunkGeneratorWorld.PLATFORM_Y + 1,
                            z + 0.5, to.getYaw(), to.getPitch());
                    if (safe(arrival)) {
                        event.setTo(arrival);
                        return true;
                    }
                }
            }
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("OneChunk: the dimension has no safe arrival yet. Please try again later.");
        return true;
    }

    private boolean safe(Location at) {
        World world = at.getWorld();
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        // Interior margin leaves room for the player's bounding box and the border damage buffer.
        if (x < 1 || x > 14 || z < 1 || z > 14 || y <= world.getMinHeight()
                || y >= world.getMaxHeight() - 1 || !world.isChunkLoaded(0, 0)
                || !world.getWorldBorder().isInside(at)) {
            return false;
        }
        var floor = world.getBlockAt(x, y - 1, z).getType();
        // Match the generated platform or an ordinary replacement floor; reject partial/harmful blocks.
        return floor.isOccluding() && floor != org.bukkit.Material.MAGMA_BLOCK
                && world.getBlockAt(x, y, z).getType().isAir()
                && world.getBlockAt(x, y + 1, z).getType().isAir();
    }
}
