package net.pikzstudio.donututils.disablephantom;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

public class EventListener implements Listener {

    private final DisablePhantomPlugin plugin;
    private final DatabaseManager databaseManager;

    public EventListener(DisablePhantomPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @EventHandler
    public void onPhantomSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.PHANTOM) return;

        // Only block natural phantom spawns
        SpawnReason reason = event.getSpawnReason();
        if (reason != SpawnReason.NATURAL) {
            return;
        }

        FileConfiguration config = plugin.getConfig();
        double maxRange = config.getDouble("Max-Range", 32.0D);
        double maxRangeSq = maxRange * maxRange;

        // Cancel if any nearby player has phantoms disabled
        for (Player player : event.getLocation().getWorld().getPlayers()) {
            if (player.getWorld() != event.getLocation().getWorld()) continue;
            if (player.getLocation().distanceSquared(event.getLocation()) <= maxRangeSq) {
                if (databaseManager.isPhantomDisabled(player.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}