package ch.eneas.flipsdisguises.disguise.domain;

import net.kyori.adventure.text.Component;

import java.util.Optional;

public record MobDisguiseOptions(
    int slimeOrMagmaSize,
    Optional<Component> displayName,
    Optional<String> variant
) {

    public static final MobDisguiseOptions DEFAULT = new MobDisguiseOptions(0, Optional.empty(), Optional.empty());

    public int effectiveSlimeOrMagmaSize() {
        if (slimeOrMagmaSize < 1) {
            return 1;
        }
        return Math.min(slimeOrMagmaSize, 127);
    }
}
