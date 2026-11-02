package ch.eneas.flipsdisguises.disguise.port;

import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.domain.PlayerDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import ch.eneas.flipsdisguises.disguise.domain.StoredDisguise;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public interface MobDisguisePort {

    boolean isMobDisguised(UUID entityUuid);

    /**
     * The disguise currently attached to this entity, as a {@link StoredDisguise} of kind {@code MOB} (or
     * {@code PLAYER} for a player model), without an original look, which the port does not track.
     */
    Optional<StoredDisguise> mobDisguiseOf(UUID entityUuid);

    default void attach(LivingEntity subject, EntityType type) {
        attach(subject, type, MobDisguiseOptions.DEFAULT);
    }

    void attach(LivingEntity subject, EntityType type, MobDisguiseOptions options);

    /**
     * Shows a non-player entity as a player model wearing {@code skin}. The disguise is reported by
     * {@link #mobDisguiseOf} as a {@code PLAYER} disguise.
     *
     * @param shownName the name of the player model unless {@code options} carries a display name
     */
    void attachPlayerModel(LivingEntity subject, SkinSnapshot skin, String shownName, PlayerDisguiseOptions options);

    void detach(LivingEntity subject);

    void detachDecoyOnly(LivingEntity subject);

    void detachDisconnected(UUID entityUuid);

    void syncNewObserver(Player observer);

    /**
     * Spoofs the profile/display name sent in player-info packets for a player disguise, so other viewers
     * see the disguise name in the tab list and on the overhead nameplate.
     */
    default void setSpoofedPlayerName(UUID playerUuid, String spoofedName) {
    }

    default void clearSpoofedPlayerName(UUID playerUuid) {
    }
}
