package ch.eneas.flipsdisguises.disguise.adapter.paper;

import ch.eneas.flipsdisguises.disguise.domain.PlayerDisguiseOptions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import org.bukkit.entity.Pose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DisguiseOptionsCodecTest {

    @Test
    void playerOptionsRoundTrip() {
        PlayerDisguiseOptions options = PlayerDisguiseOptions.DEFAULT
            .withDisplayName(Component.text("Steve"))
            .withGlowing(true)
            .withPose(Pose.SNEAKING)
            .withBurning(TriState.TRUE)
            .withSize(2.5)
            .withLeftHanded(true)
            .withCape(false);

        PlayerDisguiseOptions read = DisguiseOptionsCodec.playerFromJson(DisguiseOptionsCodec.toJson(options));

        assertEquals(Component.text("Steve"), read.displayName());
        assertEquals(true, read.glowing());
        assertEquals(Pose.SNEAKING, read.pose());
        assertEquals(TriState.TRUE, read.burning());
        assertEquals(2.5, read.size());
        assertEquals(true, read.leftHanded());
        assertEquals(false, read.cape());
        assertNull(read.silent());
    }

    @Test
    void skinPartsShowEveryLayerUnlessSwitchedOff() {
        assertEquals((byte) 0x7F, PlayerDisguiseOptions.DEFAULT.skinPartsMask());
        assertEquals(
            (byte) (0x7F & ~PlayerDisguiseOptions.PART_CAPE),
            PlayerDisguiseOptions.DEFAULT.withCape(false).skinPartsMask()
        );
    }
}
