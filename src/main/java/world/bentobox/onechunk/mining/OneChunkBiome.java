package world.bentobox.onechunk.mining;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * Biomas por los que pasa un mundo OneChunk. Cada bioma define el terreno del chunk que se genera y los mobs que
 * aparecen; el chunk 0 siempre es {@link #FOREST} para que el jugador empiece con madera, piedra, tierra e hierro.
 */
public enum OneChunkBiome {

    FOREST("Bosque", Material.GRASS_BLOCK, Material.DIRT, Material.STONE, Material.STONE, Material.OAK_LOG,
            Material.OAK_LEAVES, 6, 4, 6, Extra.NONE,
            new EntityType[] { EntityType.ZOMBIE, EntityType.SPIDER, EntityType.SKELETON, EntityType.CREEPER }),
    DESERT("Desierto", Material.SAND, Material.SAND, Material.SANDSTONE, Material.STONE, Material.OAK_LOG,
            Material.OAK_LEAVES, 0, 0, 0, Extra.CACTUS,
            new EntityType[] { EntityType.HUSK, EntityType.SKELETON, EntityType.CREEPER, EntityType.ZOMBIE }),
    SNOW("Tundra nevada", Material.SNOW_BLOCK, Material.DIRT, Material.STONE, Material.STONE, Material.SPRUCE_LOG,
            Material.SPRUCE_LEAVES, 5, 5, 7, Extra.NONE,
            new EntityType[] { EntityType.STRAY, EntityType.ZOMBIE, EntityType.CREEPER, EntityType.SPIDER }),
    JUNGLE("Jungla", Material.GRASS_BLOCK, Material.DIRT, Material.STONE, Material.STONE, Material.JUNGLE_LOG,
            Material.JUNGLE_LEAVES, 6, 6, 9, Extra.MELON,
            new EntityType[] { EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.CREEPER, EntityType.WITCH }),
    CAVE("Cavernas profundas", Material.STONE, Material.STONE, Material.STONE, Material.DEEPSLATE, Material.OAK_LOG,
            Material.OAK_LEAVES, 0, 0, 0, Extra.NONE,
            new EntityType[] { EntityType.SKELETON, EntityType.CAVE_SPIDER, EntityType.ZOMBIE, EntityType.CREEPER,
                    EntityType.ENDERMAN });

    /** Decoración extra de superficie. */
    public enum Extra { NONE, CACTUS, MELON }

    /** Mobs pacíficos del primer chunk (comida para empezar). */
    public static final EntityType[] STARTER_ANIMALS = { EntityType.SHEEP, EntityType.COW, EntityType.CHICKEN,
            EntityType.PIG };

    private final String displayName;
    private final Material surface;
    private final Material filler;
    private final Material stone;
    private final Material deep;
    private final Material log;
    private final Material leaves;
    private final int trees;
    private final int treeMin;
    private final int treeMax;
    private final Extra extra;
    private final EntityType[] hostiles;

    OneChunkBiome(String displayName, Material surface, Material filler, Material stone, Material deep, Material log,
            Material leaves, int trees, int treeMin, int treeMax, Extra extra, EntityType[] hostiles) {
        this.displayName = displayName;
        this.surface = surface;
        this.filler = filler;
        this.stone = stone;
        this.deep = deep;
        this.log = log;
        this.leaves = leaves;
        this.trees = trees;
        this.treeMin = treeMin;
        this.treeMax = treeMax;
        this.extra = extra;
        this.hostiles = hostiles;
    }

    /**
     * Bioma del chunk número {@code chunksCleared} (0 = el primero): cambia cada {@code chunksPerBiome} chunks.
     */
    public static OneChunkBiome forChunk(int chunksCleared, int chunksPerBiome) {
        OneChunkBiome[] all = values();
        int step = Math.max(1, chunksPerBiome);
        return all[(Math.max(0, chunksCleared) / step) % all.length];
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

    public Material log() {
        return log;
    }

    public Material leaves() {
        return leaves;
    }

    public int trees() {
        return trees;
    }

    public int treeMin() {
        return treeMin;
    }

    public int treeMax() {
        return treeMax;
    }

    public Extra extra() {
        return extra;
    }

    public EntityType[] hostiles() {
        return hostiles.clone();
    }
}
