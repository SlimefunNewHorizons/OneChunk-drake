package world.bentobox.onechunk.commands.island;

import java.util.List;

import world.bentobox.onechunk.OneChunk;
import world.bentobox.onechunk.listeners.BossBarListener;
import world.bentobox.bentobox.api.commands.CompositeCommand;
import world.bentobox.bentobox.api.metadata.MetaDataValue;
import world.bentobox.bentobox.api.user.User;

public class IslandActionBarCommand extends CompositeCommand {

    private OneChunk addon;

    public IslandActionBarCommand(CompositeCommand islandCommand, String label, String[] aliases)
    {
        super(islandCommand, label, aliases);
    }

    @Override
    public void setup() {
        setDescription("onechunk.commands.island.actionbar.description");
        setOnlyPlayer(true);
        // Permission
        setPermission("island.actionbar");
        addon = getAddon();
    }

    @Override
    public boolean execute(User user, String label, List<String> args) {
        getIslands().getIslandAt(user.getLocation()).ifPresent(i -> {
            if (!i.isAllowed(addon.ONECHUNK_ACTIONBAR)) {
                user.sendMessage("onechunk.actionbar.not-active");
            }
        });
        // Toggle state
        boolean newState = !user.getMetaData(BossBarListener.ONECHUNK_ACTIONBAR).map(MetaDataValue::asBoolean).orElse(true);
        user.putMetaData(BossBarListener.ONECHUNK_ACTIONBAR, new MetaDataValue(newState));
        if (newState) {
             user.sendMessage("onechunk.commands.island.actionbar.status_on");
        } else {
            user.sendMessage("onechunk.commands.island.actionbar.status_off");
        }
        return true;
    }
}
