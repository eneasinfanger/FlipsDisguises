package ch.eneas.flipsdisguises.command.args.disgopts;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.jorel.commandapi.arguments.AbstractArgument;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.executors.CommandArguments;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static ch.eneas.flipsdisguises.command.args.disgopts.MobDisguiseUtil.*;

@RequiredArgsConstructor
class MobDisguiseOptionType<T> {
    private static final Map<Class<? extends Argument<?>>, MobDisguiseOptionType<?>> CACHE = new HashMap<>();

    @SuppressWarnings("unchecked")
    public static MobDisguiseOptionType<Object> forArgType(Class<? extends Argument<?>> argType) {
        return (MobDisguiseOptionType<Object>) CACHE.computeIfAbsent(argType, t -> {
            try {
                Argument<?> arg = argType.getConstructor(String.class).newInstance("<none>");
                return new MobDisguiseOptionType<>(arg);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                throw new IllegalStateException("Argument-Type in MobDisguiseOptions must have constructor with just nodeName!", e);
            }
        });
    }

    final AbstractArgument<T, ?, ?, ?> arg;

    public T parse(String input) throws CommandSyntaxException {
        String key = "<input>";
        CommandArguments args = createCommandArgs(Map.of(input, key));
        CommandContext<?> newCtx = createCommandCtxBuilder()
            .withArgument(key, new ParsedArgument<>(0, input.length(), arg.getRawType().parse(new StringReader(input))))
            .build(input);
        return arg.parseArgument(newCtx, key, args);
    }

    public CompletableFuture<Suggestions> getSuggestions(String input) throws CommandSyntaxException {
        var builder = createSuggBuilder(input);
        var override = arg.getOverriddenSuggestions();
        if (override.isPresent()) {
            return override.get().suggest(createSuggestionInfoForInput(input), builder);
        }
        var typeSuggs = arg.getRawType().listSuggestions(createCommandCtx(input), builder);
        var additional = arg.getIncludedSuggestions();
        if (additional.isEmpty()) {
            return typeSuggs;
        }
        return typeSuggs.thenCombine(
            additional.get().suggest(createSuggestionInfoForInput(input), builder),
            (suggs1, suggs2) -> new Suggestions(suggs1.getRange(), Lists.newArrayList(Iterables.concat(suggs1.getList(), suggs2.getList())))
        );
    }
}
