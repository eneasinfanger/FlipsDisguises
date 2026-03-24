package ch.eneas.flipsdisguises.listener;

import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.application.DisguiseService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

@Injectable
public final class DisguiseLifecycleListener implements Listener {

    private final DisguiseService disguiseService;

    @Inject
    public DisguiseLifecycleListener(DisguiseService disguiseService) {
        this.disguiseService = disguiseService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        disguiseService.forget(event.getPlayer().getUniqueId());
    }
}
