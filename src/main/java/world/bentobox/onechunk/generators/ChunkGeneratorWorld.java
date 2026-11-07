package world.bentobox.onechunk.generators;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import world.bentobox.onechunk.OneChunk;

/**
 * @author tastybento
 *         Creates the void world for OneChunk dimensions and populates the safe origin column at chunk (0,0).
 */
public class ChunkGeneratorWorld extends ChunkGenerator {

    /** Safe surface Y for Nether/End platforms and columns (strictly below Nether roof 128). */
    public static final int PLATFORM_Y = 64;

    /**
     * @param addon - addon
     */
    public ChunkGeneratorWorld(OneChunk addon) {
        super();
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        populateOriginColumn(worldInfo.getEnvironment(), worldInfo.getMinHeight(), chunkX, chunkZ, data);
    }

    @SuppressWarnings("deprecation")
    public ChunkData generateChunks(World world) {
        return createChunkData(world);
    }

    /**
     * @deprecated Paper 1.19+ uses {@link #generateSurface(WorldInfo, Random, int, int, ChunkData)};
     *     kept as fallback for legacy/test harnesses.
     */
    @Override
    @Deprecated(since = "1.19", forRemoval = true)
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomeGrid) {
        ChunkData data = generateChunks(world);
        populateOriginColumn(world.getEnvironment(), world.getMinHeight(), chunkX, chunkZ, data);
        return data;
    }

    private static void populateOriginColumn(World.Environment env, int minHeight, int chunkX, int chunkZ,
            ChunkData data) {
        if (env == World.Environment.NORMAL || chunkX != 0 || chunkZ != 0) {
            return;
        }
        int floor = Math.max(0, Math.min(minHeight, PLATFORM_Y - 4));
        Material body = env == World.Environment.NETHER ? Material.NETHERRACK : Material.END_STONE;
        Material deep = env == World.Environment.NETHER ? Material.BLACKSTONE : Material.END_STONE_BRICKS;
        data.setRegion(0, floor, 0, 16, floor + 1, 16, Material.BEDROCK);
        int deepTop = Math.min(PLATFORM_Y, floor + 16);
        if (deepTop > floor + 1) {
            data.setRegion(0, floor + 1, 0, 16, deepTop, 16, deep);
        }
        if (PLATFORM_Y + 1 > deepTop) {
            data.setRegion(0, deepTop, 0, 16, PLATFORM_Y + 1, 16, body);
        }
        seedOriginOres(env, floor, data);
    }

    private static void seedOriginOres(World.Environment env, int floor, ChunkData data) {
        Random rnd = new Random(env == World.Environment.NETHER ? 0x4E4554484552L : 0x5448455F454E44L);
        int yMin = floor + 2;
        int yMax = PLATFORM_Y - 2;
        if (yMax <= yMin) {
            return;
        }
        Material primary = env == World.Environment.NETHER ? Material.NETHER_QUARTZ_ORE : Material.OBSIDIAN;
        Material secondary = env == World.Environment.NETHER ? Material.NETHER_GOLD_ORE : Material.PURPUR_BLOCK;
        for (int i = 0; i < 16; i++) {
            data.setBlock(rnd.nextInt(16), yMin + rnd.nextInt(yMax - yMin + 1), rnd.nextInt(16), primary);
        }
        for (int i = 0; i < 10; i++) {
            data.setBlock(rnd.nextInt(16), yMin + rnd.nextInt(yMax - yMin + 1), rnd.nextInt(16), secondary);
        }
    }

    /**
     * Ensures chunk (0,0) in a Nether or End world is loaded and contains a solid origin column,
     * healing worlds whose (0,0) region was previously saved empty when Multiverse had no generator.
     */
    public static void ensureOriginPlatform(World world) {
        if (world == null || world.getEnvironment() == World.Environment.NORMAL) {
            return;
        }
        var originChunk = world.getChunkAt(0, 0);
        if (originChunk == null) {
            return;
        }
        originChunk.load(true);
        var probe = world.getBlockAt(8, PLATFORM_Y, 8);
        if (probe == null || probe.getType().isOccluding()) {
            return;
        }
        int floor = Math.max(0, Math.min(world.getMinHeight(), PLATFORM_Y - 4));
        Material body = world.getEnvironment() == World.Environment.NETHER ? Material.NETHERRACK : Material.END_STONE;
        Material deep = world.getEnvironment() == World.Environment.NETHER ? Material.BLACKSTONE : Material.END_STONE_BRICKS;
        int deepTop = Math.min(PLATFORM_Y, floor + 16);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                world.getBlockAt(x, floor, z).setType(Material.BEDROCK, false);
                for (int y = floor + 1; y < deepTop; y++) {
                    world.getBlockAt(x, y, z).setType(deep, false);
                }
                for (int y = deepTop; y <= PLATFORM_Y; y++) {
                    world.getBlockAt(x, y, z).setType(body, false);
                }
            }
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

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