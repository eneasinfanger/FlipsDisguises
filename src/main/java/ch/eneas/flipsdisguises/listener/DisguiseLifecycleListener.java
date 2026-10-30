package ch.eneas.flipsdisguises.listener;

import ch.eneas.PluginMain;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.application.DisguiseService;
import ch.eneas.flipsdisguises.disguise.port.MobDisguisePort;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

@Injectable
public final class DisguiseLifecycleListener implements Listener {

    /** Ticks after the client reports its world loaded before a saved disguise is re-applied. */
    private static final long RESTORE_DELAY_TICKS = 5L;
    /** Fallback for clients that never send the loaded notification. */
    private static final long RESTORE_FALLBACK_DELAY_TICKS = 100L;
    /** Ticks to let the client finish respawning before disguise labels are re-applied. */
    private static final long RESPAWN_DELAY_TICKS = 2L;

    private final PluginMain plugin;
    private final DisguiseService disguiseService;
    private final MobDisguisePort mobDisguise;

    @Inject
    public DisguiseLifecycleListener(PluginMain plugin, DisguiseService disguiseService, MobDisguisePort mobDisguise) {
        this.plugin = plugin;
        this.disguiseService = disguiseService;
        this.mobDisguise = mobDisguise;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        mobDisguise.syncNewObserver(player);
        // Normally the restore runs from onClientLoaded; this covers a client that never reports it.
        scheduleRestore(player.getUniqueId(), RESTORE_FALLBACK_DELAY_TICKS);
    }

    /**
     * Packets sent while the client is still on the loading screen (the decoy, the self-invisibility flag) can be
     * lost, which leaves the rejoining player invisible to themselves. Wait until the client reports the world loaded.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClientLoaded(PlayerClientLoadedWorldEvent event) {
        scheduleRestore(event.getPlayer().getUniqueId(), RESTORE_DELAY_TICKS);
    }

    private void scheduleRestore(UUID id, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player online = plugin.getServer().getPlayer(id);
            if (online != null) {
                disguiseService.restorePersisted(online);
            }
        }, delayTicks);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player online = plugin.getServer().getPlayer(id);
            if (online != null) {
                disguiseService.reapplyAfterRespawn(online);
            }
        }, RESPAWN_DELAY_TICKS);
    }

    /**
     * Fires when a mob's chunk loads (including after a restart). The port forgets a mob's disguise when it leaves
     * the world, so the saved one is re-applied here; one tick later so the entity is fully added and trackable.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof Player) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> disguiseService.restorePersistedLiving(living));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        disguiseService.forget(event.getPlayer().getUniqueId());
    }
}
