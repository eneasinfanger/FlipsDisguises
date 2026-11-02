package ch.eneas.flipsdisguises.disguise.domain;

import ch.eneas.flipsdisguises.command.args.PoseArgument;
import ch.eneas.flipsdisguises.command.args.TriStateArgument;
import ch.eneas.flipsdisguises.disguise.meta.ArgType;
import dev.jorel.commandapi.arguments.BooleanArgument;
import dev.jorel.commandapi.arguments.ChatComponentArgument;
import dev.jorel.commandapi.arguments.DoubleArgument;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import org.bukkit.entity.Pose;

/**
 * Appearance options of a player-model disguise. Every field is optional: {@code null} keeps the default.
 */
@With
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@Accessors(fluent = true)
@Getter
public class PlayerDisguiseOptions {
    public static final PlayerDisguiseOptions DEFAULT = new PlayerDisguiseOptions();

    /** Bit values of the player "displayed skin parts" metadata byte. */
    public static final int PART_CAPE = 0x01;
    public static final int PART_JACKET = 0x02;
    public static final int PART_LEFT_SLEEVE = 0x04;
    public static final int PART_RIGHT_SLEEVE = 0x08;
    public static final int PART_LEFT_PANTS = 0x10;
    public static final int PART_RIGHT_PANTS = 0x20;
    public static final int PART_HAT = 0x40;

    /** Overhead name; falls back to the skin owner's name. */
    @ArgType(ChatComponentArgument.class)
    private Component displayName;
    @ArgType(BooleanArgument.class)
    private Boolean nametagVisible;
    @ArgType(BooleanArgument.class)
    private Boolean glowing;
    @ArgType(BooleanArgument.class)
    private Boolean silent;
    @ArgType(BooleanArgument.class)
    private Boolean sneaking;
    @ArgType(PoseArgument.class)
    private Pose pose;
    @ArgType(TriStateArgument.class)
    private TriState burning;
    /** Left-handed model when {@code true}; right-handed otherwise. */
    @ArgType(BooleanArgument.class)
    private Boolean leftHanded;
    /** Scale of the model, e.g. {@code 2.0} for twice the normal size. */
    @ArgType(DoubleArgument.class)
    private Double size;
    @ArgType(BooleanArgument.class)
    private Boolean cape;
    @ArgType(BooleanArgument.class)
    private Boolean jacket;
    @ArgType(BooleanArgument.class)
    private Boolean leftSleeve;
    @ArgType(BooleanArgument.class)
    private Boolean rightSleeve;
    @ArgType(BooleanArgument.class)
    private Boolean leftPants;
    @ArgType(BooleanArgument.class)
    private Boolean rightPants;
    @ArgType(BooleanArgument.class)
    private Boolean hat;

    /** The displayed-skin-parts byte; every layer is shown unless explicitly switched off. */
    public byte skinPartsMask() {
        int mask = 0;
        mask |= part(cape, PART_CAPE);
        mask |= part(jacket, PART_JACKET);
        mask |= part(leftSleeve, PART_LEFT_SLEEVE);
        mask |= part(rightSleeve, PART_RIGHT_SLEEVE);
        mask |= part(leftPants, PART_LEFT_PANTS);
        mask |= part(rightPants, PART_RIGHT_PANTS);
        mask |= part(hat, PART_HAT);
        return (byte) mask;
    }

    /** Applies the skin layer options to a skin-parts byte; unset layers keep what the player's client reports. */
    public byte applyToSkinParts(int parts) {
        int out = parts;
        out = withBit(out, PART_CAPE, cape);
        out = withBit(out, PART_JACKET, jacket);
        out = withBit(out, PART_LEFT_SLEEVE, leftSleeve);
        out = withBit(out, PART_RIGHT_SLEEVE, rightSleeve);
        out = withBit(out, PART_LEFT_PANTS, leftPants);
        out = withBit(out, PART_RIGHT_PANTS, rightPants);
        out = withBit(out, PART_HAT, hat);
        return (byte) out;
    }

    public boolean hasSkinPartOverrides() {
        return cape != null || jacket != null || leftSleeve != null || rightSleeve != null
            || leftPants != null || rightPants != null || hat != null;
    }

    /** Applies the glowing, burning and sneaking options to a shared-flags byte; unset options keep their bit. */
    public byte applyToSharedFlags(int flags) {
        int out = flags;
        out = withBit(out, 0x40, glowing);
        out = withBit(out, 0x01, burning == null || burning == TriState.NOT_SET ? null : burning == TriState.TRUE);
        out = withBit(out, 0x02, sneaking);
        return (byte) out;
    }

    private static int withBit(int flags, int bit, Boolean enabled) {
        if (enabled == null) {
            return flags;
        }
        return enabled ? flags | bit : flags & ~bit;
    }

    public boolean isNametagVisible() {
        return nametagVisible == null || nametagVisible;
    }

    /** The scale to apply, or {@code 1.0} for none; clamped to the range the client attribute accepts. */
    public double effectiveSize() {
        if (size == null) {
            return 1.0;
        }
        return Math.max(0.0625, Math.min(size, 16.0));
    }

    private static int part(Boolean enabled, int bit) {
        return enabled == null || enabled ? bit : 0;
    }
}
