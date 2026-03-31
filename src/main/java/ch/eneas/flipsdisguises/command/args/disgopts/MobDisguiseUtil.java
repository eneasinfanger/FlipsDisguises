package ch.eneas.flipsdisguises.command.args.disgopts;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.RootCommandNode;
import dev.jorel.commandapi.SuggestionInfo;
import dev.jorel.commandapi.executors.CommandArguments;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;

import java.util.Collections;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class MobDisguiseUtil {
    static Object CTX_SOURCE_REF;

    @SuppressWarnings("unchecked")
    static <C> SuggestionInfo<C> createSuggestionInfoForInput(String input) {
        return new SuggestionInfo<>((C) Bukkit.getConsoleSender(), createCommandArgs(Map.of()), input, input);
    }

    static CommandArguments createCommandArgs(Map<String, String> args) {
        String[] rawArgs = args.values().toArray(String[]::new);
        return new CommandArguments(rawArgs, Collections.unmodifiableMap(args), rawArgs, args, String.join(" ", rawArgs));
    }

    @SuppressWarnings("unchecked")
    static <C> CommandContextBuilder<C> createCommandCtxBuilder() {
        return (CommandContextBuilder<C>) new CommandContextBuilder<>(null, CTX_SOURCE_REF, new RootCommandNode<>(), 0);
    }

    static <C> CommandContext<C> createCommandCtx(String input) {
        return MobDisguiseUtil.<C>createCommandCtxBuilder().build(input);
    }

    static SuggestionsBuilder createSuggBuilder(String input) {
        return new SuggestionsBuilder(input, 0);
    }
}
