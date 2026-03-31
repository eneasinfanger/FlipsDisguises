package ch.eneas.flipsdisguises.command.args.disgopts;

import ch.eneas.flipsdisguises.command.MobDisguiseFlagTokanizer;
import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.jorel.commandapi.SuggestionInfo;
import dev.jorel.commandapi.arguments.*;
import dev.jorel.commandapi.executors.CommandArguments;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkState;

@Slf4j
public class MobDisguiseOptionsArgument extends CustomArgument<MobDisguiseOptions, String> {
    private final MobDisguiseOptionsArgProcessor argProcessor;

    public MobDisguiseOptionsArgument(String nodeName, @NonNull String entityTypeNodeName) {
        this(nodeName, new MobDisguiseOptionsArgProcessor(entityTypeNodeName));
    }

    private MobDisguiseOptionsArgument(String nodeName, MobDisguiseOptionsArgProcessor processor) {
        super(new GreedyStringArgument(nodeName), processor);
        this.argProcessor = processor;
        replaceSuggestions(argProcessor);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S> MobDisguiseOptions parseArgument(CommandContext<S> cmdCtx, String key, CommandArguments previousArgs) throws CommandSyntaxException {
        this.argProcessor.lastCtx = (CommandContext<CommandSender>) cmdCtx;
        return super.parseArgument(cmdCtx, key, previousArgs);
    }

    @RequiredArgsConstructor
    private static class MobDisguiseOptionsArgProcessor implements ArgumentSuggestions<CommandSender>, CustomArgumentInfoParser<MobDisguiseOptions, String> {
        final String entityTypeNode;
        CommandContext<CommandSender> lastCtx;

        @Override
        public CompletableFuture<Suggestions> suggest(SuggestionInfo<CommandSender> info, SuggestionsBuilder builder) throws CommandSyntaxException {
            try {
                String raw = info.currentArg();
                EntityType type = getEntityType(info.previousArgs());
                boolean lastTokenComplete = info.currentArg().endsWith(" ");

                List<ParsedFlag> flags = parseFlags(raw, false);
                ParsedFlag lastFlag = flags.isEmpty() ? null : flags.getLast();
                int targetIdx = (lastTokenComplete || lastFlag == null)
                    ? info.currentInput().length()
                    : (lastFlag.value() != null ? lastFlag.valueIdx() : lastFlag.flagIdx());

                SuggestionsBuilder builderFromHere = builder.createOffset(targetIdx);

                if (lastFlag == null
                    || info.currentArg().endsWith(" ") // TODO: string values with quotes
                    && lastFlag.value() != null) {
                    MobDisguiseFlags.allFlags()
                        .filter(flag -> flag.isValidFor(type))
                        .filter(flag -> flags.stream().noneMatch(f -> f.key().equals(flag.getName())))
                        .map(flag -> "--" + flag.getName())
                        .filter(suggestion -> shouldSuggest(builderFromHere, suggestion))
                        .forEach(builderFromHere::suggest);
                    return builderFromHere.buildFuture();
                }

                MobDisguiseFlag lastDisgFlag = MobDisguiseFlags.ofFlag(lastFlag.key());
                if (lastDisgFlag != null) {
                    return lastDisgFlag.getType().getSuggestions(Objects.requireNonNullElse(lastFlag.value(), ""))
                        .thenApply(suggestions -> {
                            suggestions.getList().stream()
                                .filter(suggestion -> shouldSuggest(builderFromHere, suggestion.getText()))
                                .forEach(suggestion -> builderFromHere.suggest(suggestion.getText(), suggestion.getTooltip()));
                            return builderFromHere.build();
                        });
                }
                return builderFromHere.buildFuture();
            } catch (CommandSyntaxException e) {
                throw e;
            } catch (CustomArgumentException e) {
                throw convertToCommandSyntax(e, info.currentArg());
            } catch (Exception e) {
                log.error("Failed to get suggestions for input '{}' (specifically '{}'):", info.currentInput(), info.currentArg(), e);
                throw convertToCommandSyntax(createErrorExecutingCommand(), info.currentArg());
            }
        }

        private boolean shouldSuggest(SuggestionsBuilder builder, String suggestion) {
            return suggestion.toLowerCase().startsWith(builder.getRemaining().toLowerCase());
        }

        @Override
        public MobDisguiseOptions apply(CustomArgumentInfo<String> info) throws CustomArgumentException {
            try {
                String raw = info.currentInput();
                EntityType type = getEntityType(info.previousArgs());

                List<ParsedFlag> flags = parseFlags(raw, true);
                MobDisguiseOptions opts = new MobDisguiseOptions();

                for (var entry : flags) {
                    var flag = entry.key();
                    var rawValue = entry.value();

                    MobDisguiseFlag disgFlag = MobDisguiseFlags.ofFlag(flag);
                    if (disgFlag == null) {
                        throw CustomArgumentException.fromString("Unknown flag: --" + flag);
                    } else if (!disgFlag.isValidFor(type)) {
                        throw CustomArgumentException.fromString("Flag is not valid for type '" + type + "': --" + flag);
                    }
                    try {
                        Object value = disgFlag.getType().parse(rawValue);
                        disgFlag.setValue(opts, value);
                    } catch (CommandSyntaxException e) {
                        throw CustomArgumentException.fromString(e.getMessage());
                    }
                }
                return null;
            } catch (CustomArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to parse argument '{}': ", info.currentInput(), e);
                throw createErrorExecutingCommand();
            }
        }

        private CustomArgumentException createErrorExecutingCommand() {
            return CustomArgumentException.fromMessageBuilder(
                new MessageBuilder("Error executing command /").appendFullInput()
            );
        }

        private CommandSyntaxException convertToCommandSyntax(CustomArgumentException cex, String argument) {
            return cex.toCommandSyntax(argument, Optional.ofNullable(lastCtx)
                .orElseGet(() -> new CommandContextBuilder<CommandSender>(null, null, null, 0)
                    .build("/... " + argument)));
        }

        EntityType getEntityType(CommandArguments args) {
            Object fromNode = args.get(entityTypeNode);
            checkState(fromNode != null, "EntityType node must be before MobDisguiseOptions node! (or is the EntityType node-name \"%s\" misspelled?)", entityTypeNode);
            checkState(fromNode.getClass() == EntityType.class, "Type of node \"%s\" must be EntityType!", entityTypeNode);
            return (EntityType) fromNode;
        }

        List<ParsedFlag> parseFlags(String input, boolean requireValue) throws CustomArgumentException {
            List<Map.Entry<Integer, String>> tokens = MobDisguiseFlagTokanizer.tokenize(input).entrySet().stream().toList();
            List<ParsedFlag> flags = new ArrayList<>();

            for (int i = 0; i < tokens.size(); i++) {
                Map.Entry<Integer, String> t = tokens.get(i);
                if (!t.getValue().startsWith("--")) {
                    throw CustomArgumentException.fromString("Unexpected token (flags must start with --): " + t);
                }
                String flag = t.getValue().substring(2);
                boolean hasValue = i + 1 < tokens.size();
                if (requireValue && !hasValue) {
                    throw CustomArgumentException.fromString("Missing value for " + "--" + flag);
                }
                Map.Entry<Integer, String> nextToken = hasValue ? tokens.get(++i) : null;
                flags.add(new ParsedFlag(flag, t.getKey(), nextToken != null ? nextToken.getValue() : null, nextToken != null ? nextToken.getKey() : -1));
            }
            return flags;
        }

        record ParsedFlag(String key, int flagIdx, String value, int valueIdx) {}
    }
}

