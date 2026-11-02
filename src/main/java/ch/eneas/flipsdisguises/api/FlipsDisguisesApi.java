package ch.eneas.flipsdisguises.api;

import ch.eneas.flipsdisguises.disguise.application.DisguiseResult;
import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Every method that changes a disguise must be called from the main server thread and throws
 * {@link IllegalStateException} otherwise. The result takes effect immediately, except for
 * {@link #disguiseAsPlayer}, which looks the skin up asynchronously. Queries only read in-memory state.
 */
public interface FlipsDisguisesApi {

    /**
     * Disguises the player with the skin and name of {@code skinUsername}. The skin lookup is asynchronous; the
     * returned future completes on the main thread once the disguise is applied (or has failed).
     */
    @NotNull CompletableFuture<DisguiseResult> disguiseAsPlayer(@NotNull Player target, @NotNull String skinUsername);

    @NotNull DisguiseResult disguiseAsMob(@NotNull LivingEntity target, @NotNull EntityType mobType);

    /**
     * Shows the entity (a player or any other living entity) as a mob.
     *
     * @param options how the mob is shown; {@code null} means {@link MobDisguiseOptions#DEFAULT}
     */
    @NotNull DisguiseResult disguiseAsMob(
        @NotNull LivingEntity target,
        @NotNull EntityType mobType,
        @Nullable MobDisguiseOptions options
    );

    /**
     * Removes the disguise from a player (restoring their look) or from any other living entity. The result is a
     * failure if the entity was not disguised.
     */
    @NotNull DisguiseResult undisguise(@NotNull LivingEntity target);

    /**
     * @return the disguise currently applied to this entity, or empty if it has none
     */
    @NotNull Optional<DisguiseInfo> getDisguise(@NotNull LivingEntity target);

    boolean isDisguised(@NotNull LivingEntity target);
}
