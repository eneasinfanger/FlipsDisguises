package ch.eneas.flipsdisguises.disguise.adapter.packet;

import ch.eneas.PluginMain;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.port.MobDisguisePort;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.exception.PacketProcessException;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.util.EventCreationUtil;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EntityEquipment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Injectable
public final class PacketMobDisguisePort implements MobDisguisePort, PacketListener, Listener {

    private static final Logger LOG = LoggerFactory.getLogger(PacketMobDisguisePort.class);

    private final PluginMain plugin;
    private final Map<UUID, EntityType> mobTypeByEntityUuid = new ConcurrentHashMap<>();
    private final Map<UUID, MobDisguiseOptions> mobOptionsByEntityUuid = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> disguisedEntityIds = new ConcurrentHashMap<>();
    /** Player-disguise name spoofing for player-info packets (tab list + overhead nameplate). */
    private final Map<UUID, String> spoofedNameByPlayerUuid = new ConcurrentHashMap<>();
    /**
     * Client-side-only decoy mob per disguised player so the subject sees their own disguise in third person.
     * The decoy is never sent to other viewers and never registered in {@link #disguisedEntityIds}, so
     * behaviour for everyone else is unchanged. Ids are chosen far above the server's entity id counter.
     */
    private final Map<UUID, Integer> decoyIdBySubjectUuid = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> decoyUuidBySubjectUuid = new ConcurrentHashMap<>();
    /**
     * Last-known PE items per equipment slot. Outgoing {@code ENTITY_EQUIPMENT} is often a delta; merging it fixes
     * wrong hands (stale Bukkit snapshots) and matches the server's authoritative item stacks.
     */
    private final Map<UUID, EnumMap<EquipmentSlot, ItemStack>> equipmentPeByEntityUuid = new ConcurrentHashMap<>();

    private static final List<EquipmentSlot> EQUIPMENT_WRITE_ORDER = List.of(
        EquipmentSlot.MAIN_HAND,
        EquipmentSlot.OFF_HAND,
        EquipmentSlot.BOOTS,
        EquipmentSlot.LEGGINGS,
        EquipmentSlot.CHEST_PLATE,
        EquipmentSlot.HELMET
    );
    private volatile PacketListenerCommon packetHandle;

    @Inject
    public PacketMobDisguisePort(PluginMain plugin) {
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
    public boolean isMobDisguised(UUID entityUuid) {
        return mobTypeByEntityUuid.containsKey(entityUuid);
    }

    @Override
    public void attach(LivingEntity subject, EntityType type, MobDisguiseOptions options) {
        UUID sid = subject.getUniqueId();
        mobTypeByEntityUuid.put(sid, type);
        mobOptionsByEntityUuid.put(sid, options == null ? MobDisguiseOptions.DEFAULT : options);
        syncEntityId(subject);
        if (plugin.getServer().isPrimaryThread()) {
            refreshEquipmentPeFromBukkit(subject);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> refreshEquipmentPeFromBukkit(subject));
        }
        refreshEntityTrackers(subject);
        if (subject instanceof Player) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> spawnSelfDecoy(subject), 3L);
        }
    }

    @Override
    public void detach(LivingEntity subject) {
        destroySelfDecoy(subject.getUniqueId());
        UUID sid = subject.getUniqueId();
        mobTypeByEntityUuid.remove(sid);
        mobOptionsByEntityUuid.remove(sid);
        equipmentPeByEntityUuid.remove(sid);
        disguisedEntityIds.entrySet().removeIf(e -> e.getValue().equals(sid));
        refreshEntityTrackers(subject);
    }

    @Override
    public void detachDecoyOnly(LivingEntity subject) {
        destroySelfDecoy(subject.getUniqueId());
        UUID sid = subject.getUniqueId();
        mobTypeByEntityUuid.remove(sid);
        mobOptionsByEntityUuid.remove(sid);
        equipmentPeByEntityUuid.remove(sid);
        disguisedEntityIds.entrySet().removeIf(e -> e.getValue().equals(sid));
    }

    @Override
    public void detachDisconnected(UUID entityUuid) {
        destroySelfDecoy(entityUuid);
        mobTypeByEntityUuid.remove(entityUuid);
        mobOptionsByEntityUuid.remove(entityUuid);
        equipmentPeByEntityUuid.remove(entityUuid);
        disguisedEntityIds.entrySet().removeIf(e -> e.getValue().equals(entityUuid));
    }

    @Override
    public void syncNewObserver(Player observer) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!observer.isOnline()) {
                return;
            }
            List<Player> toRefresh = new ArrayList<>();
            for (Player disguised : plugin.getServer().getOnlinePlayers()) {
                if (!mobTypeByEntityUuid.containsKey(disguised.getUniqueId())) {
                    continue;
                }
                if (disguised.getUniqueId().equals(observer.getUniqueId())) {
                    continue;
                }
                if (disguised.getWorld() != observer.getWorld()) {
                    continue;
                }
                if (!disguised.isValid()) {
                    continue;
                }
                toRefresh.add(disguised);
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!observer.isOnline()) {
                    return;
                }
                for (Player disguised : toRefresh) {
                    if (!disguised.isValid()) {
                        continue;
                    }
                    EntityType bukkitMob = mobTypeByEntityUuid.get(disguised.getUniqueId());
                    if (bukkitMob == null) {
                        continue;
                    }
                    MobDisguiseOptions opts = mobOptionsByEntityUuid.getOrDefault(
                        disguised.getUniqueId(),
                        MobDisguiseOptions.DEFAULT
                    );
                    sendExplicitMobDisguiseSpawn(observer, disguised, disguised.getEntityId(), bukkitMob, opts);
                }
            }, 2L);
        }, 1L);
    }

    @Override
    public void setSpoofedPlayerName(UUID playerUuid, String spoofedName) {
        if (spoofedName == null || spoofedName.isBlank()) {
            spoofedNameByPlayerUuid.remove(playerUuid);
        } else {
            spoofedNameByPlayerUuid.put(playerUuid, spoofedName);
        }
    }

    @Override
    public void clearSpoofedPlayerName(UUID playerUuid) {
        spoofedNameByPlayerUuid.remove(playerUuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player p = event.getPlayer();
            if (mobTypeByEntityUuid.containsKey(p.getUniqueId())) {
                syncEntityId(p);
                refreshEquipmentPeFromBukkit(p);
                refreshEntityTrackers(p);
                spawnSelfDecoy(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        Entity e = event.getEntity();
        if (e instanceof LivingEntity && !(e instanceof Player)) {
            detachDisconnected(e.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        if (mobTypeByEntityUuid.containsKey(p.getUniqueId())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                syncEntityId(p);
                refreshEquipmentPeFromBukkit(p);
                refreshEntityTrackers(p);
                spawnSelfDecoy(p);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityEquipmentChanged(EntityEquipmentChangedEvent event) {
        LivingEntity le = event.getEntity();
        if (mobTypeByEntityUuid.containsKey(le.getUniqueId())) {
            refreshEquipmentPeFromBukkit(le);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        if (mobTypeByEntityUuid.containsKey(p.getUniqueId())) {
            refreshEquipmentPeFromBukkit(p);
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getConnectionState() != ConnectionState.PLAY) {
            return;
        }
        MobDisguisePacketTrace.logClientInbound(LOG, plugin, event, "");
        swallowSelfDecoyInteraction(event);
        mirrorSelfDecoyOnMovement(event);
    }

    /**
     * The decoy only exists on the subject's client, so interact/attack packets aimed at its id are
     * meaningless server-side (unknown entity). Drop them so no other packet listener/plugin sees a
     * phantom entity id. Note: the vanilla client will not offer block break/place while the crosshair
     * rests on the decoy's hitbox — that ray trace happens fully client-side and cannot be disabled
     * for a mob entity; use the {@code selfDecoy} option to turn the decoy off if that is a problem.
     */
    private void swallowSelfDecoyInteraction(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }
        UUID subjectUuid = event.getUser().getUUID();
        Integer decoyId = decoyIdBySubjectUuid.get(subjectUuid);
        if (decoyId == null) {
            return;
        }
        try {
            WrapperPlayClientInteractEntity w = new WrapperPlayClientInteractEntity(event);
            if (w.getEntityId() == decoyId) {
                event.setCancelled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * The client's own movement is authoritative, so mirror the decoy onto serverbound movement packets.
     * The decoy is only known to the subject's own client; this keeps it glued to the real player position.
     */
    private void mirrorSelfDecoyOnMovement(PacketReceiveEvent event) {
        PacketTypeCommon type = event.getPacketType();
        boolean posPacket = type == PacketType.Play.Client.PLAYER_POSITION
            || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
        boolean rotPacket = type == PacketType.Play.Client.PLAYER_ROTATION
            || type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
        boolean flyingOnly = type == PacketType.Play.Client.PLAYER_FLYING;
        if (!posPacket && !rotPacket && !flyingOnly) {
            return;
        }
        UUID subjectUuid = event.getUser().getUUID();
        Integer decoyId = decoyIdBySubjectUuid.get(subjectUuid);
        if (decoyId == null || flyingOnly) {
            return;
        }
        Vector3d pos = null;
        Float yaw = null;
        Float pitch = null;
        boolean onGround = false;
        try {
            if (type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                WrapperPlayClientPlayerPositionAndRotation w = new WrapperPlayClientPlayerPositionAndRotation(event);
                pos = w.getPosition();
                yaw = w.getYaw();
                pitch = w.getPitch();
                onGround = w.isOnGround();
            } else if (type == PacketType.Play.Client.PLAYER_POSITION) {
                WrapperPlayClientPlayerPosition w = new WrapperPlayClientPlayerPosition(event);
                pos = w.getPosition();
                onGround = w.isOnGround();
            } else if (type == PacketType.Play.Client.PLAYER_ROTATION) {
                WrapperPlayClientPlayerRotation w = new WrapperPlayClientPlayerRotation(event);
                yaw = w.getYaw();
                pitch = w.getPitch();
                onGround = w.isOnGround();
            }
        } catch (Throwable t) {
            return;
        }
        if (pos == null && yaw == null) {
            return;
        }
        Player subject = plugin.getServer().getPlayer(subjectUuid);
        if (subject == null || !subject.isOnline()) {
            return;
        }
        double x = pos != null ? pos.getX() : subject.getX();
        double y = pos != null ? pos.getY() : subject.getY();
        double z = pos != null ? pos.getZ() : subject.getZ();
        float yv = yaw != null ? yaw : subject.getYaw();
        float pv = pitch != null ? pitch : subject.getPitch();
        EntityType bukkitMob = mobTypeByEntityUuid.get(subjectUuid);
        WrapperPlayServerEntityTeleport teleport = new WrapperPlayServerEntityTeleport(
            decoyId, new Vector3d(x, y, z), yv, pv, onGround
        );
        if (bukkitMob == EntityType.ENDER_DRAGON) {
            fixEnderDragonYawPitch(teleport.getYaw(), teleport.getPitch(), teleport::setYaw, teleport::setPitch);
        }
        User user = event.getUser();
        user.sendPacketSilently(teleport);
        float headYaw = bukkitMob == EntityType.ENDER_DRAGON ? wrapDegrees180(yv + 180f) : yv;
        user.sendPacketSilently(new WrapperPlayServerEntityHeadLook(decoyId, headYaw));
    }

    /**
     * Spawns a client-side-only mob at the subject's position, visible only to the subject, so they see their
     * own disguise in third person. Never registered in {@link #disguisedEntityIds}; the high random id keeps
     * it clear of the server's entity id counter.
     */
    private void spawnSelfDecoy(LivingEntity subject) {
        if (!(subject instanceof Player player) || !subject.isValid()) {
            return;
        }
        UUID sid = subject.getUniqueId();
        EntityType bukkitMob = mobTypeByEntityUuid.get(sid);
        if (bukkitMob == null) {
            return;
        }
        MobDisguiseOptions opts = mobOptionsByEntityUuid.getOrDefault(sid, MobDisguiseOptions.DEFAULT);
        if (Boolean.FALSE.equals(opts.selfDecoy())) {
            // Decoy disabled via option/API: the player just stays invisible to themselves.
            destroySelfDecoy(sid);
            sendSelfInvisibility(player, true);
            return;
        }
        destroySelfDecoy(sid);
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            return;
        }
        int decoyId;
        do {
            decoyId = 1_500_000_000 + ThreadLocalRandom.current().nextInt(500_000_000);
        } while (decoyId == subject.getEntityId()
            || disguisedEntityIds.containsKey(decoyId)
            || decoyIdBySubjectUuid.containsValue(decoyId));
        UUID decoyUuid = UUID.randomUUID();
        decoyIdBySubjectUuid.put(sid, decoyId);
        decoyUuidBySubjectUuid.put(sid, decoyUuid);

        Vector3d pos = new Vector3d(subject.getX(), subject.getY(), subject.getZ());
        PacketWrapper<?> spawn = buildMobSpawnReplacement(
            decoyId, decoyUuid, pos, subject.getYaw(), subject.getPitch(), bukkitMob, opts
        );
        spawn.setServerVersion(PacketEvents.getAPI().getServerManager().getVersion());
        spawn.setClientVersion(user.getClientVersion());
        WrapperPlayServerEntityMetadata metadata = new WrapperPlayServerEntityMetadata(
            decoyId,
            new ArrayList<>(MobDisguiseSyntheticMetadata.fragment(user.getClientVersion(), bukkitMob, opts))
        );
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(player, spawn);
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(player, metadata);
        EnumMap<EquipmentSlot, ItemStack> slots = equipmentPeByEntityUuid.get(sid);
        if (slots != null && !slots.isEmpty()) {
            PacketEvents.getAPI().getPlayerManager().sendPacketSilently(
                player, new WrapperPlayServerEntityEquipment(decoyId, toOrderedEquipmentList(slots))
            );
        }
        sendSelfInvisibility(player, true);
        sendSelfDecoyTeam(player, decoyUuid, true);
    }

    private void destroySelfDecoy(UUID subjectUuid) {
        Integer decoyId = decoyIdBySubjectUuid.remove(subjectUuid);
        UUID decoyUuid = decoyUuidBySubjectUuid.remove(subjectUuid);
        if (decoyId == null) {
            return;
        }
        Player subject = plugin.getServer().getPlayer(subjectUuid);
        if (subject == null || !subject.isOnline()) {
            return;
        }
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(
            subject, new WrapperPlayServerDestroyEntities(decoyId)
        );
        sendSelfInvisibility(subject, false);
        if (decoyUuid != null) {
            sendSelfDecoyTeam(subject, decoyUuid, false);
        }
    }

    /** Entity metadata index 0 bit marking an entity invisible; applied to the subject's own entity id client-side. */
    private static final byte SELF_INVISIBLE_FLAG = 0x20;
    /** Packet-only scoreboard team (per subject) used to disable client-side pushing between player and decoy. */
    private static final String SELF_DECOY_TEAM = "fd_selfdecoy";

    /**
     * Forces the invisible bit into an outgoing {@code ENTITY_METADATA} addressed to the disguised subject for
     * their own entity id, so the client never renders the real player model/armor/held items while a decoy
     * is shown. Without this, any genuine metadata update (sneak, fire, sprint) would clear the flag again.
     */
    private static void forceSelfInvisible(WrapperPlayServerEntityMetadata w) {
        forceSelfInvisible(w.getEntityMetadata());
    }

    private static void forceSelfInvisible(List<EntityData<?>> data) {
        for (EntityData<?> d : data) {
            if (d.getIndex() == 0 && d.getValue() instanceof Byte b) {
                @SuppressWarnings("unchecked")
                EntityData<Byte> byteData = (EntityData<Byte>) d;
                byteData.setValue((byte) (b | SELF_INVISIBLE_FLAG));
                return;
            }
        }
        data.add(new EntityData<>(0, EntityDataTypes.BYTE, SELF_INVISIBLE_FLAG));
    }

    private void sendSelfInvisibility(Player subject, boolean invisible) {
        WrapperPlayServerEntityMetadata meta = new WrapperPlayServerEntityMetadata(
            subject.getEntityId(),
            List.of(new EntityData<>(0, EntityDataTypes.BYTE, invisible ? SELF_INVISIBLE_FLAG : (byte) 0))
        );
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(subject, meta);
    }

    /**
     * Creates (or removes) a client-side-only scoreboard team containing the subject and the decoy with
     * {@code collisionRule=NEVER}, so the decoy's client-side bounding box does not push the local player.
     */
    private void sendSelfDecoyTeam(Player subject, UUID decoyUuid, boolean create) {
        WrapperPlayServerTeams packet;
        if (create) {
            WrapperPlayServerTeams.ScoreBoardTeamInfo info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                Component.empty(),
                Component.empty(),
                Component.empty(),
                WrapperPlayServerTeams.NameTagVisibility.NEVER,
                WrapperPlayServerTeams.CollisionRule.NEVER,
                NamedTextColor.WHITE,
                WrapperPlayServerTeams.OptionData.NONE
            );
            packet = new WrapperPlayServerTeams(
                SELF_DECOY_TEAM,
                WrapperPlayServerTeams.TeamMode.CREATE,
                Optional.of(info),
                List.of(subject.getName(), decoyUuid.toString())
            );
        } else {
            packet = new WrapperPlayServerTeams(
                SELF_DECOY_TEAM,
                WrapperPlayServerTeams.TeamMode.REMOVE,
                Optional.empty(),
                List.of()
            );
        }
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(subject, packet);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getConnectionState() != ConnectionState.PLAY) {
            return;
        }
        if (MobDisguisePacketTrace.enabled(plugin) && MobDisguisePacketTrace.allPlayTraffic(plugin)) {
            MobDisguisePacketTrace.logServerOutbound(LOG, plugin, event, "");
        }
        if (event.getPacketType() == PacketType.Play.Server.BUNDLE) {
            if (handleBundleMobDisguise(event)) {
                return;
            }
        }
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            WrapperPlayServerPlayerInfoUpdate tab = new WrapperPlayServerPlayerInfoUpdate(event);
            if (rewritePlayerInfoUpdateForMobSubjects(tab, event.getUser().getUUID())) {
                event.markForReEncode(true);
                MobDisguisePacketTrace.logServerOutbound(LOG, plugin, event, "disguise: PLAYER_INFO_UPDATE name rewrite");
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO) {
            WrapperPlayServerPlayerInfo tab = new WrapperPlayServerPlayerInfo(event);
            if (rewriteLegacyPlayerInfoForMobSubjects(tab, event.getUser().getUUID())) {
                event.markForReEncode(true);
                MobDisguisePacketTrace.logServerOutbound(LOG, plugin, event, "disguise: PLAYER_INFO name rewrite");
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
            WrapperPlayServerSpawnPlayer w = new WrapperPlayServerSpawnPlayer(event);
            if (rewriteIfDisguised(w.getEntityId())) {
                if (isOutgoingToDisguisedSubjectSelf(event, w.getEntityId())) {
                    return;
                }
                int eid = w.getEntityId();
                EntityType bukkitMob = bukkitMobType(eid);
                UUID uuid = w.getUUID();
                Vector3d p = w.getPosition();
                Vector3d pos = new Vector3d(p.getX(), p.getY(), p.getZ());
                float yaw = w.getYaw();
                float pitch = w.getPitch();
                User user = event.getUser();
                ServerVersion serverVersion = event.getServerVersion();
                MobDisguiseOptions disguiseOpts = optionsForEntityId(eid);
                PacketWrapper<?> replacement = buildMobSpawnReplacement(
                    eid,
                    uuid,
                    pos,
                    yaw,
                    pitch,
                    bukkitMob,
                    disguiseOpts
                );
                replacement.setServerVersion(serverVersion);
                replacement.setClientVersion(user.getClientVersion());
                event.setCancelled(true);
                MobDisguisePacketTrace.logServerOutbound(
                    LOG,
                    plugin,
                    event,
                    "mob disguise: cancelled SPAWN_PLAYER; postTask mob spawn eid=" + eid + " type=" + bukkitMob
                );
                sendReplacementAfterPipeline(event, replacement);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity w = new WrapperPlayServerSpawnEntity(event);
            if (rewriteIfDisguised(w.getEntityId())) {
                if (isOutgoingToDisguisedSubjectSelf(event, w.getEntityId())) {
                    return;
                }
                int eid = w.getEntityId();
                EntityType bukkitMob = bukkitMobType(eid);
                UUID subjectUuid = disguisedEntityIds.get(eid);
                UUID uuid = w.getUUID().orElse(subjectUuid);
                Vector3d p = w.getPosition();
                Vector3d pos = new Vector3d(p.getX(), p.getY(), p.getZ());
                float yaw = w.getYaw();
                float pitch = w.getPitch();
                User user = event.getUser();
                ServerVersion serverVersion = event.getServerVersion();
                MobDisguiseOptions disguiseOpts = optionsForEntityId(eid);
                PacketWrapper<?> replacement = buildMobSpawnReplacement(
                    eid,
                    uuid,
                    pos,
                    yaw,
                    pitch,
                    bukkitMob,
                    disguiseOpts
                );
                replacement.setServerVersion(serverVersion);
                replacement.setClientVersion(user.getClientVersion());
                event.setCancelled(true);
                MobDisguisePacketTrace.logServerOutbound(
                    LOG,
                    plugin,
                    event,
                    "mob disguise: cancelled SPAWN_ENTITY; postTask mob spawn eid=" + eid + " type=" + bukkitMob
                );
                sendReplacementAfterPipeline(event, replacement);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            WrapperPlayServerSpawnLivingEntity w = new WrapperPlayServerSpawnLivingEntity(event);
            if (rewriteIfDisguised(w.getEntityId())) {
                if (isOutgoingToDisguisedSubjectSelf(event, w.getEntityId())) {
                    return;
                }
                EntityType bukkitMob = bukkitMobType(w.getEntityId());
                w.setEntityType(MobDisguiseEntityMapping.toPacketEntityType(bukkitMob));
                ClientVersion cv = event.getUser().getClientVersion();
                w.setEntityMetadata(new ArrayList<>(
                    MobDisguiseSyntheticMetadata.fragment(cv, bukkitMob, optionsForEntityId(w.getEntityId()))
                ));
                if (bukkitMob == EntityType.ENDER_DRAGON) {
                    fixEnderDragonLivingSpawnAngles(w);
                }
                event.markForReEncode(true);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata w = new WrapperPlayServerEntityMetadata(event);
            if (rewriteIfDisguised(w.getEntityId())) {
                int eid = w.getEntityId();
                if (isOutgoingToDisguisedSubjectSelf(event, eid)) {
                    forceSelfInvisible(w);
                    event.markForReEncode(true);
                    return;
                }
                EntityType bukkitMob = bukkitMobType(eid);
                ClientVersion cv = event.getUser().getClientVersion();
                w.setEntityMetadata(new ArrayList<>(
                    MobDisguiseSyntheticMetadata.fragment(cv, bukkitMob, optionsForEntityId(eid))
                ));
                event.markForReEncode(true);
                MobDisguisePacketTrace.logServerOutbound(
                    LOG,
                    plugin,
                    event,
                    "mob disguise: ENTITY_METADATA rewrite eid=" + eid + " type=" + bukkitMob
                );
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            WrapperPlayServerEntityEquipment w = new WrapperPlayServerEntityEquipment(event);
            if (rewriteIfDisguised(w.getEntityId())) {
                int eidEq = w.getEntityId();
                if (isOutgoingToDisguisedSubjectSelf(event, eidEq)) {
                    UUID selfUuid = disguisedEntityIds.get(eidEq);
                    EnumMap<EquipmentSlot, ItemStack> selfSlots = equipmentPeByEntityUuid.computeIfAbsent(
                        selfUuid,
                        u -> new EnumMap<>(EquipmentSlot.class)
                    );
                    for (Equipment e : w.getEquipment()) {
                        ItemStack it = e.getItem();
                        selfSlots.put(e.getSlot(), it == null ? ItemStack.EMPTY : it.copy());
                    }
                    // Invisible entities still render armor/held items; strip self equipment so the
                    // disguised player sees only the decoy in third person. First-person hand is unaffected.
                    List<Equipment> empty = new ArrayList<>(EQUIPMENT_WRITE_ORDER.size());
                    for (EquipmentSlot s : EQUIPMENT_WRITE_ORDER) {
                        empty.add(new Equipment(s, ItemStack.EMPTY));
                    }
                    w.setEquipment(empty);
                    event.markForReEncode(true);
                    Integer decoyId = decoyIdBySubjectUuid.get(selfUuid);
                    Player selfPlayer = event.getPlayer();
                    if (decoyId != null && selfPlayer != null) {
                        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(
                            selfPlayer,
                            new WrapperPlayServerEntityEquipment(decoyId, toOrderedEquipmentList(selfSlots))
                        );
                    }
                    return;
                }
                UUID subjectUuid = disguisedEntityIds.get(eidEq);
                EnumMap<EquipmentSlot, ItemStack> slots = equipmentPeByEntityUuid.computeIfAbsent(
                    subjectUuid,
                    u -> new EnumMap<>(EquipmentSlot.class)
                );
                if (slots.isEmpty()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        LivingEntity le = resolveDisguisedLivingOnMain(w.getEntityId());
                        if (le != null && le.isValid()) {
                            refreshEquipmentPeFromBukkit(le);
                        }
                    });
                    return;
                } else {
                    for (Equipment e : w.getEquipment()) {
                        ItemStack it = e.getItem();
                        slots.put(e.getSlot(), it == null ? ItemStack.EMPTY : it.copy());
                    }
                    w.setEquipment(toOrderedEquipmentList(slots));
                    event.markForReEncode(true);
                }
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport w = new WrapperPlayServerEntityTeleport(event);
            if (rewriteIfDisguised(w.getEntityId()) && isEnderDragon(w.getEntityId())) {
                fixEnderDragonYawPitch(w.getYaw(), w.getPitch(), w::setYaw, w::setPitch);
                event.markForReEncode(true);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_ROTATION) {
            WrapperPlayServerEntityRotation w = new WrapperPlayServerEntityRotation(event);
            if (rewriteIfDisguised(w.getEntityId()) && isEnderDragon(w.getEntityId())) {
                fixEnderDragonYawPitch(w.getYaw(), w.getPitch(), w::setYaw, w::setPitch);
                event.markForReEncode(true);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
            WrapperPlayServerEntityHeadLook w = new WrapperPlayServerEntityHeadLook(event);
            if (rewriteIfDisguised(w.getEntityId()) && isEnderDragon(w.getEntityId())) {
                w.setHeadYaw(wrapDegrees180(w.getHeadYaw() + 180f));
                event.markForReEncode(true);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation w = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            if (rewriteIfDisguised(w.getEntityId()) && isEnderDragon(w.getEntityId())) {
                fixEnderDragonYawPitch(w.getYaw(), w.getPitch(), w::setYaw, w::setPitch);
                event.markForReEncode(true);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
            WrapperPlayServerEntityPositionSync w = new WrapperPlayServerEntityPositionSync(event);
            if (rewriteIfDisguised(w.getId()) && isEnderDragon(w.getId())) {
                EntityPositionData v = w.getValues();
                fixEnderDragonYawPitch(v.getYaw(), v.getPitch(), v::setYaw, v::setPitch);
                event.markForReEncode(true);
            }
        }
    }

    private boolean rewriteIfDisguised(int entityId) {
        UUID subjectUuid = disguisedEntityIds.get(entityId);
        if (subjectUuid == null) {
            return false;
        }
        return mobTypeByEntityUuid.get(subjectUuid) != null;
    }

    /**
     * The local client always keeps its own connection entity as a {@code Player}. Rewriting that entity to mob
     * metadata/spawn would mismatch indices; sending {@code remove_entities} for the real player id breaks the
     * connection. Skip all disguised-entity rewrites for packets addressed to the disguised subject themselves.
     */
    private boolean isOutgoingToDisguisedSubjectSelf(PacketSendEvent event, int entityId) {
        UUID subjectUuid = disguisedEntityIds.get(entityId);
        return subjectUuid != null && event.getUser().getUUID().equals(subjectUuid);
    }

    /**
     * Delivers via {@code sendPacketSilently(observer, packet)} after
     * the send pipeline, so the replacement is not re-entrant with the encoder. Falls back to {@link User} when there
     * is no Bukkit {@link Player} (e.g. non-player pipeline).
     */
    private void sendReplacementAfterPipeline(PacketSendEvent event, PacketWrapper<?> replacement) {
        event.getPostTasks().add(() -> sendSilentlyToObserver(event, replacement));
    }

    /**
     * Never pass a Bukkit {@link Player} into {@code ProtocolManager#getUser} — it expects a Netty channel.
     */
    private void sendSilentlyToObserver(PacketSendEvent event, Object packetOrBuffer) {
        if (MobDisguisePacketTrace.enabled(plugin) && packetOrBuffer instanceof PacketWrapper<?> w) {
            PacketTypeCommon syntheticType = w.getPacketTypeData().getPacketType();
            LOG.info(
                "[MobDisguise][POST-PIPELINE SEND] observer={} user={} syntheticType={}",
                MobDisguisePacketTrace.observerLabel(event),
                event.getUser().getUUID(),
                syntheticType == null ? "?" : MobDisguisePacketTrace.typeName(syntheticType)
            );
        }
        Player observer = event.getPlayer();
        User user = event.getUser();
        if (observer != null) {
            PacketEvents.getAPI().getPlayerManager().sendPacketSilently(observer, packetOrBuffer);
        } else if (packetOrBuffer instanceof PacketWrapper<?> w) {
            user.sendPacketSilently(w);
        } else {
            user.sendPacketSilently(packetOrBuffer);
        }
    }

    /**
     * Paper often wraps entity spawns in {@link PacketType.Play.Server#BUNDLE}; those inner spawns never hit a
     * top-level {@code SPAWN_*} listener. Parse the bundle on a duplicate buffer, then cancel and re-inject sub-packets
     * (raw or rewritten) in order, using immediate {@code sendPacketSilently}.
     */
    private boolean handleBundleMobDisguise(PacketSendEvent event) {
        Object buf = event.getByteBuf();
        if (ByteBufHelper.readableBytes(buf) < 1) {
            return false;
        }
        Object dup = ByteBufHelper.retainedDuplicate(buf);
        try {
            int count;
            try {
                count = ByteBufHelper.readVarInt(dup);
            } catch (IndexOutOfBoundsException e) {
                return false;
            }
            if (count < 0 || count > 4096) {
                return false;
            }
            List<Object> outPackets = new ArrayList<>(count);
            boolean changed = false;
            for (int i = 0; i < count; i++) {
                int packetBegin = ByteBufHelper.readerIndex(dup);
                if (packetBegin >= ByteBufHelper.writerIndex(dup)) {
                    return false;
                }
                Object one = ByteBufHelper.retainedDuplicate(dup);
                ByteBufHelper.readerIndex(one, packetBegin);
                final PacketSendEvent subEvent;
                try {
                    subEvent = EventCreationUtil.createSendEvent(
                        event.getChannel(),
                        event.getUser(),
                        event.getPlayer(),
                        one,
                        true
                    );
                } catch (PacketProcessException e) {
                    ByteBufHelper.release(one);
                    return false;
                }
                PacketTypeCommon subType = subEvent.getPacketType();

                if (subType == PacketType.Play.Server.SPAWN_PLAYER) {
                    WrapperPlayServerSpawnPlayer w = new WrapperPlayServerSpawnPlayer(subEvent);
                    int packetEnd = ByteBufHelper.readerIndex(one);
                    int consumed = packetEnd - packetBegin;
                    ByteBufHelper.release(one);
                    ByteBufHelper.readerIndex(dup, packetEnd);
                    if (rewriteIfDisguised(w.getEntityId())) {
                        if (isOutgoingToDisguisedSubjectSelf(event, w.getEntityId())) {
                            changed = true;
                            forceSelfInvisible(w.getEntityMetadata());
                            WrapperPlayServerEntityMetadata metaOut = new WrapperPlayServerEntityMetadata(
                                w.getEntityId(),
                                new ArrayList<>(w.getEntityMetadata())
                            );
                            metaOut.setServerVersion(event.getServerVersion());
                            metaOut.setClientVersion(event.getUser().getClientVersion());
                            outPackets.add(metaOut);
                        } else {
                            changed = true;
                            int eid = w.getEntityId();
                            EntityType bukkitMob = bukkitMobType(eid);
                            Vector3d p = w.getPosition();
                            Vector3d pos = new Vector3d(p.getX(), p.getY(), p.getZ());
                            MobDisguiseOptions disguiseOpts = optionsForEntityId(eid);
                            PacketWrapper<?> replacement = buildMobSpawnReplacement(
                                eid,
                                w.getUUID(),
                                pos,
                                w.getYaw(),
                                w.getPitch(),
                                bukkitMob,
                                disguiseOpts
                            );
                            replacement.setServerVersion(event.getServerVersion());
                            replacement.setClientVersion(event.getUser().getClientVersion());
                            outPackets.add(replacement);
                        }
                    } else {
                        ByteBufHelper.readerIndex(buf, packetBegin);
                        outPackets.add(ByteBufHelper.readSlice(buf, consumed));
                    }
                } else if (subType == PacketType.Play.Server.SPAWN_ENTITY) {
                    WrapperPlayServerSpawnEntity w = new WrapperPlayServerSpawnEntity(subEvent);
                    int packetEnd = ByteBufHelper.readerIndex(one);
                    int consumed = packetEnd - packetBegin;
                    ByteBufHelper.release(one);
                    ByteBufHelper.readerIndex(dup, packetEnd);
                    if (rewriteIfDisguised(w.getEntityId())) {
                        if (isOutgoingToDisguisedSubjectSelf(event, w.getEntityId())) {
                            ByteBufHelper.readerIndex(buf, packetBegin);
                            outPackets.add(ByteBufHelper.readSlice(buf, consumed));
                        } else {
                            changed = true;
                            int eid = w.getEntityId();
                            EntityType bukkitMob = bukkitMobType(eid);
                            UUID subjectUuid = disguisedEntityIds.get(eid);
                            UUID uuid = w.getUUID().orElse(subjectUuid);
                            Vector3d p = w.getPosition();
                            Vector3d pos = new Vector3d(p.getX(), p.getY(), p.getZ());
                            MobDisguiseOptions disguiseOpts = optionsForEntityId(eid);
                            PacketWrapper<?> replacement = buildMobSpawnReplacement(
                                eid,
                                uuid,
                                pos,
                                w.getYaw(),
                                w.getPitch(),
                                bukkitMob,
                                disguiseOpts
                            );
                            replacement.setServerVersion(event.getServerVersion());
                            replacement.setClientVersion(event.getUser().getClientVersion());
                            outPackets.add(replacement);
                        }
                    } else {
                        ByteBufHelper.readerIndex(buf, packetBegin);
                        outPackets.add(ByteBufHelper.readSlice(buf, consumed));
                    }
                } else if (subType == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
                    WrapperPlayServerSpawnLivingEntity w = new WrapperPlayServerSpawnLivingEntity(subEvent);
                    int packetEnd = ByteBufHelper.readerIndex(one);
                    int consumed = packetEnd - packetBegin;
                    ByteBufHelper.release(one);
                    ByteBufHelper.readerIndex(dup, packetEnd);
                    if (rewriteIfDisguised(w.getEntityId())) {
                        if (isOutgoingToDisguisedSubjectSelf(event, w.getEntityId())) {
                            ByteBufHelper.readerIndex(buf, packetBegin);
                            outPackets.add(ByteBufHelper.readSlice(buf, consumed));
                        } else {
                            changed = true;
                            EntityType bukkitMob = bukkitMobType(w.getEntityId());
                            w.setEntityType(MobDisguiseEntityMapping.toPacketEntityType(bukkitMob));
                            ClientVersion cv = event.getUser().getClientVersion();
                            w.setEntityMetadata(new ArrayList<>(
                                MobDisguiseSyntheticMetadata.fragment(cv, bukkitMob, optionsForEntityId(w.getEntityId()))
                            ));
                            if (bukkitMob == EntityType.ENDER_DRAGON) {
                                fixEnderDragonLivingSpawnAngles(w);
                            }
                            WrapperPlayServerSpawnLivingEntity copy = new WrapperPlayServerSpawnLivingEntity(
                                w.getEntityId(),
                                w.getEntityUUID(),
                                w.getEntityType(),
                                w.getPosition(),
                                w.getYaw(),
                                w.getPitch(),
                                w.getHeadPitch(),
                                w.getVelocity(),
                                new ArrayList<>(w.getEntityMetadata())
                            );
                            copy.setServerVersion(event.getServerVersion());
                            copy.setClientVersion(event.getUser().getClientVersion());
                            outPackets.add(copy);
                        }
                    } else {
                        ByteBufHelper.readerIndex(buf, packetBegin);
                        outPackets.add(ByteBufHelper.readSlice(buf, consumed));
                    }
                } else if (subType == PacketType.Play.Server.ENTITY_METADATA) {
                    WrapperPlayServerEntityMetadata w = new WrapperPlayServerEntityMetadata(subEvent);
                    int packetEnd = ByteBufHelper.readerIndex(one);
                    int consumed = packetEnd - packetBegin;
                    ByteBufHelper.release(one);
                    ByteBufHelper.readerIndex(dup, packetEnd);
                    if (rewriteIfDisguised(w.getEntityId())) {
                        if (isOutgoingToDisguisedSubjectSelf(event, w.getEntityId())) {
                            changed = true;
                            forceSelfInvisible(w);
                            WrapperPlayServerEntityMetadata selfMeta = new WrapperPlayServerEntityMetadata(
                                w.getEntityId(),
                                new ArrayList<>(w.getEntityMetadata())
                            );
                            selfMeta.setServerVersion(event.getServerVersion());
                            selfMeta.setClientVersion(event.getUser().getClientVersion());
                            outPackets.add(selfMeta);
                        } else {
                            changed = true;
                            int eidMeta = w.getEntityId();
                            EntityType bukkitMobMeta = bukkitMobType(eidMeta);
                            ClientVersion cvMeta = event.getUser().getClientVersion();
                            WrapperPlayServerEntityMetadata metaOut = new WrapperPlayServerEntityMetadata(
                                eidMeta,
                                new ArrayList<>(MobDisguiseSyntheticMetadata.fragment(
                                    cvMeta,
                                    bukkitMobMeta,
                                    optionsForEntityId(eidMeta)
                                ))
                            );
                            metaOut.setServerVersion(event.getServerVersion());
                            metaOut.setClientVersion(cvMeta);
                            outPackets.add(metaOut);
                        }
                    } else {
                        ByteBufHelper.readerIndex(buf, packetBegin);
                        outPackets.add(ByteBufHelper.readSlice(buf, consumed));
                    }
                } else if (subType == PacketType.Play.Server.ENTITY_EQUIPMENT) {
                    WrapperPlayServerEntityEquipment w = new WrapperPlayServerEntityEquipment(subEvent);
                    int packetEnd = ByteBufHelper.readerIndex(one);
                    int consumed = packetEnd - packetBegin;
                    ByteBufHelper.release(one);
                    ByteBufHelper.readerIndex(dup, packetEnd);
                    int eidEq = w.getEntityId();
                    if (rewriteIfDisguised(eidEq) && isOutgoingToDisguisedSubjectSelf(event, eidEq)) {
                        changed = true;
                        UUID selfUuid = disguisedEntityIds.get(eidEq);
                        EnumMap<EquipmentSlot, ItemStack> selfSlots = equipmentPeByEntityUuid.computeIfAbsent(
                            selfUuid,
                            u -> new EnumMap<>(EquipmentSlot.class)
                        );
                        for (Equipment e : w.getEquipment()) {
                            ItemStack it = e.getItem();
                            selfSlots.put(e.getSlot(), it == null ? ItemStack.EMPTY : it.copy());
                        }
                        // Invisible entities still render armor/held items; strip self equipment so the
                        // disguised player sees only the decoy in third person.
                        List<Equipment> empty = new ArrayList<>(EQUIPMENT_WRITE_ORDER.size());
                        for (EquipmentSlot s : EQUIPMENT_WRITE_ORDER) {
                            empty.add(new Equipment(s, ItemStack.EMPTY));
                        }
                        WrapperPlayServerEntityEquipment eqOut = new WrapperPlayServerEntityEquipment(eidEq, empty);
                        eqOut.setServerVersion(event.getServerVersion());
                        eqOut.setClientVersion(event.getUser().getClientVersion());
                        outPackets.add(eqOut);
                        Integer decoyId = decoyIdBySubjectUuid.get(selfUuid);
                        Player selfPlayer = event.getPlayer();
                        if (decoyId != null && selfPlayer != null) {
                            WrapperPlayServerEntityEquipment decoyEq = new WrapperPlayServerEntityEquipment(
                                decoyId,
                                toOrderedEquipmentList(selfSlots)
                            );
                            decoyEq.setServerVersion(event.getServerVersion());
                            decoyEq.setClientVersion(event.getUser().getClientVersion());
                            outPackets.add(decoyEq);
                        }
                    } else if (rewriteIfDisguised(eidEq)) {
                        UUID subjectUuidEq = disguisedEntityIds.get(eidEq);
                        EnumMap<EquipmentSlot, ItemStack> slotsEq = equipmentPeByEntityUuid.get(subjectUuidEq);
                        if (slotsEq == null || slotsEq.isEmpty()) {
                            MobDisguisePacketTrace.logServerOutbound(
                                LOG,
                                plugin,
                                event,
                                "bundle ENTITY_EQUIPMENT eid=" + eidEq + " passthrough (equipment cache empty)"
                            );
                            ByteBufHelper.readerIndex(buf, packetBegin);
                            outPackets.add(ByteBufHelper.readSlice(buf, consumed));
                        } else {
                            changed = true;
                            for (Equipment e : w.getEquipment()) {
                                ItemStack it = e.getItem();
                                slotsEq.put(e.getSlot(), it == null ? ItemStack.EMPTY : it.copy());
                            }
                            WrapperPlayServerEntityEquipment eqOut = new WrapperPlayServerEntityEquipment(
                                eidEq,
                                toOrderedEquipmentList(slotsEq)
                            );
                            eqOut.setServerVersion(event.getServerVersion());
                            eqOut.setClientVersion(event.getUser().getClientVersion());
                            outPackets.add(eqOut);
                        }
                    } else {
                        ByteBufHelper.readerIndex(buf, packetBegin);
                        outPackets.add(ByteBufHelper.readSlice(buf, consumed));
                    }
                } else {
                    Class<? extends PacketWrapper<?>> clazz = subType.getWrapperClass();
                    if (clazz == null) {
                        ByteBufHelper.release(one);
                        return false;
                    }
                    try {
                        clazz.getConstructor(PacketSendEvent.class).newInstance(subEvent);
                    } catch (ReflectiveOperationException e) {
                        ByteBufHelper.release(one);
                        return false;
                    }
                    int packetEnd = ByteBufHelper.readerIndex(one);
                    int consumed = packetEnd - packetBegin;
                    ByteBufHelper.release(one);
                    ByteBufHelper.readerIndex(dup, packetEnd);
                    ByteBufHelper.readerIndex(buf, packetBegin);
                    outPackets.add(ByteBufHelper.readSlice(buf, consumed));
                }
            }
            if (!changed) {
                return false;
            }
            event.setCancelled(true);
            MobDisguisePacketTrace.logServerOutbound(
                LOG,
                plugin,
                event,
                "bundle cancelled; resending " + outPackets.size() + " sub-packets (spawn/meta/equipment rewritten for mob disguise)"
            );
            event.getPostTasks().add(() -> {
                for (Object part : outPackets) {
                    sendSilentlyToObserver(event, part);
                }
            });
            return true;
        } catch (IndexOutOfBoundsException e) {
            return false;
        } finally {
            ByteBufHelper.release(dup);
        }
    }

    private boolean isEnderDragon(int entityId) {
        UUID sid = disguisedEntityIds.get(entityId);
        return sid != null && mobTypeByEntityUuid.get(sid) == EntityType.ENDER_DRAGON;
    }

    private LivingEntity resolveDisguisedLivingOnMain(int entityId) {
        if (!plugin.getServer().isPrimaryThread()) {
            return null;
        }
        UUID subjectUuid = disguisedEntityIds.get(entityId);
        if (subjectUuid == null) {
            return null;
        }
        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
            Entity e = w.getEntity(subjectUuid);
            if (e instanceof LivingEntity le) {
                return le;
            }
        }
        return null;
    }

    private void refreshEquipmentPeFromBukkit(LivingEntity subject) {
        if (!subject.isValid()) {
            return;
        }
        if (!mobTypeByEntityUuid.containsKey(subject.getUniqueId())) {
            return;
        }
        EntityEquipment be = subject.getEquipment();
        EnumMap<EquipmentSlot, ItemStack> m = new EnumMap<>(EquipmentSlot.class);
        if (be == null) {
            for (EquipmentSlot s : EQUIPMENT_WRITE_ORDER) {
                m.put(s, ItemStack.EMPTY);
            }
        } else {
            m.put(EquipmentSlot.MAIN_HAND, peItem(mainHandBukkitStack(subject, be)));
            m.put(EquipmentSlot.OFF_HAND, peItem(be.getItemInOffHand()));
            m.put(EquipmentSlot.BOOTS, peItem(be.getBoots()));
            m.put(EquipmentSlot.LEGGINGS, peItem(be.getLeggings()));
            m.put(EquipmentSlot.CHEST_PLATE, peItem(be.getChestplate()));
            m.put(EquipmentSlot.HELMET, peItem(be.getHelmet()));
        }
        equipmentPeByEntityUuid.put(subject.getUniqueId(), m);
    }

    /**
     * Hotbar selection for players must use inventory held slot; {@link EntityEquipment#getItemInMainHand()} can lag
     * behind the selected stack for a tick.
     */
    private static org.bukkit.inventory.ItemStack mainHandBukkitStack(LivingEntity le, EntityEquipment be) {
        if (le instanceof Player p) {
            return p.getInventory().getItem(p.getInventory().getHeldItemSlot());
        }
        return be.getItemInMainHand();
    }

    private static List<Equipment> toOrderedEquipmentList(EnumMap<EquipmentSlot, ItemStack> slots) {
        List<Equipment> out = new ArrayList<>(EQUIPMENT_WRITE_ORDER.size());
        for (EquipmentSlot s : EQUIPMENT_WRITE_ORDER) {
            ItemStack it = slots.get(s);
            out.add(new Equipment(s, it == null ? ItemStack.EMPTY : it));
        }
        return out;
    }

    /**
     * Modern protocol (1.19+) uses a single clientbound add-entity packet for all types; the old
     * {@code SPAWN_LIVING_ENTITY} packet is not used for new clients. Sending {@link WrapperPlayServerSpawnLivingEntity}
     * replacements can be ignored, leaving the client with a {@code Player} entity tracker so metadata rewrites then
     * mismatch (e.g. index 16 byte vs boolean) and viewers see nothing.
     */
    private static PacketWrapper<?> buildMobSpawnReplacement(
        int entityId,
        UUID uuid,
        Vector3d position,
        float yaw,
        float pitch,
        EntityType bukkitMob,
        MobDisguiseOptions options
    ) {
        MobDisguiseOptions opts = options == null ? MobDisguiseOptions.DEFAULT : options;
        com.github.retrooper.packetevents.protocol.entity.type.EntityType peType =
            MobDisguiseEntityMapping.toPacketEntityType(bukkitMob);
        int spawnObjectData = spawnObjectDataField(bukkitMob, opts);
        WrapperPlayServerSpawnEntity se = new WrapperPlayServerSpawnEntity(
            entityId,
            Optional.of(uuid),
            peType,
            position,
            pitch,
            yaw,
            yaw,
            spawnObjectData,
            Optional.of(Vector3d.zero())
        );
        if (bukkitMob == EntityType.ENDER_DRAGON) {
            fixEnderDragonSpawnAngles(se);
        }
        return se;
    }

    private static int spawnObjectDataField(EntityType bukkitMob, MobDisguiseOptions o) {
        if (bukkitMob == EntityType.SLIME || bukkitMob == EntityType.MAGMA_CUBE) {
            return o.effectiveSlimeOrMagmaSize();
        }
        return 0;
    }

    private static ItemStack peItem(org.bukkit.inventory.ItemStack bukkit) {
        if (bukkit == null || bukkit.getType().isAir()) {
            return ItemStack.EMPTY;
        }
        try {
            return SpigotConversionUtil.fromBukkitItemStack(bukkit);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void fixEnderDragonSpawnAngles(WrapperPlayServerSpawnEntity w) {
        fixEnderDragonYawPitch(w.getYaw(), w.getPitch(), w::setYaw, w::setPitch);
        w.setHeadYaw(wrapDegrees180(w.getHeadYaw() + 180f));
    }

    private static void fixEnderDragonLivingSpawnAngles(WrapperPlayServerSpawnLivingEntity w) {
        fixEnderDragonYawPitch(w.getYaw(), w.getPitch(), w::setYaw, w::setPitch);
        w.setHeadPitch(wrapDegrees180(w.getHeadPitch() + 180f));
    }

    private static void fixEnderDragonYawPitch(float yaw, float pitch, java.util.function.Consumer<Float> setYaw, java.util.function.Consumer<Float> setPitch) {
        setYaw.accept(wrapDegrees180(yaw + 180f));
        setPitch.accept(clampPitch(-pitch));
    }

    private static float wrapDegrees180(float deg) {
        float r = deg % 360f;
        if (r > 180f) {
            r -= 360f;
        }
        if (r < -180f) {
            r += 360f;
        }
        return r;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90f, Math.min(90f, pitch));
    }

    private EntityType bukkitMobType(int entityId) {
        UUID subjectUuid = disguisedEntityIds.get(entityId);
        EntityType bukkit = mobTypeByEntityUuid.get(subjectUuid);
        if (bukkit == null) {
            throw new IllegalStateException("No bukkit mob type for entity " + entityId);
        }
        return bukkit;
    }

    private MobDisguiseOptions optionsForEntityId(int entityId) {
        UUID subjectUuid = disguisedEntityIds.get(entityId);
        if (subjectUuid == null) {
            return MobDisguiseOptions.DEFAULT;
        }
        return mobOptionsByEntityUuid.getOrDefault(subjectUuid, MobDisguiseOptions.DEFAULT);
    }

    private void syncEntityId(LivingEntity entity) {
        UUID sid = entity.getUniqueId();
        if (!mobTypeByEntityUuid.containsKey(sid)) {
            return;
        }
        disguisedEntityIds.entrySet().removeIf(e -> e.getValue().equals(sid));
        disguisedEntityIds.put(entity.getEntityId(), sid);
    }

    private void refreshEntityTrackers(LivingEntity subject) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!subject.isValid()) {
                return;
            }
            refreshEquipmentPeFromBukkit(subject);
            int eid = subject.getEntityId();
            for (Player viewer : subject.getWorld().getPlayers()) {
                if (viewer.getUniqueId().equals(subject.getUniqueId())) {
                    continue;
                }
                PacketEvents.getAPI().getPlayerManager().sendPacketSilently(viewer, new WrapperPlayServerDestroyEntities(eid));
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> resendMobDisguiseToOtherViewers(subject), 2L);
        }, 1L);
    }

    /**
     * Destroys the client entity, waits, then forces a full
     * resync so rewritten spawn/metadata packets are observed (hide/show retriggers tracker pipeline on Paper).
     * Additionally sends the mob spawn replacement, metadata and equipment explicitly, because relying on the
     * re-track pipeline alone can leave viewers with no entity at all (player/entity vanishes completely).
     */
    private void resendMobDisguiseToOtherViewers(LivingEntity subject) {
        if (!subject.isValid()) {
            return;
        }
        UUID sid = subject.getUniqueId();
        EntityType bukkitMob = mobTypeByEntityUuid.get(sid);
        if (bukkitMob == null) {
            return;
        }
        MobDisguiseOptions opts = mobOptionsByEntityUuid.getOrDefault(sid, MobDisguiseOptions.DEFAULT);
        int eid = subject.getEntityId();
        for (Player viewer : subject.getWorld().getPlayers()) {
            if (viewer.getUniqueId().equals(subject.getUniqueId())) {
                continue;
            }
            sendExplicitMobDisguiseSpawn(viewer, subject, eid, bukkitMob, opts);
        }
    }

    /**
     * Deterministic disguise delivery: destroy the real client-side entity, then send the mob spawn, metadata and
     * equipment packets directly. Guarantees viewers see the disguise even if the tracker respawn is lost or
     * rewritten inconsistently (fixes players/entities vanishing for all observers). No hide/show: the tracker
     * keeps streaming movement/metadata for the real entity id, which the packet listener rewrites to the mob.
     */
    private void sendExplicitMobDisguiseSpawn(
        Player viewer,
        LivingEntity subject,
        int eid,
        EntityType bukkitMob,
        MobDisguiseOptions opts
    ) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(viewer);
        if (user == null) {
            return;
        }
        Vector3d pos = new Vector3d(subject.getX(), subject.getY(), subject.getZ());
        PacketWrapper<?> spawn = buildMobSpawnReplacement(
            eid,
            subject.getUniqueId(),
            pos,
            subject.getYaw(),
            subject.getPitch(),
            bukkitMob,
            opts
        );
        spawn.setServerVersion(PacketEvents.getAPI().getServerManager().getVersion());
        spawn.setClientVersion(user.getClientVersion());

        ClientVersion cv = user.getClientVersion();
        WrapperPlayServerEntityMetadata metadata = new WrapperPlayServerEntityMetadata(
            eid,
            new ArrayList<>(MobDisguiseSyntheticMetadata.fragment(cv, bukkitMob, opts))
        );

        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(viewer, new WrapperPlayServerDestroyEntities(eid));
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(viewer, spawn);
        PacketEvents.getAPI().getPlayerManager().sendPacketSilently(viewer, metadata);

        EnumMap<EquipmentSlot, ItemStack> slots = equipmentPeByEntityUuid.get(subject.getUniqueId());
        if (slots != null && !slots.isEmpty()) {
            WrapperPlayServerEntityEquipment equipment = new WrapperPlayServerEntityEquipment(
                eid,
                toOrderedEquipmentList(slots)
            );
            PacketEvents.getAPI().getPlayerManager().sendPacketSilently(viewer, equipment);
        }
    }

    /**
     * Keep tab list and profile strip text as real usernames while the entity model is rewritten to a mob,
     * matching expected behaviour when {@code PaperPlayerAppearance} no longer pushes mob labels into the list name.
     * For player disguises, spoof the profile/display name so viewers see the disguise name in tab and overhead.
     */
    private boolean rewritePlayerInfoUpdateForMobSubjects(WrapperPlayServerPlayerInfoUpdate packet, UUID viewerUuid) {
        boolean modified = false;
        for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo entry : packet.getEntries()) {
            UUID id = entry.getProfileId();
            UserProfile profile = entry.getGameProfile();
            if (id == null && profile != null) {
                id = profile.getUUID();
            }
            if (id == null) {
                continue;
            }
            if (mobTypeByEntityUuid.containsKey(id)) {
                Player subject = plugin.getServer().getPlayer(id);
                if (subject == null || !subject.isOnline()) {
                    continue;
                }
                String realName = subject.getName();
                entry.setDisplayName(Component.text(realName));
                if (profile != null) {
                    profile.setName(realName);
                }
                modified = true;
            } else if (viewerUuid != null && !viewerUuid.equals(id)) {
                String spoofed = spoofedNameByPlayerUuid.get(id);
                if (spoofed == null) {
                    continue;
                }
                entry.setDisplayName(Component.text(spoofed));
                if (profile != null) {
                    profile.setName(spoofed.length() > 16 ? spoofed.substring(0, 16) : spoofed);
                }
                modified = true;
            }
        }
        return modified;
    }

    private boolean rewriteLegacyPlayerInfoForMobSubjects(WrapperPlayServerPlayerInfo packet, UUID viewerUuid) {
        boolean modified = false;
        for (WrapperPlayServerPlayerInfo.PlayerData data : packet.getPlayerDataList()) {
            UserProfile profile = data.getUserProfile();
            if (profile == null) {
                profile = data.getUser();
            }
            if (profile == null) {
                continue;
            }
            UUID id = profile.getUUID();
            if (mobTypeByEntityUuid.containsKey(id)) {
                Player subject = plugin.getServer().getPlayer(id);
                if (subject == null || !subject.isOnline()) {
                    continue;
                }
                String realName = subject.getName();
                profile.setName(realName);
                data.setDisplayName(Component.text(realName));
                modified = true;
            } else if (viewerUuid != null && !viewerUuid.equals(id)) {
                String spoofed = spoofedNameByPlayerUuid.get(id);
                if (spoofed == null) {
                    continue;
                }
                profile.setName(spoofed.length() > 16 ? spoofed.substring(0, 16) : spoofed);
                data.setDisplayName(Component.text(spoofed));
                modified = true;
            }
        }
        return modified;
    }

}
