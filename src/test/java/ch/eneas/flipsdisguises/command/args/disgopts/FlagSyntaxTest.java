package ch.eneas.flipsdisguises.command.args.disgopts;

import ch.eneas.flipsdisguises.disguise.meta.EntityCategory;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlagSyntaxTest {

    @Test
    void tokenizesFlagsValuesAndQuotes() {
        List<FlagSyntax.Token> tokens = FlagSyntax.tokenize("--size 3 --name \"Big Slime\"");
        assertEquals(4, tokens.size());
        assertEquals(0, tokens.get(0).start());
        assertEquals("--size", tokens.get(0).text());
        assertEquals("3", tokens.get(1).text());
        assertEquals("Big Slime", tokens.get(3).text());
        assertEquals(16, tokens.get(3).start());
        assertTrue(tokens.get(3).quoted());
    }

    @Test
    void quotedDoubleDashIsAValueNotAFlag() throws Exception {
        List<FlagSyntax.Entry> entries = FlagSyntax.group(FlagSyntax.tokenize("--name \"--x\""));
        assertEquals(1, entries.size());
        assertEquals("--x", entries.getFirst().value().text());
    }

    @Test
    void flagWithoutValueIsFollowedByNextFlag() throws Exception {
        List<FlagSyntax.Entry> entries = FlagSyntax.group(FlagSyntax.tokenize("--a --b 1"));
        assertEquals(2, entries.size());
        assertNull(entries.get(0).value());
        assertEquals("1", entries.get(1).value().text());
    }

    @Test
    void strayValueIsRejected() {
        assertThrows(FlagSyntax.SyntaxException.class, () -> FlagSyntax.group(FlagSyntax.tokenize("3 --size")));
    }

    @Test
    void flagListingOnlyTypesIsValidForThoseTypes() {
        MobDisguiseFlag size = new MobDisguiseFlag("size", null, null,
            EnumSet.of(EntityType.SLIME, EntityType.MAGMA_CUBE), EnumSet.noneOf(EntityCategory.class));
        assertTrue(size.isValidFor(EntityType.SLIME));
        assertFalse(size.isValidFor(EntityType.COW));
    }

    @Test
    void categoryFlagMatchesByEntityClass() {
        MobDisguiseFlag baby = new MobDisguiseFlag("baby", null, null,
            EnumSet.noneOf(EntityType.class), EnumSet.of(EntityCategory.AGEABLE));
        assertTrue(baby.isValidFor(EntityType.COW));
        assertFalse(baby.isValidFor(EntityType.SLIME));
    }
}
