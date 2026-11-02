package ch.eneas.flipsdisguises.plugin;

import ch.eneas.PluginMain;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;

@Injectable
public final class PaperSyncTaskRunner implements SyncTaskRunner {

    private final PluginMain plugin;

    @Inject
    public PaperSyncTaskRunner(PluginMain plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
