package world.bentobox.onechunk.generators;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import world.bentobox.onechunk.OneChunk;

/**
 * @author tastybento
 *         Creates the world
 */
public class ChunkGeneratorWorld extends ChunkGenerator {

    /**
     * @param addon - addon
     */
    public ChunkGeneratorWorld(OneChunk addon) {
        super();
    }

    @SuppressWarnings("deprecation")
    public ChunkData generateChunks(World world) {
        return createChunkData(world);
    }

    /**
     * @deprecated Paper 1.19+ uses the new ChunkGenerator API; override
     *     {@link #generateChunks(World)} instead.
     */
    @Override
    @Deprecated(since = "1.19", forRemoval = true)
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomeGrid) {
        ChunkData data = generateChunks(world);
        // Nether y End: solo existe UN chunk (0,0), una plataforma; el resto es vacio.
        if (world.getEnvironment() != World.Environment.NORMAL && chunkX == 0 && chunkZ == 0) {
            Material fill = world.getEnvironment() == World.Environment.NETHER ? Material.NETHERRACK
                    : Material.END_STONE;
            data.setRegion(0, PLATFORM_Y - 2, 0, 16, PLATFORM_Y + 1, 16, fill);
            data.setRegion(0, PLATFORM_Y - 3, 0, 16, PLATFORM_Y - 2, 16, Material.BEDROCK);
        }
        return data;
    }

    /** Y de la superficie de la plataforma del nether/end. */
    public static final int PLATFORM_Y = 64;

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, PLATFORM_Y + 1, 8.5);
    }

    // This needs to be set to return true to override minecraft's default
    // behavior
    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(final World world) {
        return Collections.emptyList();
    }

}