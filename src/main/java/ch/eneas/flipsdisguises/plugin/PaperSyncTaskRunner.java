package ch.eneas.flipsdisguises.plugin;

import ch.eneas.Main;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;

@Injectable
public final class PaperSyncTaskRunner implements SyncTaskRunner {

    private final Main plugin;

    @Inject
    public PaperSyncTaskRunner(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public long rateLimitMillis() {
        return plugin.getConfig().getLong("rate-limit-millis", 1000L);
    }
}
