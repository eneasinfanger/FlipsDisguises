package ch.eneas.flipsdisguises.disguise.port;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface MobDisguisePort {

    boolean isMobDisguised(UUID playerId);

    void attach(Player subject, EntityType type);

    void detach(Player subject);

    void detachDecoyOnly(Player subject);

    void detachDisconnected(UUID subjectId);

    void syncNewObserver(Player observer);
}
