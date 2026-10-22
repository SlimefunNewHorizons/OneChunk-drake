package world.bentobox.onechunk.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Cuando el chunk se regenera, el terreno nuevo se genera ENCIMA de lo que hubiera dentro: nadie puede quedar
 * enterrado. Jugadores (miembros y visitantes) y objetos van a la superficie; los mobs viejos se eliminan.
 */
class OneChunkRelocationTest {

    private static final int CX = 40;
    private static final int CZ = 40;
    private static final int SURFACE = 100;
    private static final int DEPTH = 12;
    private static final int FLOOR = SURFACE - DEPTH;

    private ServerMock server;
    private World world;
    private OneChunkGenerator.Result result;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("oc");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Regenera el chunk y reubica, igual que OneChunkManager.generate. */
    private void regenerate(OneChunkBiome biome, int index) {
        result = OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, biome, index, 5L);
        OneChunkManager.relocateEntities(world, 32, 32, FLOOR, OneChunkGenerator.topY(SURFACE), result.spawn());
    }

    private boolean isSafe(Location l) {
        return world.getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ()).getType().isAir()
                && world.getBlockAt(l.getBlockX(), l.getBlockY() + 1, l.getBlockZ()).getType().isAir()
                && !world.getBlockAt(l.getBlockX(), l.getBlockY() - 1, l.getBlockZ()).getType().isAir();
    }

    @Test
    void playerDeepInsideIsBroughtToTheSurface() {
        OneChunkGenerator.generate(world, CX, CZ, SURFACE, DEPTH, OneChunkBiome.FOREST, 0, 1L);
        PlayerMock deep = server.addPlayer();
        deep.teleport(new Location(world, 35.5, FLOOR + 3, 44.5)); // minando abajo, a 8 bloques de la superficie
        regenerate(OneChunkBiome.DESERT, 3);
        assertEquals(CX + 0.5, deep.getLocation().getX(), 0.01);
        assertEquals(SURFACE + 1, deep.getLocation().getBlockY());
        assertTrue(isSafe(deep.getLocation()), "el jugador queda de pie sobre suelo, con 2 bloques de aire");
    }

    @Test
    void visitorsAreProtectedToo() {
        PlayerMock owner = server.addPlayer();
        PlayerMock visitor = server.addPlayer();
        owner.teleport(new Location(world, 33.5, FLOOR + 2, 33.5));
        visitor.teleport(new Location(world, 45.5, SURFACE + 5, 45.5)); // cayendo sobre el chunk
        regenerate(OneChunkBiome.SNOW, 6);
        assertTrue(isSafe(owner.getLocation()));
        assertTrue(isSafe(visitor.getLocation()), "un visitante dentro del volumen también se traslada");
    }

    @Test
    void droppedItemsAreRescuedNotBuried() {
        Item item = world.dropItem(new Location(world, 38.5, FLOOR + 4, 38.5), new ItemStack(Material.DIAMOND, 5));
        regenerate(OneChunkBiome.JUNGLE, 9);
        assertFalse(item.isDead(), "el objeto sigue existiendo");
        assertEquals(SURFACE + 1, item.getLocation().getBlockY(), "y está en la superficie, no sepultado");
    }

    @Test
    void oldMobsAreRemovedInsteadOfSuffocating() {
        Zombie old = world.spawn(new Location(world, 36.5, FLOOR + 5, 36.5), Zombie.class);
        regenerate(OneChunkBiome.CAVE, 12);
        assertTrue(old.isDead(), "el zombi del chunk anterior se elimina");
    }

    @Test
    void thingsOutsideTheFootprintAreUntouched() {
        PlayerMock far = server.addPlayer();
        Location away = new Location(world, 200.5, 64, 200.5);
        far.teleport(away);
        Zombie other = world.spawn(new Location(world, 100.5, 64, 100.5), Zombie.class);
        regenerate(OneChunkBiome.FOREST, 0);
        assertEquals(200, far.getLocation().getBlockX());
        assertFalse(other.isDead(), "un mob de otra isla no se toca");
    }
}
