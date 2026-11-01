package world.bentobox.onechunk.commands.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.onechunk.CommonTestSetup;
import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.Settings;
import world.bentobox.onechunk.oneblocks.OneBlocksManager;

/**
 * Tests for {@link AdminSetChestCommand}
 */
class AdminSetChestCommandTest extends CommonTestSetup {

    @Mock
    private CompositeCommand ac;
    @Mock
    private User user;
    @Mock
    private OneChunk addon;
    @Mock
    private OneBlocksManager oneBlocksManager;

    private AdminSetChestCommand cmd;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        when(addon.getOneBlockManager()).thenReturn(oneBlocksManager);
        cmd = new AdminSetChestCommand(ac);
        java.lang.reflect.Field addonField = AdminSetChestCommand.class.getDeclaredField("addon");
        addonField.setAccessible(true);
        addonField.set(cmd, addon);
        when(user.isPlayer()).thenReturn(true);
    }

    @Test
    void testSetup() {
        assertNotNull(cmd);
        assertEquals("admin.setchest", cmd.getPermission());
        assertEquals("onechunk.commands.admin.setchest.description", cmd.getDescription());
        assertTrue(cmd.isOnlyPlayer());
    }

    @Test
    void testCanExecuteOneChunkDisabled() {
        Settings settings = new Settings();
        settings.setOneChunkEnabled(true);
        when(addon.getSettings()).thenReturn(settings);

        assertFalse(cmd.canExecute(user, "setchest", List.of("PLAINS", "COMMON")));
        verify(user).sendMessage("onechunk.commands.admin.setchest.onechunk-disabled");
    }

    @Test
    void testCanExecuteWrongArgCount() {
        Settings settings = new Settings();
        settings.setOneChunkEnabled(false);
        when(addon.getSettings()).thenReturn(settings);

        assertFalse(cmd.canExecute(user, "setchest", List.of()));
    }
}
