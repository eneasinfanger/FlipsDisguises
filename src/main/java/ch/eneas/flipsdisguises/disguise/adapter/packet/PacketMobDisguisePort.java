package ch.eneas.flipsdisguises.disguise.adapter.packet;

import ch.eneas.Main;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.port.MobDisguisePort;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
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

@Injectable
public final class PacketMobDisguisePort implements MobDisguisePort, PacketListener, Listener {

    private final Main plugin;
    private final Map<UUID, EntityType> mobTypeByEntityUuid = new ConcurrentHashMap<>();
    private final Map<UUID, MobDisguiseOptions> mobOptionsByEntityUuid = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> disguisedEntityIds = new ConcurrentHashMap<>();
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
    }

    @Override
    public void detach(LivingEntity subject) {
        UUID sid = subject.getUniqueId();
        mobTypeByEntityUuid.remove(sid);
        mobOptionsByEntityUuid.remove(sid);
        equipmentPeByEntityUuid.remove(sid);
        disguisedEntityIds.entrySet().removeIf(e -> e.getValue().equals(sid));
        refreshEntityTrackers(subject);
    }

    @Override
    public void detachDecoyOnly(LivingEntity subject) {
        UUID sid = subject.getUniqueId();
        mobTypeByEntityUuid.remove(sid);
        mobOptionsByEntityUuid.remove(sid);
        equipmentPeByEntityUuid.remove(sid);
        disguisedEntityIds.entrySet().removeIf(e -> e.getValue().equals(sid));
    }

    @Override
    public void detachDisconnected(UUID entityUuid) {
        mobTypeByEntityUuid.remove(entityUuid);
        mobOptionsByEntityUuid.remove(entityUuid);
        equipmentPeByEntityUuid.remove(entityUuid);
        disguisedEntityIds.entrySet().removeIf(e -> e.getValue().equals(entityUuid));
    }

    @Override
    public void syncNewObserver(Player observer) {
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player p = event.getPlayer();
            if (mobTypeByEntityUuid.containsKey(p.getUniqueId())) {
                syncEntityId(p);
                refreshEquipmentPeFromBukkit(p);
                refreshEntityTrackers(p);
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
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_PLAYER) {
            WrapperPlayServerSpawnPlayer w = new WrapperPlayServerSpawnPlayer(event);
            if (rewriteIfDisguised(w.getEntityId())) {
                int eid = w.getEntityId();
                EntityType bukkitMob = bukkitMobType(eid);
                UUID uuid = w.getUUID();
                Vector3d p = w.getPosition();
                Vector3d pos = new Vector3d(p.getX(), p.getY(), p.getZ());
                float yaw = w.getYaw();
                float pitch = w.getPitch();
                User user = event.getUser();
                ServerVersion serverVersion = event.getServerVersion();
                ClientVersion clientVersion = user.getClientVersion();
                MobDisguiseOptions disguiseOpts = optionsForEntityId(eid);
                event.setCancelled(true);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    PacketWrapper<?> replacement = buildMobSpawnReplacement(
                        eid,
                        uuid,
                        pos,
                        yaw,
                        pitch,
                        bukkitMob,
                        disguiseOpts,
                        clientVersion
                    );
                    replacement.setServerVersion(serverVersion);
                    replacement.setClientVersion(clientVersion);
                    replacement.prepareForSend(user, true);
                    user.sendPacket(replacement);
                });
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity w = new WrapperPlayServerSpawnEntity(event);
            if (rewriteIfDisguised(w.getEntityId())) {
                EntityType bukkitMob = bukkitMobType(w.getEntityId());
                w.setEntityType(MobDisguiseEntityMapping.toPacketEntityType(bukkitMob));
                if (bukkitMob == EntityType.SLIME || bukkitMob == EntityType.MAGMA_CUBE) {
                    w.setData(slimeOrMagmaSpawnData(bukkitMob, optionsForEntityId(w.getEntityId())));
                }
                if (bukkitMob == EntityType.ENDER_DRAGON) {
                    fixEnderDragonSpawnAngles(w);
                }
                event.markForReEncode(true);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            WrapperPlayServerSpawnLivingEntity w = new WrapperPlayServerSpawnLivingEntity(event);
            if (rewriteIfDisguised(w.getEntityId())) {
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
                EntityType bukkitMob = bukkitMobType(eid);
                ClientVersion cv = event.getUser().getClientVersion();
                w.setEntityMetadata(new ArrayList<>(
                    MobDisguiseSyntheticMetadata.fragment(cv, bukkitMob, optionsForEntityId(eid))
                ));
                event.markForReEncode(true);
            }
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            WrapperPlayServerEntityEquipment w = new WrapperPlayServerEntityEquipment(event);
            if (rewriteIfDisguised(w.getEntityId())) {
                UUID subjectUuid = disguisedEntityIds.get(w.getEntityId());
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
                    event.setCancelled(true);
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

    private static PacketWrapper<?> buildMobSpawnReplacement(
        int entityId,
        UUID uuid,
        Vector3d position,
        float yaw,
        float pitch,
        EntityType bukkitMob,
        MobDisguiseOptions options,
        ClientVersion clientVersion
    ) {
        MobDisguiseOptions opts = options == null ? MobDisguiseOptions.DEFAULT : options;
        com.github.retrooper.packetevents.protocol.entity.type.EntityType peType =
            MobDisguiseEntityMapping.toPacketEntityType(bukkitMob);
        if (usesSpawnEntityPacket(bukkitMob)) {
            WrapperPlayServerSpawnEntity se = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(uuid),
                peType,
                position,
                pitch,
                yaw,
                yaw,
                0,
                Optional.of(Vector3d.zero())
            );
            if (bukkitMob == EntityType.SLIME || bukkitMob == EntityType.MAGMA_CUBE) {
                se.setData(slimeOrMagmaSpawnData(bukkitMob, opts));
            }
            if (bukkitMob == EntityType.ENDER_DRAGON) {
                fixEnderDragonSpawnAngles(se);
            }
            return se;
        }
        WrapperPlayServerSpawnLivingEntity sle = new WrapperPlayServerSpawnLivingEntity(
            entityId,
            uuid,
            peType,
            position,
            yaw,
            pitch,
            yaw,
            Vector3d.zero(),
            new ArrayList<>(MobDisguiseSyntheticMetadata.fragment(clientVersion, bukkitMob, opts))
        );
        if (bukkitMob == EntityType.ENDER_DRAGON) {
            fixEnderDragonLivingSpawnAngles(sle);
        }
        return sle;
    }

    private static int slimeOrMagmaSpawnData(EntityType bukkitMob, MobDisguiseOptions o) {
        if (bukkitMob != EntityType.SLIME && bukkitMob != EntityType.MAGMA_CUBE) {
            return 0;
        }
        return o.slimeOrMagmaSize() > 0 ? o.effectiveSlimeOrMagmaSize() : 1;
    }

    private static boolean usesSpawnEntityPacket(EntityType bukkit) {
        return bukkit == EntityType.ENDER_DRAGON || bukkit == EntityType.SLIME || bukkit == EntityType.MAGMA_CUBE;
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
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!subject.isValid()) {
                return;
            }
            refreshEquipmentPeFromBukkit(subject);
            for (Player viewer : subject.getWorld().getPlayers()) {
                if (viewer.getUniqueId().equals(subject.getUniqueId())) {
                    continue;
                }
                viewer.hideEntity(plugin, subject);
                viewer.showEntity(plugin, subject);
            }
        });
    }

}
