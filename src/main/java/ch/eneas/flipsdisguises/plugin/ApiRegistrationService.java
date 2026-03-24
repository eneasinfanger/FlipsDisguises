package ch.eneas.flipsdisguises.plugin;

import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flips.loader.api.Service;
import ch.eneas.flipsdisguises.api.FlipsDisguisesApi;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

@Injectable
public final class ApiRegistrationService implements Service {

    private final FlipsDisguisesApi api;

    @Inject
    public ApiRegistrationService(FlipsDisguisesApi api) {
        this.api = api;
    }

    @Override
    public void startup(JavaPlugin myPlugin) {
        myPlugin.getServer().getServicesManager().register(FlipsDisguisesApi.class, api, myPlugin, ServicePriority.Normal);
    }

    @Override
    public void shutdown(JavaPlugin myPlugin) {
        myPlugin.getServer().getServicesManager().unregisterAll(myPlugin);
    }
}
