package world.bentobox.onechunk.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Garantías de OneChunk: el primer chunk trae árboles, hierro, carbón, piedra y tierra SIEMPRE; el chunk se mide por
 * escaneo; los biomas rotan cada X chunks y los mobs escalan.
 */
class OneChunkGeneratorTest {

    private static final int CX = 40; // centro de la isla (el chunk generado es el 32..47)
    private static final int CZ = 40;
    private static final int SURFACE = 100;
    private static final int DEPTH = 12;

    private ServerMock server;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("oc");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private int count(Material... any) {
        int n = 0;
        int ox = (CX >> 4) << 4;
        int oz = (CZ >> 4) << 4;
        for (int x = ox; x < ox + 16; x++) {
            for (int z = oz; z < oz + 16; z++) {
                for (int y = OneChunkGenerator.floorY(SURFACE, DEPTH); y <= OneChunkGenerator.topY(SURFACE); y++) {
                    Material m = world.getBlockAt(x, y, z).getType();
                    for (Material a : any) {
                        if (m == a) {
                            n++;
                        }
                    }
                }
            }
        }
        return n;
    }

    @Test
    void firstChunkHasGuaranteedResources() {
        OneChunkGenerator.Result r = OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, OneChunkBiome.FOREST, 0, 1L);
        assertEquals(6, r.trees(), "el primer chunk debe traer varios árboles (6)");
        assertTrue(count(Material.OAK_LOG) >= 6 * 4, "cada árbol tiene al menos 4 troncos");
        assertTrue(count(Material.OAK_LEAVES) > 0, "los árboles tienen hojas");
        assertEquals(10, r.ironOres(), "hierro garantizado: exactamente 10 en el chunk 0");
        assertEquals(14, r.coalOres(), "carbón garantizado: exactamente 14 en el chunk 0");
        assertTrue(count(Material.IRON_ORE) >= 10);
        assertTrue(count(Material.STONE) > 100, "hay piedra");
        assertTrue(count(Material.DIRT) > 100, "hay tierra");
        assertTrue(count(Material.GRASS_BLOCK) == 256, "toda la superficie es hierba");
        assertEquals(256, count(Material.BEDROCK), "suelo de bedrock completo: no se puede caer al vacío");
    }

    @Test
    void spawnColumnIsAlwaysFree() {
        for (OneChunkBiome b : OneChunkBiome.values()) {
            OneChunkGenerator.Result r = OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, b, 3, 7L);
            for (int dy = 1; dy <= 3; dy++) {
                assertTrue(world.getBlockAt(CX, SURFACE + dy, CZ).getType().isAir(), b + ": hueco sobre el centro");
            }
            assertEquals(SURFACE + 1, r.spawn().getBlockY());
        }
    }

    @Test
    void sameSeedGivesSameChunk() {
        OneChunkGenerator.Result a = OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, OneChunkBiome.JUNGLE, 4, 99L);
        int logsA = count(Material.JUNGLE_LOG);
        OneChunkGenerator.Result b = OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, OneChunkBiome.JUNGLE, 4, 99L);
        assertEquals(a.solidBlocks(), b.solidBlocks());
        assertEquals(logsA, count(Material.JUNGLE_LOG));
    }

    @Test
    void regeneratingReplacesTheOldChunkCompletely() {
        OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, OneChunkBiome.FOREST, 0, 1L);
        OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, OneChunkBiome.DESERT, 3, 2L);
        assertEquals(0, count(Material.OAK_LOG, Material.GRASS_BLOCK), "no queda nada del bioma anterior");
        assertEquals(Material.SAND, world.getBlockAt(CX + 1, SURFACE, CZ + 1).getType(), "la superficie del desierto es arena");
        assertEquals(1024, count(Material.SAND), "superficie y 3 capas de relleno son arena (4 x 256)");
        assertTrue(count(Material.CACTUS) >= 5, "el desierto trae cactus");
    }

    @Test
    void biomesHaveTheirSignatureContent() {
        OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, OneChunkBiome.SNOW, 6, 3L);
        assertEquals(256, count(Material.SNOW_BLOCK));
        assertTrue(count(Material.SPRUCE_LOG) > 0);
        OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, OneChunkBiome.JUNGLE, 9, 4L);
        assertTrue(count(Material.JUNGLE_LOG) > 0);
        assertTrue(count(Material.MELON) >= 3);
        OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, OneChunkBiome.CAVE, 12, 5L);
        assertTrue(count(Material.DEEPSLATE) > 0, "las cavernas tienen deepslate");
        assertTrue(count(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE) > 0, "diamantes en chunks avanzados");
        assertEquals(0, count(Material.OAK_LOG), "las cavernas no tienen árboles");
    }

    @Test
    void richerOresAppearWithProgress() {
        OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, OneChunkBiome.FOREST, 0, 1L);
        assertEquals(0, count(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE), "sin oro en el chunk 0");
        assertEquals(0, count(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE));
        OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, OneChunkBiome.FOREST, 8, 1L);
        assertTrue(count(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE) > 0);
        assertTrue(count(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE) > 0);
    }

    @Test
    void clearingIsMeasuredByScanningNotCounting() {
        OneChunkGenerator.Result r = OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, OneChunkBiome.FOREST, 0, 1L);
        int floor = OneChunkGenerator.floorY(SURFACE, DEPTH);
        int top = OneChunkGenerator.topY(SURFACE);
        int ox = (CX >> 4) << 4;
        int oz = (CZ >> 4) << 4;
        assertFalse(OneChunkManager.isCleared(r.solidBlocks(),
                OneChunkGenerator.countSolid(world, ox, oz, floor, top), 0.9), "recién generado no está agotado");
        // Minar el 95 %, de arriba abajo
        int toMine = (int) (r.solidBlocks() * 0.95);
        outer:
        for (int y = top; y > floor; y--) {
            for (int x = ox; x < ox + 16; x++) {
                for (int z = oz; z < oz + 16; z++) {
                    if (OneChunkGenerator.isMinable(world.getBlockAt(x, y, z).getType())) {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                        if (--toMine == 0) {
                            break outer;
                        }
                    }
                }
            }
        }
        assertTrue(OneChunkManager.isCleared(r.solidBlocks(),
                OneChunkGenerator.countSolid(world, ox, oz, floor, top), 0.9), "al minar el 95 % se da por agotado");
        // Trampa: colocar bloques extra NO adelanta el chunk, lo retrasa
        for (int i = 0; i < 40; i++) {
            world.getBlockAt(ox + (i % 16), SURFACE + 1, oz + (i / 16)).setType(Material.COBBLESTONE);
        }
        assertTrue(OneChunkGenerator.countSolid(world, ox, oz, floor, top) >= 40);
        assertEquals(256, count(Material.BEDROCK), "el bedrock nunca cuenta ni se mina");
    }

    @Test
    void biomeRotatesEveryNChunks() {
        assertEquals(OneChunkBiome.FOREST, OneChunkBiome.forChunk(0, 3), "el primer chunk siempre es bosque");
        assertEquals(OneChunkBiome.FOREST, OneChunkBiome.forChunk(2, 3));
        assertEquals(OneChunkBiome.DESERT, OneChunkBiome.forChunk(3, 3));
        assertEquals(OneChunkBiome.SNOW, OneChunkBiome.forChunk(6, 3));
        assertEquals(OneChunkBiome.JUNGLE, OneChunkBiome.forChunk(9, 3));
        assertEquals(OneChunkBiome.CAVE, OneChunkBiome.forChunk(12, 3));
        assertEquals(OneChunkBiome.FOREST, OneChunkBiome.forChunk(15, 3), "da la vuelta");
        assertEquals(OneChunkBiome.DESERT, OneChunkBiome.forChunk(1, 1), "con 1 chunk por bioma cambia en cada uno");
    }

    @Test
    void mobsScaleWithProgress() {
        assertEquals(0, OneChunkManager.hostileCount(0, 12), "el primer chunk no tiene hostiles");
        assertEquals(3, OneChunkManager.hostileCount(1, 12));
        assertEquals(12, OneChunkManager.hostileCount(40, 12), "tope configurable");
        assertEquals(0, OneChunkManager.hostileCount(5, 0), "max-mobs 0 los desactiva");
        assertEquals(1, OneChunkManager.unlockedTypes(0, 4));
        assertEquals(2, OneChunkManager.unlockedTypes(3, 4));
        assertEquals(4, OneChunkManager.unlockedTypes(30, 4), "el tipo más fuerte llega tarde pero llega");
    }
}
