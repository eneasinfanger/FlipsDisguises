package ch.eneas.flipsdisguises.disguise.adapter.packet;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

/**
 * INFO-level tracing for PacketEvents play traffic. Enabled via {@code config.yml}; avoids DEBUG/TRACE on Paper.
 */
public final class MobDisguisePacketTrace {

    private MobDisguisePacketTrace() {
    }

    public static boolean enabled(JavaPlugin plugin) {
        return plugin.getConfig().getBoolean("mob-disguise-packet-trace", false);
    }

    /**
     * When true, logs every {@link ConnectionState#PLAY} send and receive. When false, only a focused subset
     * (spawn, bundles, entity meta/equipment, tab list, destroy, teams).
     */
    public static boolean allPlayTraffic(JavaPlugin plugin) {
        return plugin.getConfig().getBoolean("mob-disguise-packet-trace-all-play", false);
    }

    public static String typeName(PacketTypeCommon t) {
        return t instanceof Enum<?> e ? e.name() : String.valueOf(t);
    }

    public static String observerLabel(PacketSendEvent event) {
        Player p = event.getPlayer();
        return p != null ? p.getName() : "(no-bukkit-player)";
    }

    public static String playerLabel(PacketReceiveEvent event) {
        Player p = event.getPlayer();
        return p != null ? p.getName() : "(no-bukkit-player)";
    }

    public static boolean isFocusedServerType(PacketTypeCommon t) {
        return t == PacketType.Play.Server.BUNDLE
            || t == PacketType.Play.Server.SPAWN_PLAYER
            || t == PacketType.Play.Server.SPAWN_ENTITY
            || t == PacketType.Play.Server.SPAWN_LIVING_ENTITY
            || t == PacketType.Play.Server.ENTITY_METADATA
            || t == PacketType.Play.Server.ENTITY_EQUIPMENT
            || t == PacketType.Play.Server.DESTROY_ENTITIES
            || t == PacketType.Play.Server.PLAYER_INFO
            || t == PacketType.Play.Server.PLAYER_INFO_UPDATE
            || t == PacketType.Play.Server.PLAYER_INFO_REMOVE
            || t == PacketType.Play.Server.TEAMS;
    }

    public static void logServerOutbound(Logger log, JavaPlugin plugin, PacketSendEvent event, String detail) {
        if (!enabled(plugin)) {
            return;
        }
        PacketTypeCommon t = event.getPacketType();
        if (!allPlayTraffic(plugin) && !isFocusedServerType(t)) {
            return;
        }
        log.info(
            "[MobDisguise][SERVER->CLIENT] observer={} user={} type={} cancelled={} {}",
            observerLabel(event),
            event.getUser().getUUID(),
            typeName(t),
            event.isCancelled(),
            detail == null ? "" : detail
        );
    }

    /**
     * Logs every {@link ConnectionState#PLAY} serverbound packet when tracing is enabled.
     * Volume is usually lower than outbound; use {@link #allPlayTraffic(JavaPlugin)} only for configuration-phase noise if needed later.
     */
    public static void logClientInbound(Logger log, JavaPlugin plugin, PacketReceiveEvent event, String detail) {
        if (!enabled(plugin)) {
            return;
        }
        if (event.getConnectionState() != ConnectionState.PLAY) {
            return;
        }
        PacketTypeCommon t = event.getPacketType();
        log.info(
            "[MobDisguise][CLIENT->SERVER] player={} user={} type={} cancelled={} {}",
            playerLabel(event),
            event.getUser().getUUID(),
            typeName(t),
            event.isCancelled(),
            detail == null ? "" : detail
        );
    }
}
