package ch.eneas.flipsdisguises.disguise.domain;

import org.bukkit.entity.EntityType;

/**
 * An entity's active disguise in a form that can be persisted and re-applied after a rejoin or restart.
 * {@code options} belongs to a {@code MOB} disguise and {@code playerOptions} to a {@code PLAYER} disguise.
 *
 * @param original the look to restore on undisguise; only set for players, and only its name fields are persisted,
 *                 its skin is {@code null} after loading and must be re-captured from the live profile
 */
public record StoredDisguise(
    Kind kind,
    EntityType mobType,
    MobDisguiseOptions options,
    SkinSnapshot skin,
    String shownName,
    PlayerDisguiseOptions playerOptions,
    PlayerLookBackup original
) {

    public enum Kind {
        MOB,
        PLAYER
    }

    public static StoredDisguise mob(EntityType type, MobDisguiseOptions options, PlayerLookBackup original) {
        return new StoredDisguise(
            Kind.MOB, type, options == null ? MobDisguiseOptions.DEFAULT : options, null, null, null, original
        );
    }

    public static StoredDisguise player(
        SkinSnapshot skin,
        String shownName,
        PlayerDisguiseOptions options,
        PlayerLookBackup original
    ) {
        return new StoredDisguise(
            Kind.PLAYER, null, null, skin, shownName, options == null ? PlayerDisguiseOptions.DEFAULT : options, original
        );
    }

    public StoredDisguise withOriginal(PlayerLookBackup replacement) {
        return new StoredDisguise(kind, mobType, options, skin, shownName, playerOptions, replacement);
    }
}
