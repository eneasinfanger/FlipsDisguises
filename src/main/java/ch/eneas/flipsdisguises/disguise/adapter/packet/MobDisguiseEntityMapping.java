package ch.eneas.flipsdisguises.disguise.adapter.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import org.bukkit.entity.EntityType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MobDisguiseEntityMapping {

    private static final EnumSet<EntityType> BLOCKED = EnumSet.of(
        EntityType.PLAYER,
        EntityType.UNKNOWN,
        EntityType.BLOCK_DISPLAY,
        EntityType.ITEM_DISPLAY,
        EntityType.TEXT_DISPLAY,
        EntityType.INTERACTION,
        EntityType.MARKER
    );

    private static volatile Map<EntityType, com.github.retrooper.packetevents.protocol.entity.type.EntityType> bukkitToPe;
    private static volatile String[] sortedNames;

    private MobDisguiseEntityMapping() {
    }

    private static void ensureBuilt() {
        if (bukkitToPe != null) {
            return;
        }
        synchronized (MobDisguiseEntityMapping.class) {
            if (bukkitToPe != null) {
                return;
            }
            if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isInitialized()) {
                throw new IllegalStateException("PacketEvents must be initialized before mob disguise mapping is built");
            }
            Map<String, com.github.retrooper.packetevents.protocol.entity.type.EntityType> peByFieldName = new HashMap<>();
            for (Field f : EntityTypes.class.getFields()) {
                if (!Modifier.isStatic(f.getModifiers())
                    || f.getType() != com.github.retrooper.packetevents.protocol.entity.type.EntityType.class) {
                    continue;
                }
                String n = f.getName();
                if (!n.equals(n.toUpperCase()) || n.startsWith("$")) {
                    continue;
                }
                try {
                    peByFieldName.put(n, (com.github.retrooper.packetevents.protocol.entity.type.EntityType) f.get(null));
                } catch (ReflectiveOperationException e) {
                    throw new ExceptionInInitializerError(e);
                }
            }

            EnumMap<EntityType, com.github.retrooper.packetevents.protocol.entity.type.EntityType> map = new EnumMap<>(EntityType.class);
            for (EntityType bukkit : EntityType.values()) {
                if (BLOCKED.contains(bukkit) || !bukkit.isAlive()) {
                    continue;
                }
                com.github.retrooper.packetevents.protocol.entity.type.EntityType pe = peByFieldName.get(bukkit.name());
                if (pe != null) {
                    map.put(bukkit, pe);
                }
            }
            bukkitToPe = Collections.unmodifiableMap(map);

            List<String> names = new ArrayList<>(bukkitToPe.keySet().size());
            for (EntityType t : bukkitToPe.keySet()) {
                names.add(t.name());
            }
            Collections.sort(names);
            sortedNames = names.toArray(new String[0]);
        }
    }

    public static boolean isSupported(EntityType bukkit) {
        ensureBuilt();
        return bukkit != null && bukkitToPe.containsKey(bukkit);
    }

    public static com.github.retrooper.packetevents.protocol.entity.type.EntityType toPacketEntityType(EntityType bukkit) {
        ensureBuilt();
        com.github.retrooper.packetevents.protocol.entity.type.EntityType pe = bukkitToPe.get(Objects.requireNonNull(bukkit, "bukkit"));
        if (pe == null) {
            throw new IllegalArgumentException("No PacketEvents entity mapping for " + bukkit);
        }
        return pe;
    }

    public static String[] sortedSuggestionNames() {
        ensureBuilt();
        return sortedNames.clone();
    }

    public static int supportedCount() {
        ensureBuilt();
        return bukkitToPe.size();
    }
}
