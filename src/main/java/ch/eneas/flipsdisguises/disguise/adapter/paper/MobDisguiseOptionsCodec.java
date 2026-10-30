package ch.eneas.flipsdisguises.disguise.adapter.paper;

import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import com.google.gson.JsonObject;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * JSON form of {@link MobDisguiseOptions}. Walks the option fields reflectively, so a new option is persisted
 * without touching this class as long as it is a boolean, int, component, enum or registry-keyed type.
 */
final class MobDisguiseOptionsCodec {

    private static final Logger LOG = LoggerFactory.getLogger(MobDisguiseOptionsCodec.class);

    private static final Map<Class<?>, RegistryKey<?>> REGISTRY_KEYS = Map.of(
        Villager.Profession.class, RegistryKey.VILLAGER_PROFESSION,
        Villager.Type.class, RegistryKey.VILLAGER_TYPE,
        Cow.Variant.class, RegistryKey.COW_VARIANT,
        Cat.Type.class, RegistryKey.CAT_VARIANT,
        Frog.Variant.class, RegistryKey.FROG_VARIANT,
        Pig.Variant.class, RegistryKey.PIG_VARIANT,
        Chicken.Variant.class, RegistryKey.CHICKEN_VARIANT,
        Wolf.Variant.class, RegistryKey.WOLF_VARIANT
    );

    private MobDisguiseOptionsCodec() {
    }

    static JsonObject toJson(MobDisguiseOptions options) {
        JsonObject out = new JsonObject();
        for (Field f : optionFields()) {
            try {
                Object v = f.get(options);
                if (v == null) {
                    continue;
                }
                if (v instanceof Boolean b) {
                    out.addProperty(f.getName(), b);
                } else if (v instanceof Integer i) {
                    out.addProperty(f.getName(), i);
                } else if (v instanceof Component c) {
                    out.addProperty(f.getName(), GsonComponentSerializer.gson().serialize(c));
                } else if (v instanceof Enum<?> e) {
                    out.addProperty(f.getName(), e.name());
                } else if (v instanceof Keyed k) {
                    out.addProperty(f.getName(), k.key().asString());
                } else {
                    LOG.warn("Not persisting disguise option '{}': unsupported type {}", f.getName(), v.getClass().getName());
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                LOG.warn("Not persisting disguise option '{}'", f.getName(), ex);
            }
        }
        return out;
    }

    static MobDisguiseOptions fromJson(JsonObject json) {
        MobDisguiseOptions options = new MobDisguiseOptions();
        for (Field f : optionFields()) {
            if (!json.has(f.getName())) {
                continue;
            }
            try {
                Object v = decode(f.getType(), json.get(f.getName()));
                if (v != null) {
                    f.set(options, v);
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                LOG.warn("Ignoring stored disguise option '{}'", f.getName(), ex);
            }
        }
        return options;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object decode(Class<?> type, com.google.gson.JsonElement element) {
        if (type == Boolean.class) {
            return element.getAsBoolean();
        }
        if (type == Integer.class) {
            return element.getAsInt();
        }
        if (type == Component.class) {
            return GsonComponentSerializer.gson().deserialize(element.getAsString());
        }
        if (type.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) type, element.getAsString());
        }
        RegistryKey<?> registryKey = REGISTRY_KEYS.get(type);
        if (registryKey != null) {
            Registry<?> registry = RegistryAccess.registryAccess().getRegistry((RegistryKey) registryKey);
            NamespacedKey key = NamespacedKey.fromString(element.getAsString());
            return key == null ? null : registry.get(key);
        }
        return null;
    }

    private static Field[] optionFields() {
        return java.util.Arrays.stream(MobDisguiseOptions.class.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
            .peek(f -> f.setAccessible(true))
            .toArray(Field[]::new);
    }
}
