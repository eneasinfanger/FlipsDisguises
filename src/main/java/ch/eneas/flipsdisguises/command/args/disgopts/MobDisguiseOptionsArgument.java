package ch.eneas.flipsdisguises.command.args.disgopts;

import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.jorel.commandapi.SuggestionInfo;
import dev.jorel.commandapi.arguments.CustomArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkState;

/**
 * Greedy {@code --flag value ...} tail of a mob-disguise command. Which flags exist depends on the
 * {@link EntityType} parsed by an earlier node.
 */
@Slf4j
public class MobDisguiseOptionsArgument extends CustomArgument<MobDisguiseOptions, String> {

    public MobDisguiseOptionsArgument(String nodeName, @NonNull String entityTypeNodeName) {
        super(new GreedyStringArgument(nodeName), info -> parse(info.currentInput(), entityType(info.previousArgs(), entityTypeNodeName)));
        replaceSuggestions((info, builder) -> suggest(info, builder, entityTypeNodeName));
    }

    private static MobDisguiseOptions parse(String input, EntityType type) throws CustomArgumentException {
        try {
            MobDisguiseOptions opts = new MobDisguiseOptions();
            Set<String> seen = new HashSet<>();
            for (FlagSyntax.Entry entry : FlagSyntax.group(FlagSyntax.tokenize(input))) {
                String name = entry.name();
                MobDisguiseFlag flag = MobDisguiseFlags.ofFlag(name);
                if (flag == null) {
                    throw new FlagError("Unknown flag --" + name + ". " + validFlagsHint(type));
                }
                if (!flag.isValidFor(type)) {
                    throw new FlagError("--" + name + " can't be used with " + type + ". " + validFlagsHint(type));
                }
                if (!seen.add(name)) {
                    throw new FlagError("Duplicate flag --" + name);
                }
                if (entry.value() == null) {
                    throw new FlagError("Missing value for --" + name);
                }
                try {
                    flag.setValue(opts, flag.getType().parse(entry.value().text()));
                } catch (CommandSyntaxException e) {
                    throw new FlagError("Invalid value for --" + name + ": " + e.getMessage());
                }
            }
            return opts;
        } catch (FlagSyntax.SyntaxException | FlagError e) {
            throw CustomArgumentException.fromString(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Failed to parse mob flags '{}':", input, e);
            throw CustomArgumentException.fromString("Failed to parse flags (see console).");
        }
    }

    private static String validFlagsHint(EntityType type) {
        List<String> names = MobDisguiseFlags.namesFor(type);
        return names.isEmpty() ? type + " has no flags." : "Flags for " + type + ": " + String.join(", ", names.stream().map(n -> "--" + n).toList());
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionInfo<CommandSender> info, SuggestionsBuilder builder,
                                                          String entityTypeNode) throws CommandSyntaxException {
        try {
            EntityType type = entityType(info.previousArgs(), entityTypeNode);
            return FlagSyntax.suggest(info.currentInput(), info.currentArg(), builder, MobDisguiseFlags.namesFor(type), (name, partial) -> {
                MobDisguiseFlag flag = MobDisguiseFlags.ofFlag(name);
                return flag == null || !flag.isValidFor(type) ? Suggestions.empty() : flag.getType().getSuggestions(partial);
            });
        } catch (CommandSyntaxException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to suggest mob flags for '{}':", info.currentInput(), e);
            return builder.buildFuture();
        }
    }

    private static EntityType entityType(CommandArguments args, String nodeName) {
        Object node = args.get(nodeName);
        checkState(node instanceof EntityType, "Node \"%s\" must come before the flags node and be an EntityType!", nodeName);
        return (EntityType) node;
    }

    /** User-facing parse failure, converted to a {@link CustomArgumentException}. */
    private static final class FlagError extends RuntimeException {
        FlagError(String message) {
            super(message, null, false, false);
        }
    }
}
