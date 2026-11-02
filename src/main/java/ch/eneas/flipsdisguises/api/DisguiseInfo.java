package ch.eneas.flipsdisguises.api;

import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Read-only snapshot of the disguise currently applied to an entity; see {@link FlipsDisguisesApi#getDisguise}.
 */
public sealed interface DisguiseInfo permits DisguiseInfo.PlayerDisguise, DisguiseInfo.MobDisguise {

    /**
     * A player wearing another player's skin and name.
     *
     * @param shownName     the name other players see
     * @param skinSourceUuid the Mojang UUID the skin was taken from, if known
     */
    record PlayerDisguise(@NotNull String shownName, @Nullable UUID skinSourceUuid) implements DisguiseInfo {}

    /**
     * An entity, player or not, shown as a mob.
     */
    record MobDisguise(@NotNull EntityType mobType, @NotNull MobDisguiseOptions options) implements DisguiseInfo {}
}
