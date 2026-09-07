package ch.eneas.flipsdisguises.listener;

import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.application.DisguiseService;
import ch.eneas.flipsdisguises.disguise.port.MobDisguisePort;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Injectable
public final class DisguiseLifecycleListener implements Listener {

    private final DisguiseService disguiseService;
    private final MobDisguisePort mobDisguise;

    @Inject
    public DisguiseLifecycleListener(DisguiseService disguiseService, MobDisguisePort mobDisguise) {
        this.disguiseService = disguiseService;
        this.mobDisguise = mobDisguise;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        mobDisguise.syncNewObserver(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        disguiseService.forget(event.getPlayer().getUniqueId());
    }
}
