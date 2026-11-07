package world.bentobox.onechunk.mining;

import java.util.Random;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.dataobjects.OneBlockIslands;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.managers.IslandsManager;

/**
 * Lógica de juego de OneChunk: el jugador MINA un chunk con recursos garantizados; al agotarlo aparece otro, y cada
 * {@code chunks-per-biome} chunks cambia el bioma, con mobs cada vez más peligrosos.
 * <p>
 * El agotamiento se mide ESCANEANDO el volumen (no contando roturas): así colocar y romper bloques una y otra vez no
 * hace avanzar el chunk.
 */
public class OneChunkManager {

    /** Cada cuántas roturas se vuelve a escanear el volumen (el escaneo recorre ~16x16xprofundidad bloques). */
    public static final int SCAN_EVERY = 16;

    private final OneChunk addon;

    public OneChunkManager(OneChunk addon) {
        this.addon = addon;
    }

    /** Profundidad efectiva: 0 en config = hasta el fondo del mundo (columna completa); nunca por debajo de minHeight. */
    private int depth(World world, int surfaceY) {
        int d = addon.getSettings().getOneChunkDepth();
        int max = surfaceY - world.getMinHeight();
        return d <= 0 ? max : Math.min(d, max);
    }

    /** ¿Está activo el modo OneChunk? */
    public boolean enabled() {
        return addon.getSettings() != null && addon.getSettings().isOneChunkEnabled();
    }

    /** Genera el chunk actual de la isla (primero al crearla, siguientes al agotar el anterior). */
    public void generate(Island island, OneBlockIslands is) {
        Location c = island.getCenter();
        if (c == null || c.getWorld() == null) {
            return;
        }
        int per = addon.getSettings().getOneChunkChunksPerBiome();
        int depth = depth(c.getWorld(), c.getBlockY());
        int index = is.getChunksCleared();
        OneChunkBiome biome = OneChunkBiome.forChunk(index, per);
        long seed = island.getUniqueId().hashCode() * 31L + index;
        OneChunkGenerator.Result r = OneChunkGenerator.generate(c.getWorld(), c.getBlockX(), c.getBlockZ(), c.getBlockY(),
                depth, biome, index, seed);
        is.setSolidInChunk(r.solidBlocks());
        is.setBrokenInChunk(0);
        int floor = OneChunkGenerator.floorY(c.getBlockY(), depth);
        // Sincronizar spawn point oficial de BentoBox a la superficie segura generada
        if (r.spawn() != null && r.spawn().getWorld() != null) {
            island.setSpawnPoint(r.spawn().getWorld().getEnvironment(), r.spawn());
            IslandsManager.saveIsland(island);
        }
        // Primero poner a salvo lo que había dentro (el terreno nuevo ya está generado encima) y SOLO DESPUÉS sembrar los
        // mobs nuevos: al revés, la reubicación los eliminaría.
        relocateEntities(c.getWorld(), (c.getBlockX() >> 4) << 4, (c.getBlockZ() >> 4) << 4, floor,
                OneChunkGenerator.topY(c.getBlockY()), r.spawn(), index);
        spawnMobs(c.getWorld(), c, biome, index, new Random(seed ^ 0x5DEECE66DL));
    }

    /**
     * Pone a salvo todo lo que estaba dentro del volumen cuando se regeneró el chunk (el terreno nuevo se genera encima):
     * jugadores (miembros o visitantes) y mascotas -> a la superficie, objetos tirados -> a la superficie, y
     * cualquier otro ser vivo -> se elimina (quedaría sepultado y se asfixiaría).
     * <p>
     * Usa teletransporte SÍNCRONO en el mismo tick de la generación: con {@code teleportAsync} el jugador pasaba uno o
     * más ticks incrustado en bloques sólidos.
     *
     * @return número de jugadores trasladados
     */
    public static int relocateEntities(World world, int ox, int oz, int floor, int top, Location spawn) {
        return relocateEntities(world, ox, oz, floor, top, spawn, 1);
    }

    /**
     * Pone a salvo todo lo que estaba dentro del volumen discriminando según sea creación inicial (chunkIndex == 0)
     * o regeneración de fase (chunkIndex > 0).
     *
     * @return número de jugadores trasladados
     */
    public static int relocateEntities(World world, int ox, int oz, int floor, int top, Location spawn, int chunkIndex) {
        int moved = 0;
        // Caja del volumen (más 40 bloques por encima: jugadores volando o cayendo sobre el chunk)
        for (Entity e : world.getNearbyEntities(BoundingBox.of(new Vector(ox, floor, oz), new Vector(ox + 16, top + 40, oz + 16)))) {
            if (e instanceof Player p) {
                p.teleport(spawn);
                p.setFallDistance(0f);
                p.setVelocity(new Vector(0, 0, 0));
                if (chunkIndex == 0) {
                    p.sendMessage("§6OneChunk §7» §a¡Bienvenido a tu chunk! Te ubicamos en la superficie segura.");
                } else {
                    p.sendMessage("§6OneChunk §7» §eEl chunk se regeneró: te llevamos a la superficie.");
                }
                moved++;
            } else if (e instanceof Item || (e instanceof Tameable t && t.isTamed())) {
                e.teleport(spawn);
            } else if (e instanceof LivingEntity) {
                e.remove();
            }
        }
        return moved;
    }

    /**
     * Calcula la cantidad de bloques sólidos minables que quedan actualmente en el volumen del chunk de la isla.
     */
    public int getRemainingSolid(Island island) {
        Location c = island.getCenter();
        if (c == null || c.getWorld() == null) {
            return 0;
        }
        int depth = depth(c.getWorld(), c.getBlockY());
        int floor = OneChunkGenerator.floorY(c.getBlockY(), depth);
        int ox = (c.getBlockX() >> 4) << 4;
        int oz = (c.getBlockZ() >> 4) << 4;
        return OneChunkGenerator.countSolid(c.getWorld(), ox, oz, floor, OneChunkGenerator.topY(c.getBlockY()));
    }

    /**
     * Se llama tras romper un bloque dentro del chunk de la isla. No coloca nada en el hueco: el bloque roto se queda
     * roto (se mina A TRAVÉS del chunk).
     *
     * @return true si este rompimiento agotó el chunk y se generó el siguiente
     */
    public boolean onBlockBroken(Island island, OneBlockIslands is) {
        Location c = island.getCenter();
        if (c == null || c.getWorld() == null) {
            return false;
        }
        is.setBrokenInChunk(is.getBrokenInChunk() + 1);
        if (is.getBrokenInChunk() % SCAN_EVERY != 0) {
            return false;
        }
        int remaining = getRemainingSolid(island);
        if (is.getSolidInChunk() <= 0) {
            // Isla anterior a OneChunk (o sin datos): se ADOPTA el terreno que ya tiene como chunk actual en vez de darlo
            // por agotado, que lo regeneraría y borraría lo que el jugador construyó.
            is.setSolidInChunk(Math.max(1, remaining));
            return false;
        }
        if (!isCleared(is.getSolidInChunk(), remaining, addon.getSettings().getOneChunkClearFraction())) {
            return false;
        }
        advance(island, is);
        return true;
    }

    /** ¿Se minó al menos {@code fraction} de lo que generó el chunk? */
    public static boolean isCleared(int generated, int remaining, double fraction) {
        if (generated <= 0) {
            return true;
        }
        return remaining <= generated * (1.0 - fraction);
    }

    private void advance(Island island, OneBlockIslands is) {
        int per = addon.getSettings().getOneChunkChunksPerBiome();
        OneChunkBiome before = OneChunkBiome.forChunk(is.getChunksCleared(), per);
        is.setChunksCleared(is.getChunksCleared() + 1);
        OneChunkBiome after = OneChunkBiome.forChunk(is.getChunksCleared(), per);
        generate(island, is);
        String msg = "§6OneChunk §7» §aChunk " + is.getChunksCleared() + " completado. "
                + (before != after ? "§eNuevo bioma: §f" + after.displayName() + "§e. Cuidado con lo que aparece."
                        : "§7Bioma: §f" + after.displayName() + "§7.");
        for (Player p : island.getPlayersOnIsland()) {
            p.sendMessage(msg);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    /** ¿Está {@code l} dentro de la huella 16x16 del chunk de la isla (cualquier altura)? */
    public static boolean inFootprint(Location center, Location l) {
        return l.getWorld() != null && l.getWorld().equals(center.getWorld())
                && (l.getBlockX() >> 4) == (center.getBlockX() >> 4) && (l.getBlockZ() >> 4) == (center.getBlockZ() >> 4);
    }

    /** Surface Y for a dimension column (64 in Nether/End, island center Y in Overworld). */
    public static int surfaceYForWorld(World world, Location islandCenter) {
        if (world == null || world.getEnvironment() == World.Environment.NORMAL) {
            return islandCenter != null ? islandCenter.getBlockY() : OneChunkGenerator.DIMENSION_SURFACE_Y;
        }
        return OneChunkGenerator.safeSurfaceY(world, OneChunkGenerator.DIMENSION_SURFACE_Y);
    }

    /** Generates or advances an island's 16x16 OneChunk column in Nether or The End. */
    public OneChunkGenerator.Result generateDimension(Island island, OneBlockIslands is, World dimWorld) {
        Location c = island.getCenter();
        if (c == null || dimWorld == null || dimWorld.getEnvironment() == World.Environment.NORMAL) {
            return null;
        }
        boolean isEnd = dimWorld.getEnvironment() == World.Environment.THE_END;
        int surfaceY = surfaceYForWorld(dimWorld, c);
        int depth = depth(dimWorld, surfaceY);
        int index = isEnd ? is.getEndChunksCleared() : is.getNetherChunksCleared();
        int per = addon.getSettings().getOneChunkChunksPerBiome();
        OneChunkDimensionBiome biome = OneChunkDimensionBiome.forDimension(dimWorld.getEnvironment(), index, per);
        long seed = (island.getUniqueId().hashCode() * 31L + index)
                ^ (isEnd ? 0x5448455F454E44L : 0x4E4554484552L);

        dimWorld.getChunkAt(c.getBlockX() >> 4, c.getBlockZ() >> 4).load(true);
        OneChunkGenerator.Result r = OneChunkGenerator.generateDimension(
                dimWorld, c.getBlockX(), c.getBlockZ(), surfaceY, depth, biome, index, seed);
        if (isEnd) {
            is.setEndSolidInChunk(r.solidBlocks());
            is.setEndBrokenInChunk(0);
        } else {
            is.setNetherSolidInChunk(r.solidBlocks());
            is.setNetherBrokenInChunk(0);
        }
        if (r.spawn() != null) {
            island.setSpawnPoint(dimWorld.getEnvironment(), r.spawn());
            IslandsManager.saveIsland(island);
        }
        int floor = OneChunkGenerator.floorY(surfaceY, depth);
        int ox = (c.getBlockX() >> 4) << 4;
        int oz = (c.getBlockZ() >> 4) << 4;
        relocateDimensionEntities(dimWorld, ox, oz, floor, OneChunkGenerator.topY(surfaceY), r.spawn(), index);
        spawnDimensionMobs(dimWorld, new Location(dimWorld, c.getBlockX(), surfaceY, c.getBlockZ()), biome, index,
                new Random(seed ^ 0x5DEECE66DL));
        return r;
    }

    /**
     * Ensures an island's Nether or End column exists on disk before a player arrives.
     * Existing player-mined columns with a bedrock floor are adopted without overwriting blocks.
     */
    public void ensureDimensionGenerated(Island island, OneBlockIslands is, World dimWorld) {
        Location c = island.getCenter();
        if (c == null || dimWorld == null || dimWorld.getEnvironment() == World.Environment.NORMAL) {
            return;
        }
        dimWorld.getChunkAt(c.getBlockX() >> 4, c.getBlockZ() >> 4).load(true);
        int surfaceY = surfaceYForWorld(dimWorld, c);
        int depth = depth(dimWorld, surfaceY);
        int floor = OneChunkGenerator.floorY(surfaceY, depth);
        int ox = (c.getBlockX() >> 4) << 4;
        int oz = (c.getBlockZ() >> 4) << 4;
        int remaining = OneChunkGenerator.countSolid(dimWorld, ox, oz, floor, OneChunkGenerator.topY(surfaceY));
        boolean isEnd = dimWorld.getEnvironment() == World.Environment.THE_END;
        int recordedSolid = isEnd ? is.getEndSolidInChunk() : is.getNetherSolidInChunk();
        if (remaining == 0) {
            generateDimension(island, is, dimWorld);
        } else if (recordedSolid <= 0) {
            if (isEnd) {
                is.setEndSolidInChunk(Math.max(1, remaining));
            } else {
                is.setNetherSolidInChunk(Math.max(1, remaining));
            }
        }
        ensureReturnPad(island, dimWorld);
    }

    /**
     * Places a compact obsidian return pad and exit portal immediately west of the 16x16 mining chunk
     * (inside the island's protected range, outside the regenerating footprint so chunk resets keep it intact).
     */
    public void ensureReturnPad(Island island, World dimWorld) {
        Location c = island.getCenter();
        if (c == null || dimWorld == null || dimWorld.getEnvironment() == World.Environment.NORMAL) {
            return;
        }
        int ox = (c.getBlockX() >> 4) << 4;
        int oz = (c.getBlockZ() >> 4) << 4;
        int surfaceY = surfaceYForWorld(dimWorld, c);
        dimWorld.getChunkAt((ox - 4) >> 4, (oz + 6) >> 4).load(true);

        Material portalType = dimWorld.getEnvironment() == World.Environment.NETHER
                ? Material.NETHER_PORTAL
                : Material.END_PORTAL;
        for (int x = ox - 4; x <= ox - 1; x++) {
            for (int z = oz + 6; z <= oz + 10; z++) {
                for (int y = surfaceY; y <= surfaceY + 5; y++) {
                    if (dimWorld.getBlockAt(x, y, z).getType() == portalType) {
                        return;
                    }
                }
            }
        }
        for (int x = ox - 4; x <= ox - 1; x++) {
            for (int z = oz + 6; z <= oz + 10; z++) {
                if (dimWorld.getBlockAt(x, surfaceY, z).getType().isAir()) {
                    dimWorld.getBlockAt(x, surfaceY, z).setType(Material.OBSIDIAN, false);
                }
            }
        }
        if (dimWorld.getEnvironment() == World.Environment.NETHER) {
            int px = ox - 3;
            for (int z = oz + 6; z <= oz + 9; z++) {
                for (int y = surfaceY + 1; y <= surfaceY + 5; y++) {
                    boolean frame = z == oz + 6 || z == oz + 9 || y == surfaceY + 1 || y == surfaceY + 5;
                    if (frame) {
                        dimWorld.getBlockAt(px, y, z).setType(Material.OBSIDIAN, false);
                    } else {
                        BlockData bd = Material.NETHER_PORTAL.createBlockData();
                        if (bd instanceof Orientable orientable) {
                            orientable.setAxis(Axis.Z);
                        }
                        dimWorld.getBlockAt(px, y, z).setBlockData(bd, false);
                    }
                }
            }
        } else {
            // Raised 1-block bedrock rim around a single END_PORTAL return block so players cannot step in accidentally.
            for (int x = ox - 4; x <= ox - 2; x++) {
                for (int z = oz + 7; z <= oz + 9; z++) {
                    Material m = (x == ox - 3 && z == oz + 8) ? Material.END_PORTAL : Material.BEDROCK;
                    dimWorld.getBlockAt(x, surfaceY + 1, z).setType(m, false);
                }
            }
        }
    }

    /** Handles block breaks inside an island's Nether or End 16x16 mining column. */
    public boolean onDimensionBlockBroken(Island island, OneBlockIslands is, World dimWorld) {
        Location c = island.getCenter();
        if (c == null || dimWorld == null || dimWorld.getEnvironment() == World.Environment.NORMAL) {
            return false;
        }
        boolean isEnd = dimWorld.getEnvironment() == World.Environment.THE_END;
        int broken = (isEnd ? is.getEndBrokenInChunk() : is.getNetherBrokenInChunk()) + 1;
        if (isEnd) {
            is.setEndBrokenInChunk(broken);
        } else {
            is.setNetherBrokenInChunk(broken);
        }
        if (broken % SCAN_EVERY != 0) {
            return false;
        }
        int remaining = getRemainingDimensionSolid(island, dimWorld);
        int solid = isEnd ? is.getEndSolidInChunk() : is.getNetherSolidInChunk();
        if (solid <= 0) {
            if (isEnd) {
                is.setEndSolidInChunk(Math.max(1, remaining));
            } else {
                is.setNetherSolidInChunk(Math.max(1, remaining));
            }
            return false;
        }
        if (!isCleared(solid, remaining, addon.getSettings().getOneChunkClearFraction())) {
            return false;
        }
        int per = addon.getSettings().getOneChunkChunksPerBiome();
        int clearedBefore = isEnd ? is.getEndChunksCleared() : is.getNetherChunksCleared();
        OneChunkDimensionBiome before = OneChunkDimensionBiome.forDimension(dimWorld.getEnvironment(), clearedBefore, per);
        int clearedAfter = clearedBefore + 1;
        if (isEnd) {
            is.setEndChunksCleared(clearedAfter);
        } else {
            is.setNetherChunksCleared(clearedAfter);
        }
        OneChunkDimensionBiome after = OneChunkDimensionBiome.forDimension(dimWorld.getEnvironment(), clearedAfter, per);
        generateDimension(island, is, dimWorld);
        String msg = "§6OneChunk §7» §aDimension chunk " + clearedAfter + " cleared! "
                + (before != after ? "§eNew biome: §f" + after.displayName() + "§e."
                        : "§7Biome: §f" + after.displayName() + "§7.");
        for (Player p : dimWorld.getPlayers()) {
            if (island.onIsland(p.getLocation())) {
                p.sendMessage(msg);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }
        return true;
    }

    public int getRemainingDimensionSolid(Island island, World dimWorld) {
        Location c = island.getCenter();
        if (c == null || dimWorld == null) {
            return 0;
        }
        int surfaceY = surfaceYForWorld(dimWorld, c);
        int depth = depth(dimWorld, surfaceY);
        int floor = OneChunkGenerator.floorY(surfaceY, depth);
        int ox = (c.getBlockX() >> 4) << 4;
        int oz = (c.getBlockZ() >> 4) << 4;
        return OneChunkGenerator.countSolid(dimWorld, ox, oz, floor, OneChunkGenerator.topY(surfaceY));
    }

    private static void relocateDimensionEntities(World world, int ox, int oz, int floor, int top, Location spawn,
            int chunkIndex) {
        for (Entity e : world.getNearbyEntities(BoundingBox.of(new Vector(ox, floor, oz), new Vector(ox + 16, top + 40, oz + 16)))) {
            if (e instanceof Player p) {
                p.teleport(spawn);
                p.setFallDistance(0f);
                p.setVelocity(new Vector(0, 0, 0));
                if (chunkIndex > 0) {
                    p.sendMessage("§6OneChunk §7» §eThe dimension chunk regenerated: moved you to safe ground.");
                }
            } else if (e instanceof Item || (e instanceof Tameable t && t.isTamed())) {
                e.teleport(spawn);
            } else if (e instanceof LivingEntity) {
                e.remove();
            }
        }
    }

    private void spawnDimensionMobs(World world, Location c, OneChunkDimensionBiome biome, int index, Random rnd) {
        if (index <= 0) {
            return;
        }
        int ox = (c.getBlockX() >> 4) << 4;
        int oz = (c.getBlockZ() >> 4) << 4;
        int y = c.getBlockY() + 1;
        EntityType[] pool = biome.hostiles();
        int types = unlockedTypes(index, pool.length);
        int n = hostileCount(index, addon.getSettings().getOneChunkMaxMobs());
        for (int i = 0; i < n; i++) {
            spawn(world, pool[rnd.nextInt(types)], ox, oz, y, c, rnd);
        }
    }

    /** Nº de mobs hostiles de un chunk: 0 en el primero (animales), luego 2 + número de chunk, con tope. */
    public static int hostileCount(int index, int max) {
        return index <= 0 ? 0 : Math.min(Math.max(0, max), 2 + index);
    }

    /** Cuántos tipos de mob del bioma están desbloqueados en el chunk {@code index} (el más fuerte llega más tarde). */
    public static int unlockedTypes(int index, int total) {
        return Math.max(1, Math.min(total, 1 + index / 3));
    }

    private void spawnMobs(World world, Location c, OneChunkBiome biome, int index, Random rnd) {
        int ox = (c.getBlockX() >> 4) << 4;
        int oz = (c.getBlockZ() >> 4) << 4;
        int y = c.getBlockY() + 1;
        if (index <= 0) {
            for (EntityType t : OneChunkBiome.STARTER_ANIMALS) {
                spawn(world, t, ox, oz, y, c, rnd);
            }
            return;
        }
        EntityType[] pool = biome.hostiles();
        int types = unlockedTypes(index, pool.length);
        int n = hostileCount(index, addon.getSettings().getOneChunkMaxMobs());
        for (int i = 0; i < n; i++) {
            spawn(world, pool[rnd.nextInt(types)], ox, oz, y, c, rnd);
        }
    }

    private void spawn(World world, EntityType type, int ox, int oz, int y, Location c, Random rnd) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = ox + rnd.nextInt(16);
            int z = oz + rnd.nextInt(16);
            // lejos del punto de aparición del jugador
            if (Math.abs(x - c.getBlockX()) <= 4 && Math.abs(z - c.getBlockZ()) <= 4) {
                continue;
            }
            if (world.getBlockAt(x, y, z).getType() != Material.AIR || world.getBlockAt(x, y + 1, z).getType() != Material.AIR) {
                continue;
            }
            Entity e = world.spawnEntity(new Location(world, x + 0.5, y, z + 0.5), type);
            e.setPersistent(true);
            if (e instanceof LivingEntity le) {
                le.setRemoveWhenFarAway(false);
            }
            return;
        }
    }
}
