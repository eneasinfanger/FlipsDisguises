package ch.eneas.flipsdisguises.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class FlipsDisguises {

    private static final String PLUGIN_NAME = "FlipsDisguises";

    private FlipsDisguises() {
    }

    /**
     * @return whether {@link #api()} can currently be called
     */
    public static boolean isAvailable() {
        return loadService() != null;
    }

    /**
     * @throws FlipsDisguisesUnavailableException if the plugin is not installed, not enabled yet, or disabled
     */
    public static FlipsDisguisesApi api() {
        RegisteredServiceProvider<FlipsDisguisesApi> registered = loadService();
        if (registered != null) {
            return registered.getProvider();
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null) {
            throw new FlipsDisguisesUnavailableException(
                PLUGIN_NAME + " is not installed on this server. Install it, and add it to 'depend' or 'softdepend' "
                    + "in your plugin.yml so it loads before your plugin."
            );
        }
        if (!plugin.isEnabled()) {
            throw new FlipsDisguisesUnavailableException(
                PLUGIN_NAME + " is installed but not enabled (yet). Add it to 'depend' or 'softdepend' in your "
                    + "plugin.yml so it enables before your plugin, or call the API after server startup."
            );
        }
        throw new FlipsDisguisesUnavailableException(
            PLUGIN_NAME + " is enabled but its API is not registered; check the server log for startup errors."
        );
    }

    private static RegisteredServiceProvider<FlipsDisguisesApi> loadService() {
        return Bukkit.getServer().getServicesManager().getRegistration(FlipsDisguisesApi.class);
    }

    public static final class FlipsDisguisesUnavailableException extends IllegalStateException {
        public FlipsDisguisesUnavailableException(String message) {
            super(message);
        }
    }
}
