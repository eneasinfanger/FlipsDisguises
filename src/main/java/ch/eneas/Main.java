package ch.eneas;

import ch.eneas.flips.loader.api.FlipsLoader;
import ch.eneas.flips.loader.api.Injectable;
import org.bukkit.plugin.java.JavaPlugin;

@Injectable
public class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        FlipsLoader.initPlugin(this);
    }

    @Override
    public void onDisable() {
        FlipsLoader.deinitPlugin(this);
    }
}
