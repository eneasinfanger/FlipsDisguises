package ch.eneas.flipsdisguises.disguise.port;

import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface MobDisguisePort {

    boolean isMobDisguised(UUID entityUuid);

    default void attach(LivingEntity subject, EntityType type) {
        attach(subject, type, MobDisguiseOptions.DEFAULT);
    }

    void attach(LivingEntity subject, EntityType type, MobDisguiseOptions options);

    void detach(LivingEntity subject);

    void detachDecoyOnly(LivingEntity subject);

    void detachDisconnected(UUID entityUuid);

    void syncNewObserver(Player observer);
}
