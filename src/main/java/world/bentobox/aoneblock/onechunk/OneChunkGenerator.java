package world.bentobox.aoneblock.onechunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;

/**
 * Genera el contenido de un chunk de OneChunk: capas de terreno, menas, árboles y decoración con recursos
 * GARANTIZADOS (no al azar: las cantidades son fijas, solo cambian las posiciones, que salen de una semilla).
 * <p>
 * El volumen ocupado es la huella 16x16 del chunk, de {@code surfaceY - depth} (suelo de bedrock) a
 * {@code surfaceY + TREE_ROOM} (hueco para copas de árboles). La columna alrededor del centro de la isla queda
 * siempre libre de árboles para que el jugador aparezca en un sitio seguro.
 */
public final class OneChunkGenerator {

    /** Altura reservada sobre la superficie para árboles y cactus. */
    public static final int TREE_ROOM = 14;

    /** Resultado de generar un chunk. */
    public record Result(int solidBlocks, Location spawn, int trees, int ironOres, int coalOres) {}

    private OneChunkGenerator() {
    }

    /** Mayor coordenada Y que ocupa el volumen de un chunk generado en {@code surfaceY}. */
    public static int topY(int surfaceY) {
        return surfaceY + TREE_ROOM;
    }

    /** Altura del suelo de bedrock. */
    public static int floorY(int surfaceY, int depth) {
        return surfaceY - depth;
    }

    /**
     * Genera un chunk completo.
     *
     * @param world    mundo
     * @param centerX  X del centro de la isla (el chunk generado es el que lo contiene)
     * @param centerZ  Z del centro de la isla
     * @param surfaceY altura de la superficie (la del centro de la isla)
     * @param depth    bloques de profundidad
     * @param biome    bioma
     * @param index    número de chunk (0 = el primero); sube la rareza de las menas
     * @param seed     semilla (misma semilla = mismo chunk)
     */
    public static Result generate(World world, int centerX, int centerZ, int surfaceY, int depth, OneChunkBiome biome,
            int index, long seed) {
        int ox = (centerX >> 4) << 4;
        int oz = (centerZ >> 4) << 4;
        int floor = floorY(surfaceY, depth);
        Random rnd = new Random(seed);

        clear(world, ox, oz, floor, topY(surfaceY));
        layers(world, ox, oz, floor, surfaceY, biome);
        int[] counts = ores(world, ox, oz, floor, surfaceY, biome, index, rnd);
        int trees = decorate(world, ox, oz, surfaceY, biome, centerX, centerZ, rnd);
        int solid = countSolid(world, ox, oz, floor, topY(surfaceY));
        Location spawn = new Location(world, centerX + 0.5, surfaceY + 1, centerZ + 0.5);
        return new Result(solid, spawn, trees, counts[0], counts[1]);
    }

    /** Bloques minables que quedan en el volumen del chunk (todo lo que no es aire, bedrock ni líquido). */
    public static int countSolid(World world, int ox, int oz, int floor, int top) {
        int solid = 0;
        for (int x = ox; x < ox + 16; x++) {
            for (int z = oz; z < oz + 16; z++) {
                for (int y = floor + 1; y <= top; y++) {
                    Material m = world.getBlockAt(x, y, z).getType();
                    if (isMinable(m)) {
                        solid++;
                    }
                }
            }
        }
        return solid;
    }

    /** ¿Cuenta este material como bloque del chunk que el jugador debe minar? */
    public static boolean isMinable(Material m) {
        return !m.isAir() && m != Material.BEDROCK && m != Material.WATER && m != Material.LAVA;
    }

    private static void clear(World world, int ox, int oz, int floor, int top) {
        for (int x = ox; x < ox + 16; x++) {
            for (int z = oz; z < oz + 16; z++) {
                for (int y = floor; y <= top; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private static void layers(World world, int ox, int oz, int floor, int surfaceY, OneChunkBiome biome) {
        int half = floor + (surfaceY - floor) / 2;
        for (int x = ox; x < ox + 16; x++) {
            for (int z = oz; z < oz + 16; z++) {
                world.getBlockAt(x, floor, z).setType(Material.BEDROCK, false);
                for (int y = floor + 1; y <= surfaceY; y++) {
                    Material m;
                    if (y == surfaceY) {
                        m = biome.surface();
                    } else if (y >= surfaceY - 3) {
                        m = biome.filler();
                    } else if (y <= half && biome.deep() != biome.stone()) {
                        m = biome.deep();
                    } else {
                        m = biome.stone();
                    }
                    world.getBlockAt(x, y, z).setType(m, false);
                }
            }
        }
    }

    /** Cantidad garantizada de cada mena para el chunk {@code index}; devuelve {hierro, carbón} colocados. */
    private static int[] ores(World world, int ox, int oz, int floor, int surfaceY, OneChunkBiome biome, int index,
            Random rnd) {
        double boost = biome == OneChunkBiome.CAVE ? 1.5 : 1.0;
        int[] placed = new int[2];
        placed[1] = vein(world, ox, oz, floor, surfaceY, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
                (int) ((14 + index) * boost), rnd);
        placed[0] = vein(world, ox, oz, floor, surfaceY, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
                (int) ((10 + index) * boost), rnd);
        vein(world, ox, oz, floor, surfaceY, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, (int) (6 * boost), rnd);
        if (index >= 2) {
            vein(world, ox, oz, floor, surfaceY, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
                    (int) (6 * boost), rnd);
        }
        if (index >= 3) {
            vein(world, ox, oz, floor, surfaceY, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
                    (int) ((4 + index / 2) * boost), rnd);
            vein(world, ox, oz, floor, surfaceY, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, (int) (4 * boost), rnd);
        }
        if (index >= 6) {
            vein(world, ox, oz, floor, surfaceY, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
                    Math.min(2 + (index - 6) / 2, 6), rnd);
        }
        return placed;
    }

    /**
     * Coloca exactamente {@code count} bloques de mena en vetas pequeñas dentro de la piedra (la variante deepslate en
     * la mitad inferior). Devuelve cuántos colocó (siempre {@code count} salvo que no haya hueco).
     */
    private static int vein(World world, int ox, int oz, int floor, int surfaceY, Material ore, Material deepOre,
            int count, Random rnd) {
        int yMin = floor + 1;
        int yMax = surfaceY - 4; // nunca en la capa de tierra/superficie
        int placed = 0;
        int guard = 0;
        while (placed < count && guard++ < count * 40) {
            int x = ox + rnd.nextInt(16);
            int z = oz + rnd.nextInt(16);
            int y = yMin + rnd.nextInt(Math.max(1, yMax - yMin + 1));
            int size = 1 + rnd.nextInt(4);
            for (int i = 0; i < size && placed < count; i++) {
                Block b = world.getBlockAt(x, y, z);
                Material m = b.getType();
                if (m == Material.STONE || m == Material.DEEPSLATE || m == Material.SANDSTONE) {
                    b.setType(m == Material.DEEPSLATE ? deepOre : ore, false);
                    placed++;
                }
                x = Math.min(ox + 15, Math.max(ox, x + rnd.nextInt(3) - 1));
                z = Math.min(oz + 15, Math.max(oz, z + rnd.nextInt(3) - 1));
                y = Math.min(yMax, Math.max(yMin, y + rnd.nextInt(3) - 1));
            }
        }
        return placed;
    }

    /** Árboles, cactus y sandías según el bioma; devuelve cuántos árboles colocó. */
    private static int decorate(World world, int ox, int oz, int surfaceY, OneChunkBiome biome, int centerX,
            int centerZ, Random rnd) {
        List<int[]> spots = new ArrayList<>();
        for (int gx = 3; gx <= 12; gx += 3) {
            for (int gz = 3; gz <= 12; gz += 3) {
                int x = ox + gx;
                int z = oz + gz;
                if (Math.abs(x - centerX) > 3 || Math.abs(z - centerZ) > 3) {
                    spots.add(new int[] { x, z });
                }
            }
        }
        Collections.shuffle(spots, rnd);
        int used = 0;
        int trees = 0;
        for (int i = 0; i < biome.trees() && used < spots.size(); i++, used++) {
            int[] p = spots.get(used);
            int h = biome.treeMin() + rnd.nextInt(Math.max(1, biome.treeMax() - biome.treeMin() + 1));
            tree(world, p[0], surfaceY + 1, p[1], h, biome);
            trees++;
        }
        if (biome.extra() == OneChunkBiome.Extra.CACTUS) {
            for (int i = 0; i < 5 && used < spots.size(); i++, used++) {
                int[] p = spots.get(used);
                int h = 2 + rnd.nextInt(2);
                for (int y = 1; y <= h; y++) {
                    world.getBlockAt(p[0], surfaceY + y, p[1]).setType(Material.CACTUS, false);
                }
            }
        } else if (biome.extra() == OneChunkBiome.Extra.MELON) {
            for (int i = 0; i < 3 && used < spots.size(); i++, used++) {
                int[] p = spots.get(used);
                world.getBlockAt(p[0], surfaceY + 1, p[1]).setType(Material.MELON, false);
            }
        }
        return trees;
    }

    private static void tree(World world, int x, int baseY, int z, int height, OneChunkBiome biome) {
        int topLog = baseY + height - 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy) > 3 || (dx == 0 && dz == 0 && dy <= 0)) {
                        continue;
                    }
                    Block b = world.getBlockAt(x + dx, topLog + dy, z + dz);
                    if (b.getType().isAir()) {
                        Leaves data = (Leaves) biome.leaves().createBlockData();
                        data.setPersistent(true); // las hojas no decaen: no cuentan como bloques "que se rompen solos"
                        b.setBlockData(data, false);
                    }
                }
            }
        }
        for (int y = baseY; y <= topLog; y++) {
            world.getBlockAt(x, y, z).setType(biome.log(), false);
        }
    }
}
