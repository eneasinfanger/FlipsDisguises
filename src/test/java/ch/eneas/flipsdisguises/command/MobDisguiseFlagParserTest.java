package ch.eneas.flipsdisguises.command;

import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobDisguiseFlagParserTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void parseEmptyYieldsDefault() {
        assertEquals(MobDisguiseOptions.DEFAULT, MobDisguiseFlagParser.parse(""));
        assertEquals(MobDisguiseOptions.DEFAULT, MobDisguiseFlagParser.parse("   "));
    }

    @Test
    void parseSizeAndName() {
        MobDisguiseOptions o = MobDisguiseFlagParser.parse("--size 4 --name \"<green>Hi\"");
        assertEquals(4, o.slimeOrMagmaSize());
        assertTrue(o.displayName().isPresent());
        assertEquals("Hi", PLAIN.serialize(o.displayName().get()));
    }

    @Test
    void parseVariant() {
        MobDisguiseOptions o = MobDisguiseFlagParser.parse("--variant COLD");
        assertEquals(Optional.of("COLD"), o.variant());
    }

    @Test
    void rejectsUnknownFlag() {
        assertThrows(IllegalArgumentException.class, () -> MobDisguiseFlagParser.parse("--foo bar"));
    }

    @Test
    void tokenizeRespectsQuotes() {
        List<String> t = MobDisguiseFlagParser.tokenize("--name \"a b\" --size 2");
        assertEquals(List.of("--name", "a b", "--size", "2"), t);
    }
}
