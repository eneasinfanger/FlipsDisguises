package ch.eneas.flipsdisguises.command;

import ch.eneas.PluginMain;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles how often a player can run disguise commands ({@code rate-limit-millis} in the config). Only commands are
 * limited; the programmatic API is not.
 */
@Injectable
public final class CommandRateLimiter {

    private final PluginMain plugin;
    private final Map<UUID, Long> lastActionByInitiator = new ConcurrentHashMap<>();

    @Inject
    public CommandRateLimiter(PluginMain plugin) {
        this.plugin = plugin;
    }

    /**
     * @return {@code false} if this initiator ran a command too recently; non-player senders are never limited
     */
    public boolean tryAcquire(CommandSender initiator) {
        if (!(initiator instanceof Player player)) {
            return true;
        }
        long limit = plugin.getConfig().getLong("rate-limit-millis", 1000L);
        if (limit <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        boolean[] allowed = {false};
        lastActionByInitiator.compute(player.getUniqueId(), (id, prev) -> {
            if (prev != null && now - prev < limit) {
                return prev;
            }
            allowed[0] = true;
            return now;
        });
        return allowed[0];
    }

    public void forget(UUID playerId) {
        lastActionByInitiator.remove(playerId);
    }
}
