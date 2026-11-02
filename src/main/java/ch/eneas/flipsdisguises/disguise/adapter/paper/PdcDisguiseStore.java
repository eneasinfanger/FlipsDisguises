package ch.eneas.flipsdisguises.disguise.adapter.paper;

import ch.eneas.PluginMain;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.domain.PlayerLookBackup;
import ch.eneas.flipsdisguises.disguise.domain.SkinProperty;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import ch.eneas.flipsdisguises.disguise.domain.StoredDisguise;
import ch.eneas.flipsdisguises.disguise.port.DisguiseStore;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the disguise on the disguised entity's own persistent data container, so it is saved with the player
 * data or the entity's chunk and needs no extra file or lifecycle.
 */
@Injectable
public final class PdcDisguiseStore implements DisguiseStore {

    private static final Logger LOG = LoggerFactory.getLogger(PdcDisguiseStore.class);
    private static final Gson GSON = new Gson();
    private static final int VERSION = 1;

    private final NamespacedKey key;

    @Inject
    public PdcDisguiseStore(PluginMain plugin) {
        this.key = new NamespacedKey(plugin, "active_disguise");
    }

    @Override
    public void save(Entity entity, StoredDisguise d) {
        JsonObject json = new JsonObject();
        json.addProperty("v", VERSION);
        json.addProperty("kind", d.kind().name());
        if (d.mobType() != null) {
            json.addProperty("mob", d.mobType().key().asString());
        }
        if (d.options() != null) {
            json.add("options", DisguiseOptionsCodec.toJson(d.options()));
        }
        if (d.playerOptions() != null) {
            json.add("playerOptions", DisguiseOptionsCodec.toJson(d.playerOptions()));
        }
        if (d.skin() != null) {
            json.add("skin", skinToJson(d.skin()));
        }
        if (d.shownName() != null) {
            json.addProperty("shown", d.shownName());
        }
        PlayerLookBackup o = d.original();
        if (o != null) {
            JsonObject orig = new JsonObject();
            if (o.getDisplayPlainOrNull() != null) {
                orig.addProperty("display", o.getDisplayPlainOrNull());
            }
            if (o.getListPlainOrNull() != null) {
                orig.addProperty("list", o.getListPlainOrNull());
            }
            if (o.getCustomNamePlainOrNull() != null) {
                orig.addProperty("custom", o.getCustomNamePlainOrNull());
            }
            orig.addProperty("customVisible", o.isCustomNameVisibleBefore());
            json.add("original", orig);
        }
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, GSON.toJson(json));
    }

    @Override
    public Optional<StoredDisguise> load(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String raw = pdc.get(key, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            JsonObject json = GSON.fromJson(raw, JsonObject.class);
            // Only players have an original look to restore; non-player mobs are stored without one.
            JsonObject orig = json.has("original") ? json.getAsJsonObject("original") : null;
            PlayerLookBackup original = orig == null ? null : new PlayerLookBackup(
                null,
                optString(orig, "display"),
                optString(orig, "list"),
                optString(orig, "custom"),
                orig.has("customVisible") && orig.get("customVisible").getAsBoolean()
            );
            StoredDisguise.Kind kind = StoredDisguise.Kind.valueOf(json.get("kind").getAsString());
            if (kind == StoredDisguise.Kind.MOB) {
                EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.fromString(json.get("mob").getAsString()));
                if (type == null) {
                    LOG.warn("Stored disguise for {} references unknown mob '{}'", entity.getUniqueId(), json.get("mob"));
                    return Optional.empty();
                }
                return Optional.of(StoredDisguise.mob(
                    type,
                    json.has("options") ? DisguiseOptionsCodec.mobFromJson(json.getAsJsonObject("options")) : null,
                    original
                ));
            }
            return Optional.of(StoredDisguise.player(
                skinFromJson(json.getAsJsonObject("skin")),
                optString(json, "shown"),
                json.has("playerOptions") ? DisguiseOptionsCodec.playerFromJson(json.getAsJsonObject("playerOptions")) : null,
                original
            ));
        } catch (RuntimeException ex) {
            LOG.error("Could not read stored disguise for {}", entity.getUniqueId(), ex);
            return Optional.empty();
        }
    }

    @Override
    public void clear(Entity entity) {
        entity.getPersistentDataContainer().remove(key);
    }

    private static JsonObject skinToJson(SkinSnapshot skin) {
        JsonObject out = new JsonObject();
        if (skin.getSkinSourceUuid() != null) {
            out.addProperty("uuid", skin.getSkinSourceUuid().toString());
        }
        if (skin.getDisplayName() != null) {
            out.addProperty("name", skin.getDisplayName());
        }
        JsonArray props = new JsonArray();
        for (SkinProperty p : skin.getProperties()) {
            JsonObject po = new JsonObject();
            po.addProperty("n", p.getName());
            po.addProperty("v", p.getValue());
            if (p.getSignature() != null) {
                po.addProperty("s", p.getSignature());
            }
            props.add(po);
        }
        out.add("props", props);
        return out;
    }

    private static SkinSnapshot skinFromJson(JsonObject json) {
        List<SkinProperty> props = new ArrayList<>();
        for (var e : json.getAsJsonArray("props")) {
            JsonObject po = e.getAsJsonObject();
            props.add(new SkinProperty(po.get("n").getAsString(), po.get("v").getAsString(), optString(po, "s")));
        }
        String uuid = optString(json, "uuid");
        return new SkinSnapshot(uuid == null ? null : UUID.fromString(uuid), optString(json, "name"), props);
    }

    private static String optString(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : null;
    }
}
