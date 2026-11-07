package world.bentobox.onechunk.mining;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

/**
 * Dimension-specific biomes for OneChunk Nether and End columns.
 * <p>
 * Kept separate from {@link OneChunkBiome} so Overworld biome rotation and tests remain untouched.
 */
public enum OneChunkDimensionBiome {

    // Nether progression biomes
    NETHER_WASTES(World.Environment.NETHER, "Nether Wastes",
            Material.NETHERRACK, Material.NETHERRACK, Material.NETHERRACK, Material.BLACKSTONE,
            Material.CRIMSON_STEM, Material.NETHER_WART_BLOCK, 0,
            new EntityType[] { EntityType.ZOMBIFIED_PIGLIN, EntityType.MAGMA_CUBE, EntityType.SKELETON, EntityType.BLAZE }),

    CRIMSON_FOREST(World.Environment.NETHER, "Crimson Forest",
            Material.CRIMSON_NYLIUM, Material.NETHERRACK, Material.NETHERRACK, Material.BLACKSTONE,
            Material.CRIMSON_STEM, Material.NETHER_WART_BLOCK, 5,
            new EntityType[] { EntityType.PIGLIN, EntityType.HOGLIN, EntityType.MAGMA_CUBE, EntityType.BLAZE }),

    WARPED_FOREST(World.Environment.NETHER, "Warped Forest",
            Material.WARPED_NYLIUM, Material.NETHERRACK, Material.NETHERRACK, Material.BLACKSTONE,
            Material.WARPED_STEM, Material.WARPED_WART_BLOCK, 5,
            new EntityType[] { EntityType.ENDERMAN, EntityType.ZOMBIFIED_PIGLIN, EntityType.SKELETON, EntityType.BLAZE }),

    SOUL_SAND_VALLEY(World.Environment.NETHER, "Soul Sand Valley",
            Material.SOUL_SAND, Material.SOUL_SOIL, Material.NETHERRACK, Material.BASALT,
            Material.CRIMSON_STEM, Material.NETHER_WART_BLOCK, 0,
            new EntityType[] { EntityType.SKELETON, EntityType.GHAST, EntityType.BLAZE, EntityType.WITHER_SKELETON }),

    // End progression biomes
    END_BARRENS(World.Environment.THE_END, "End Barrens",
            Material.END_STONE, Material.END_STONE, Material.END_STONE, Material.END_STONE,
            Material.OBSIDIAN, Material.PURPUR_BLOCK, 0,
            new EntityType[] { EntityType.ENDERMAN, EntityType.ENDERMITE }),

    CHORUS_HIGHLANDS(World.Environment.THE_END, "Chorus Highlands",
            Material.END_STONE, Material.END_STONE, Material.END_STONE, Material.END_STONE_BRICKS,
            Material.CHORUS_FLOWER, Material.PURPUR_BLOCK, 4,
            new EntityType[] { EntityType.ENDERMAN, EntityType.SHULKER });

    private static final OneChunkDimensionBiome[] NETHER_ROTATION = {
            NETHER_WASTES, CRIMSON_FOREST, WARPED_FOREST, SOUL_SAND_VALLEY
    };

    private static final OneChunkDimensionBiome[] END_ROTATION = {
            END_BARRENS, CHORUS_HIGHLANDS
    };

    private final World.Environment environment;
    private final String displayName;
    private final Material surface;
    private final Material filler;
    private final Material stone;
    private final Material deep;
    private final Material stem;
    private final Material canopy;
    private final int structures;
    private final EntityType[] hostiles;

    OneChunkDimensionBiome(World.Environment environment, String displayName,
            Material surface, Material filler, Material stone, Material deep,
            Material stem, Material canopy, int structures, EntityType[] hostiles) {
        this.environment = environment;
        this.displayName = displayName;
        this.surface = surface;
        this.filler = filler;
        this.stone = stone;
        this.deep = deep;
        this.stem = stem;
        this.canopy = canopy;
        this.structures = structures;
        this.hostiles = hostiles;
    }

    public static OneChunkDimensionBiome forDimension(World.Environment environment, int chunksCleared, int chunksPerBiome) {
        OneChunkDimensionBiome[] pool = environment == World.Environment.THE_END ? END_ROTATION : NETHER_ROTATION;
        int step = Math.max(1, chunksPerBiome);
        return pool[(Math.max(0, chunksCleared) / step) % pool.length];
    }

    public World.Environment environment() {
        return environment;
    }

    public String displayName() {
        return displayName;
    }

    public Material surface() {
        return surface;
    }

    public Material filler() {
        return filler;
    }

    public Material stone() {
        return stone;
    }

    public Material deep() {
        return deep;
    }

    public Material stem() {
        return stem;
    }

    public Material canopy() {
        return canopy;
    }

    public int structures() {
        return structures;
    }

    public EntityType[] hostiles() {
        return hostiles.clone();
    }
}
