package world.bentobox.onechunk.commands.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import world.bentobox.onechunk.CommonTestSetup;
import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.Settings;
import world.bentobox.onechunk.dataobjects.OneBlockIslands;
import world.bentobox.onechunk.mining.OneChunkManager;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.localization.TextVariables;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.AbstractDatabaseHandler;
import world.bentobox.bentobox.database.DatabaseSetup;
import world.bentobox.bentobox.managers.CommandsManager;
import world.bentobox.bentobox.managers.PlayersManager;

class AdminSetCountCommandTest extends CommonTestSetup {

    @Mock
    private CompositeCommand ac;
    @Mock
    private User user;
    @Mock
    private OneChunk addon;
    @Mock
    private OneChunkManager oneChunkManager;
    @Mock
    private PlayersManager pm;

    private AdminSetCountCommand cmd;
    private final OneBlockIslands oneBlockIsland = new OneBlockIslands(UUID.randomUUID().toString());

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        deleteAll(new File("database"));
        deleteAll(new File("database_backup"));
    }

    @SuppressWarnings("unchecked")
    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        AbstractDatabaseHandler<Object> h = mock(AbstractDatabaseHandler.class);
        MockedStatic<DatabaseSetup> mockedDb = Mockito.mockStatic(DatabaseSetup.class);
        DatabaseSetup dbSetup = mock(DatabaseSetup.class);
        mockedDb.when(DatabaseSetup::getDatabase).thenReturn(dbSetup);
        when(dbSetup.getHandler(any())).thenReturn(h);
        when(h.saveObject(any())).thenReturn(CompletableFuture.completedFuture(true));

        CommandsManager cm = mock(CommandsManager.class);
        when(plugin.getCommandsManager()).thenReturn(cm);
        when(plugin.getPlayers()).thenReturn(pm);

        when(user.getWorld()).thenReturn(world);
        when(user.getTranslation(anyString())).thenAnswer(i -> i.getArgument(0, String.class));
        when(user.getTranslation(anyString(), any(String[].class))).thenAnswer(i -> i.getArgument(0, String.class));

        when(ac.getWorld()).thenReturn(world);
        when(ac.getAddon()).thenReturn(addon);

        when(addon.getOneBlocksIsland(island)).thenReturn(oneBlockIsland);
        when(addon.getOneChunkManager()).thenReturn(oneChunkManager);
        Settings settings = new Settings();
        when(addon.getSettings()).thenReturn(settings);

        cmd = new AdminSetCountCommand(ac);
        Field addonField = AdminSetCountCommand.class.getDeclaredField("addon");
        addonField.setAccessible(true);
        addonField.set(cmd, addon);
    }

    @Test
    void testSetup() {
        assertNotNull(cmd);
        assertEquals("admin.setcount", cmd.getPermission());
        assertEquals("onechunk.commands.admin.setcount.description", cmd.getDescription());
        assertEquals("onechunk.commands.admin.setcount.parameters", cmd.getParameters());
        assertFalse(cmd.isOnlyPlayer());
    }

    @Test
    void testExecuteHelpOnInvalidArgs() {
        assertFalse(cmd.execute(user, "setcount", Collections.emptyList()));
        assertFalse(cmd.execute(user, "setcount", List.of("player", "10", "invalid")));
    }

    @Test
    void testExecuteNegativeCount() {
        assertFalse(cmd.execute(user, "setcount", List.of("player", "-5")));
        verify(user).sendMessage("general.errors.must-be-positive-number", TextVariables.NUMBER, "-5");
    }

    @Test
    void testExecuteUnknownPlayer() {
        when(pm.getUUID("unknown")).thenReturn(null);
        assertFalse(cmd.execute(user, "setcount", List.of("unknown", "10")));
        verify(user).sendMessage("general.errors.unknown-player", TextVariables.NAME, "unknown");
    }

    @Test
    void testExecuteNoIsland() {
        UUID targetUUID = UUID.randomUUID();
        when(pm.getUUID("target")).thenReturn(targetUUID);
        when(im.getIsland(world, targetUUID)).thenReturn(null);

        assertFalse(cmd.execute(user, "setcount", List.of("target", "10")));
        verify(user).sendMessage("general.errors.no-island");
    }

    @Test
    void testExecuteSetBlockNumber() {
        UUID targetUUID = UUID.randomUUID();
        when(pm.getUUID("target")).thenReturn(targetUUID);
        when(pm.getName(targetUUID)).thenReturn("TargetPlayer");
        when(im.getIsland(world, targetUUID)).thenReturn(island);

        assertTrue(cmd.execute(user, "setcount", List.of("target", "42")));
        assertEquals(42, oneBlockIsland.getBlockNumber());
        verify(user).sendMessage("onechunk.commands.admin.setcount.set", TextVariables.NUMBER, "42", TextVariables.NAME, "TargetPlayer");
    }

    @Test
    void testExecuteSetLifetime() {
        UUID targetUUID = UUID.randomUUID();
        when(pm.getUUID("target")).thenReturn(targetUUID);
        when(pm.getName(targetUUID)).thenReturn("TargetPlayer");
        when(im.getIsland(world, targetUUID)).thenReturn(island);

        assertTrue(cmd.execute(user, "setcount", List.of("target", "100", "lifetime")));
        assertEquals(100, oneBlockIsland.getLifetime());
        verify(user).sendMessage("onechunk.commands.admin.setcount.set-lifetime", TextVariables.NUMBER, "100", TextVariables.NAME, "TargetPlayer");
    }

    @Test
    void testExecuteSetChunksOneChunkEnabled() {
        UUID targetUUID = UUID.randomUUID();
        when(pm.getUUID("target")).thenReturn(targetUUID);
        when(pm.getName(targetUUID)).thenReturn("TargetPlayer");
        when(im.getIsland(world, targetUUID)).thenReturn(island);

        Settings s = new Settings();
        s.setOneChunkEnabled(true);
        when(addon.getSettings()).thenReturn(s);

        assertTrue(cmd.execute(user, "setcount", List.of("target", "5", "chunks")));
        assertEquals(5, oneBlockIsland.getChunksCleared());
        verify(oneChunkManager).generate(island, oneBlockIsland);
        verify(user).sendMessage("onechunk.commands.admin.setcount.set-chunks", TextVariables.NUMBER, "5", TextVariables.NAME, "TargetPlayer");
    }

    @Test
    void testExecuteSetChunksOneChunkDisabled() {
        UUID targetUUID = UUID.randomUUID();
        when(pm.getUUID("target")).thenReturn(targetUUID);
        when(pm.getName(targetUUID)).thenReturn("TargetPlayer");
        when(im.getIsland(world, targetUUID)).thenReturn(island);

        Settings s = new Settings();
        s.setOneChunkEnabled(false);
        when(addon.getSettings()).thenReturn(s);

        assertTrue(cmd.execute(user, "setcount", List.of("target", "5", "chunks")));
        verify(user).sendMessage("onechunk.commands.admin.setcount.onechunk-required");
    }

    @Test
    void testTabComplete() {
        Settings s = new Settings();
        s.setOneChunkEnabled(false);
        when(addon.getSettings()).thenReturn(s);

        Optional<List<String>> opts = cmd.tabComplete(user, "setcount", List.of("player", "10", ""));
        assertTrue(opts.isPresent());
        assertEquals(List.of("lifetime"), opts.get());

        s.setOneChunkEnabled(true);
        opts = cmd.tabComplete(user, "setcount", List.of("player", "10", ""));
        assertTrue(opts.isPresent());
        assertEquals(List.of("lifetime", "chunks"), opts.get());
    }
}
