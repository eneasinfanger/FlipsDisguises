package ch.eneas.flipsdisguises.api;

import ch.eneas.flipsdisguises.disguise.application.DisguiseResult;
import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Every method that changes a disguise must be called from the main server thread and throws
 * {@link IllegalStateException} otherwise. The result takes effect immediately, except for
 * {@link #disguisePlayerAsPlayer}, which looks the skin up asynchronously.
 */
public interface FlipsDisguisesApi {

    /**
     * Disguises the player with the skin and name of {@code skinUsername}. The skin lookup is asynchronous; the
     * returned future completes on the main thread once the disguise is applied (or has failed).
     */
    CompletableFuture<DisguiseResult> disguisePlayerAsPlayer(Player target, String skinUsername);

    DisguiseResult disguisePlayerAsMob(Player target, EntityType mobType);

    DisguiseResult disguisePlayerAsMob(Player target, EntityType mobType, MobDisguiseOptions options);

    /**
     * Disguises a non-player entity. Players are rejected; use {@link #disguisePlayerAsMob} for them.
     */
    DisguiseResult disguiseLivingAsMob(LivingEntity target, EntityType mobType);

    DisguiseResult disguiseLivingAsMob(LivingEntity target, EntityType mobType, MobDisguiseOptions options);

    DisguiseResult undisguise(Player target);

    DisguiseResult undisguiseMob(LivingEntity target);

    boolean isDisguised(UUID playerId);

    boolean isMobModelDisguised(UUID entityUuid);
}
