package ch.eneas.flipsdisguises.disguise.port;

import ch.eneas.flipsdisguises.disguise.domain.StoredDisguise;
import org.bukkit.entity.Entity;

import java.util.Optional;

/**
 * Durable per-entity disguise state, so a disguise survives rejoins, chunk unloads and server restarts.
 */
public interface DisguiseStore {

    void save(Entity entity, StoredDisguise disguise);

    Optional<StoredDisguise> load(Entity entity);

    void clear(Entity entity);
}
