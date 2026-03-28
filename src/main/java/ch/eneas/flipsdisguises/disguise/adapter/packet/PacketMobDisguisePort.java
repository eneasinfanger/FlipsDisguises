package ch.eneas.flipsdisguises.disguise.adapter.packet;

import ch.eneas.Main;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.port.MobDisguisePort;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Injectable
public final class PacketMobDisguisePort implements MobDisguisePort, PacketListener, Listener {

    private final Main plugin;
    private final Map<UUID, EntityType> mobTypeByPlayer = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> disguisedEntityIds = new ConcurrentHashMap<>();
    private volatile PacketListenerCommon packetHandle;

    @Inject
    public PacketMobDisguisePort(Main plugin) {
        this.plugin = plugin;
    }

    public void registerPacketListener() {
        if (packetHandle != null) {
            return;
        }
        packetHandle = PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
    }

    public void unregisterPacketListener() {
        if (packetHandle != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetHandle);
            packetHandle = null;
        }
    }

    @Override
    public boolean isMobDisguised(UUID playerId) {
        return mobTypeByPlayer.containsKey(playerId);
    }

    @Override
    public void attach(Player subject, EntityType type) {
        UUID sid = subject.getUniqueId();
        mobTypeByPlayer.put(sid, type);
        syncEntityId(subject);
        subject.hideEntity(plugin, subject);
        refreshOthers(subject);
    }

    @Override
    public void detach(Player subject) {
        UUID sid = subject.getUniqueId();
        mobTypeByPlayer.remove(sid);
        disguisedEntityIds.entrySet().removeIf(e -> e.getValue().equals(sid));
        subject.showEntity(plugin, subject);
        refreshOthers(subject);
    }

    @Override
    public void detachDecoyOnly(Player subject) {
        UUID sid = subject.getUniqueId();
        mobTypeByPlayer.remove(sid);
        disguisedEntityIds.entrySet().removeIf(e -> e.getValue().equals(sid));
    }

    @Override
    public void detachDisconnected(UUID subjectId) {
        mobTypeByPlayer.remove(subjectId);
        disguisedEntityIds.entrySet().removeIf(e -> e.getValue().equals(subjectId));
    }

    @Override
    public void syncNewObserver(Player observer) {
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player p = event.getPlayer();
            if (mobTypeByPlayer.containsKey(p.getUniqueId())) {
                syncEntityId(p);
                refreshOthers(p);
            }
        });
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity w = new WrapperPlayServerSpawnEntity(event);
            if (rewriteIfDisguised(event, w.getEntityId())) {
                w.setEntityType(resolvePeType(w.getEntityId()));
                event.markForReEncode(true);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            WrapperPlayServerSpawnLivingEntity w = new WrapperPlayServerSpawnLivingEntity(event);
            if (rewriteIfDisguised(event, w.getEntityId())) {
                w.setEntityType(resolvePeType(w.getEntityId()));
                w.setEntityMetadata(new ArrayList<>());
                event.markForReEncode(true);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata w = new WrapperPlayServerEntityMetadata(event);
            if (rewriteIfDisguised(event, w.getEntityId())) {
                w.setEntityMetadata(new ArrayList<>());
                event.markForReEncode(true);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            WrapperPlayServerEntityEquipment w = new WrapperPlayServerEntityEquipment(event);
            if (rewriteIfDisguised(event, w.getEntityId())) {
                w.setEquipment(new ArrayList<>());
                event.markForReEncode(true);
            }
        }
    }

    private boolean rewriteIfDisguised(PacketSendEvent event, int entityId) {
        UUID subjectUuid = disguisedEntityIds.get(entityId);
        if (subjectUuid == null) {
            return false;
        }
        if (mobTypeByPlayer.get(subjectUuid) == null) {
            return false;
        }
        Player observer = event.getPlayer();
        if (observer != null && observer.getUniqueId().equals(subjectUuid)) {
            return false;
        }
        return true;
    }

    private com.github.retrooper.packetevents.protocol.entity.type.EntityType resolvePeType(int entityId) {
        UUID subjectUuid = disguisedEntityIds.get(entityId);
        EntityType bukkit = mobTypeByPlayer.get(subjectUuid);
        return bukkit == null ? EntityTypes.COW : toPacketType(bukkit);
    }

    private void syncEntityId(Player player) {
        UUID sid = player.getUniqueId();
        if (!mobTypeByPlayer.containsKey(sid)) {
            return;
        }
        disguisedEntityIds.entrySet().removeIf(e -> e.getValue().equals(sid));
        disguisedEntityIds.put(player.getEntityId(), sid);
    }

    private void refreshOthers(Player subject) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                if (viewer.getUniqueId().equals(subject.getUniqueId())) {
                    continue;
                }
                viewer.hideEntity(plugin, subject);
                viewer.showEntity(plugin, subject);
            }
        });
    }

    private static com.github.retrooper.packetevents.protocol.entity.type.EntityType toPacketType(EntityType bukkit) {
        return switch (bukkit) {
            case COW -> EntityTypes.COW;
            case PIG -> EntityTypes.PIG;
            case CHICKEN -> EntityTypes.CHICKEN;
            case SHEEP -> EntityTypes.SHEEP;
            default -> EntityTypes.COW;
        };
    }
}
