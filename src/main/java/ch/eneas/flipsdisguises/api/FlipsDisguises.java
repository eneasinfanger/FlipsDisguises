package ch.eneas.flipsdisguises.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class FlipsDisguises {

    private FlipsDisguises() {
    }

    public static FlipsDisguisesApi api() {
        RegisteredServiceProvider<FlipsDisguisesApi> registered =
            Bukkit.getServer().getServicesManager().getRegistration(FlipsDisguisesApi.class);
        if (registered == null) {
            throw new IllegalStateException("FlipsDisguisesApi is not registered");
        }
        return registered.getProvider();
    }
}
