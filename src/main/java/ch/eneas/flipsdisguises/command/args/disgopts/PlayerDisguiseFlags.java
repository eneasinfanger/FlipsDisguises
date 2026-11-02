package ch.eneas.flipsdisguises.command.args.disgopts;

import ch.eneas.flipsdisguises.disguise.domain.PlayerDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.meta.ArgType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code --flag value} options of a player-model disguise. Every annotated field of
 * {@link PlayerDisguiseOptions} is a flag named like the field.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlayerDisguiseFlags {

    private static final Map<String, Flag> FLAGS = new TreeMap<>();

    static {
        Arrays.stream(PlayerDisguiseOptions.class.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()) && f.isAnnotationPresent(ArgType.class))
            .forEach(f -> {
                f.setAccessible(true);
                FLAGS.put(f.getName(), new Flag(MobDisguiseOptionType.forArgType(f.getAnnotation(ArgType.class).value()), f));
            });
    }

    private record Flag(MobDisguiseOptionType<Object> type, Field field) {
    }

    /** A flag that could not be parsed; the message is meant for the command sender. */
    public static final class FlagException extends Exception {
        public FlagException(String message) {
            super(message);
        }
    }

    public static Collection<String> names() {
        return FLAGS.keySet();
    }

    /** Parses {@code --flag value} pairs; blank input yields the default options. */
    public static PlayerDisguiseOptions parse(String input) throws FlagException {
        PlayerDisguiseOptions options = new PlayerDisguiseOptions();
        List<FlagSyntax.Entry> entries;
        try {
            entries = FlagSyntax.group(FlagSyntax.tokenize(input));
        } catch (FlagSyntax.SyntaxException e) {
            throw new FlagException(e.getMessage());
        }
        Set<String> seen = new HashSet<>();
        for (FlagSyntax.Entry entry : entries) {
            String name = entry.name();
            Flag flag = FLAGS.get(name);
            if (flag == null) {
                throw new FlagException("Unknown flag --" + name + ". Flags: " + String.join(", ", names().stream().map(n -> "--" + n).toList()));
            }
            if (!seen.add(name)) {
                throw new FlagException("Duplicate flag --" + name);
            }
            if (entry.value() == null) {
                throw new FlagException("Missing value for --" + name);
            }
            try {
                flag.field().set(options, flag.type().parse(entry.value().text()));
            } catch (CommandSyntaxException e) {
                throw new FlagException("Invalid value for --" + name + ": " + e.getMessage());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return options;
    }

    /** Suggestions for the value of {@code name}, or none if it is not a flag. */
    static CompletableFuture<Suggestions> valueSuggestions(String name, String partialValue) throws CommandSyntaxException {
        Flag flag = FLAGS.get(name);
        return flag == null ? Suggestions.empty() : flag.type().getSuggestions(partialValue);
    }
}
