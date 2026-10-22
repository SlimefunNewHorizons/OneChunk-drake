package world.bentobox.aoneblock.onechunk;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import world.bentobox.aoneblock.AOneBlock;
import world.bentobox.aoneblock.dataobjects.OneBlockIslands;
import world.bentobox.bentobox.database.objects.Island;

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

    private final AOneBlock addon;

    public OneChunkManager(AOneBlock addon) {
        this.addon = addon;
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
        int depth = addon.getSettings().getOneChunkDepth();
        int index = is.getChunksCleared();
        OneChunkBiome biome = OneChunkBiome.forChunk(index, per);
        long seed = island.getUniqueId().hashCode() * 31L + index;
        OneChunkGenerator.Result r = OneChunkGenerator.generate(c.getWorld(), c.getBlockX(), c.getBlockZ(), c.getBlockY(),
                depth, biome, index, seed);
        is.setSolidInChunk(r.solidBlocks());
        is.setBrokenInChunk(0);
        spawnMobs(c.getWorld(), c, biome, index, new Random(seed ^ 0x5DEECE66DL));
        for (Player p : island.getPlayersOnIsland()) {
            if (inFootprint(c, p.getLocation())) {
                p.teleportAsync(r.spawn());
            }
        }
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
        int depth = addon.getSettings().getOneChunkDepth();
        int floor = OneChunkGenerator.floorY(c.getBlockY(), depth);
        int ox = (c.getBlockX() >> 4) << 4;
        int oz = (c.getBlockZ() >> 4) << 4;
        int remaining = OneChunkGenerator.countSolid(c.getWorld(), ox, oz, floor, OneChunkGenerator.topY(c.getBlockY()));
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
