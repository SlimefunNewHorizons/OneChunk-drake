package world.bentobox.onechunk.commands.island;

import java.util.List;

import world.bentobox.onechunk.OneChunk;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.user.User;

public class IslandBossBarCommand extends CompositeCommand {

    private OneChunk addon;

    public IslandBossBarCommand(CompositeCommand islandCommand, String label, String[] aliases)
    {
        super(islandCommand, label, aliases);
    }

    @Override
    public void setup() {
        setDescription("onechunk.commands.island.bossbar.description");
        setOnlyPlayer(true);
        // Permission
        setPermission("island.bossbar");
        addon = getAddon();
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        addon.getBossBar().toggleUser(user);
        getIslands().getIslandAt(user.getLocation()).ifPresent(i -> {
            if (!i.isAllowed(addon.ONECHUNK_BOSSBAR)) {
                user.sendMessage("onechunk.bossbar.not-active");
            }
        });
        return true;
    }
}
