package ch.eneas;

import ch.eneas.flips.loader.api.FlipsLoader;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    @Override
    public void onEnable() {
        FlipsLoader.initPlugin(this);
    }

    @Override
    public void onDisable() {
        FlipsLoader.deinitPlugin(this);
    }
}