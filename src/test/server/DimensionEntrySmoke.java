import java.lang.reflect.Proxy;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;

/** Run ONLY on a fresh disposable server with BentoBox + OneChunk; shuts that server down. */
public final class DimensionEntrySmoke extends JavaPlugin {
    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("onechunk.disposable.smoke")) {
            throw new IllegalStateException("Disposable smoke flag required");
        }
        Bukkit.getScheduler().runTaskLater(this, this::runChecks, 80L);
    }

    private void runChecks() {
        try {
            var bentobox = Bukkit.getPluginManager().getPlugin("BentoBox");
            Object manager = bentobox.getClass().getMethod("getAddonsManager").invoke(bentobox);
            Object addon = ((Optional<?>) manager.getClass().getMethod("getAddonByName", String.class)
                    .invoke(manager, "OneChunk")).orElseThrow();
            Object settings = addon.getClass().getMethod("getSettings").invoke(addon);
            settings.getClass().getMethod("setSafeDimensionEntry", boolean.class).invoke(settings, true);
            Class<?> type = addon.getClass().getClassLoader().loadClass(
                    "world.bentobox.onechunk.listeners.DimensionEntryListener");
            Object listener = type.getConstructor(addon.getClass()).newInstance(addon);
            require(java.util.Arrays.stream(HandlerList.getRegisteredListeners(bentobox))
                    .anyMatch(registration -> registration.getListener().getClass() == type), "registered listener");
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                    new Class<?>[] { Player.class }, (proxy, method, args) ->
                            method.getName().equals("teleportAsync")
                                    ? java.util.concurrent.CompletableFuture.completedFuture(true) : null);
            World from = Bukkit.getWorld("onechunk_world");
            int checks = 0;
            for (String suffix : new String[] { "_nether", "_the_end" }) {
                World world = Bukkit.getWorld("onechunk_world" + suffix);
                require(world != null, "dimension loaded");
                world.getChunkAt(0, 0).load();
                require(world.getBlockAt(8, 64, 8).getType().isOccluding(), "generated platform");
                PlayerTeleportEvent event = new PlayerTeleportEvent(player, new Location(from, 4096, 200, 8192),
                        new Location(world, 4096, 200, 8192), TeleportCause.COMMAND);
                type.getMethod("onTeleport", PlayerTeleportEvent.class).invoke(listener, event);
                require(!event.isCancelled() && world.getWorldBorder().isInside(event.getTo()), "border arrival");
                require(event.getTo().getBlockY() == 65, "platform height");
                require(event.getTo().getBlock().getType().isAir(), "free headroom");
                PlayerPortalEvent portal = new PlayerPortalEvent(player, event.getFrom(),
                        new Location(world, 4096, 200, 8192), TeleportCause.NETHER_PORTAL, 128, true, 16);
                type.getMethod("onPortal", PlayerPortalEvent.class).invoke(listener, portal);
                require(portal.isCancelled() && !portal.getCanCreatePortal() && portal.getSearchRadius() == 0,
                        "portal stays on platform");
                checks += 5;
            }
            getLogger().info("ONECHUNK_DIMENSION_SMOKE PASS checks=" + checks);
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "ONECHUNK_DIMENSION_SMOKE FAIL", failure);
        } finally {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all flush");
            Bukkit.shutdown();
        }
    }

    private static void require(boolean condition, String check) {
        if (!condition) throw new IllegalStateException(check);
    }
}
