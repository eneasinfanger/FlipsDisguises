package ch.eneas.flipsdisguises.command.args.disgopts;

import ch.eneas.flipsdisguises.command.MobDisguiseFlagTokanizer;
import ch.eneas.flipsdisguises.disguise.domain.PlayerDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.meta.ArgType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
        List<Map.Entry<Integer, String>> tokens = new ArrayList<>(MobDisguiseFlagTokanizer.tokenize(input).entrySet());
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i).getValue();
            if (!token.startsWith("--")) {
                throw new FlagException("Unexpected token (flags must start with --): " + token);
            }
            String name = token.substring(2);
            Flag flag = FLAGS.get(name);
            if (flag == null) {
                throw new FlagException("Unknown flag: --" + name);
            }
            if (i + 1 >= tokens.size()) {
                throw new FlagException("Missing value for --" + name);
            }
            try {
                flag.field().set(options, flag.type().parse(tokens.get(++i).getValue()));
            } catch (CommandSyntaxException e) {
                throw new FlagException(e.getMessage());
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
