package ch.eneas;

import ch.eneas.flips.loader.api.FlipsLoader;
import ch.eneas.flips.loader.api.FlipsLoaderConfig.DebugCategory;
import ch.eneas.flips.loader.api.Injectable;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

@Injectable
public class Main extends JavaPlugin {
    private static final String SHADED_PACKAGE = "ch.eneas.flipsdisguises.shaded";
    private volatile boolean loaderStarted;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        try {
            var api = SpigotPacketEventsBuilder.build(this);
            PacketEvents.setAPI(api);
            api.load();
            api.init();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "PacketEvents failed to start; disabling plugin.", ex);
            try {
                if (PacketEvents.getAPI() != null) {
                    PacketEvents.getAPI().terminate();
                }
            } catch (Exception ignored) {
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        FlipsLoader.getLoader().initPluginClasses(this, c -> c.ignoredPackages(List.of(SHADED_PACKAGE))
            .debugLoader(DebugCategory.CLASS_LOADING, DebugCategory.INITIALIZATION));
        loaderStarted = true;
    }

    @Override
    public void onDisable() {
        if (loaderStarted) {
            try {
                FlipsLoader.deinitPlugin(this);
            } catch (Exception e) {
                getSLF4JLogger().error("Failed to deinitialize FlipsLoader:", e);
            }
            loaderStarted = false;
        }
        try {
            if (PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized()) {
                PacketEvents.getAPI().terminate();
            }
        } catch (Exception ex) {
            getSLF4JLogger().error("Failed to terminate PacketEvents API:", ex);
        }
    }
}
