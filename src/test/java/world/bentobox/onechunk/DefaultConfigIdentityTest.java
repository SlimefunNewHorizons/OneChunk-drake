package world.bentobox.onechunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * El config.yml del jar se copia en el primer arranque y PISA los valores por defecto de Settings. Si trajera los
 * comandos o el mundo de OneBlock, OneChunk chocaría con el OneBlock real (así pasó en staging: «Failed to register
 * command onechunk:ob»).
 */
class DefaultConfigIdentityTest {

    private static final YamlConfiguration CONFIG =
            YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));

    @Test
    void commandsDoNotCollideWithOneBlock() {
        String island = CONFIG.getString("onechunk.command.island");
        String admin = CONFIG.getString("onechunk.command.admin");
        for (String reserved : new String[] { "ob", "oneblock", "oba", "obadmin", "oneblockadmin" }) {
            assertFalse(island.contains(" " + reserved) || island.startsWith(reserved + " ") || island.equals(reserved),
                    "comando de jugador reservado por OneBlock: " + reserved);
            assertFalse(admin.contains(" " + reserved) || admin.startsWith(reserved + " ") || admin.equals(reserved),
                    "comando de admin reservado por OneBlock: " + reserved);
        }
        assertEquals("oc onechunk", island);
        assertEquals("ocadmin onechunkadmin oca", admin);
    }

    @Test
    void worldAndNameAreOwn() {
        assertEquals("onechunk_world", CONFIG.getString("world.world-name"));
        assertEquals("OneChunk", CONFIG.getString("world.friendly-name"));
    }

    /**
     * BentoBox NO deja cambiar la distancia entre islas con islas ya creadas: si no coincide con el rango guardado,
     * se deshabilita ENTERO (SkyBlock, OneBlock y OneChunk) con "Island distance mismatch". Pasó en Dallas el
     * 2026-10-23 02:18: el jar traía 400 y las islas de ChunkBlock tenían 256 (BentoBox caído ~5 h).
     * 256 es el valor de ChunkBlock, el addon al que sustituye OneChunk.
     */
    @Test
    void islandDistanceMatchesTheIslandsAlreadyInProduction() {
        assertEquals(256, CONFIG.getInt("world.distance-between-islands"));
        assertEquals(256, new Settings().getIslandDistance());
    }

    @Test
    void oneChunkIsOnByDefault() {
        assertEquals(true, CONFIG.getBoolean("world.onechunk.enabled"));
    }
}
