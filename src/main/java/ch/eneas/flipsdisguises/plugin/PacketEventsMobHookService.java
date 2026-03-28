package ch.eneas.flipsdisguises.plugin;

import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flips.loader.api.Service;
import ch.eneas.flipsdisguises.disguise.adapter.packet.PacketMobDisguisePort;
import org.bukkit.plugin.java.JavaPlugin;

@Injectable
public final class PacketEventsMobHookService implements Service {

    private final PacketMobDisguisePort mobDisguise;

    @Inject
    public PacketEventsMobHookService(PacketMobDisguisePort mobDisguise) {
        this.mobDisguise = mobDisguise;
    }

    @Override
    public void startup(JavaPlugin myPlugin) {
        mobDisguise.registerPacketListener();
    }

    @Override
    public void shutdown(JavaPlugin myPlugin) {
        mobDisguise.unregisterPacketListener();
    }
}
