package ch.eneas.flipsdisguises.command.args.disgopts;

import ch.eneas.flipsdisguises.command.MobDisguiseFlagTokanizer;
import ch.eneas.flipsdisguises.disguise.domain.PlayerDisguiseOptions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.jorel.commandapi.SuggestionInfo;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.CustomArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Greedy {@code --flag value ...} tail of a command, parsed into {@link PlayerDisguiseOptions}. */
public class PlayerDisguiseOptionsArgument extends CustomArgument<PlayerDisguiseOptions, String> {

    public PlayerDisguiseOptionsArgument(String nodeName) {
        super(new GreedyStringArgument(nodeName), info -> {
            try {
                return PlayerDisguiseFlags.parse(info.currentInput());
            } catch (PlayerDisguiseFlags.FlagException e) {
                throw CustomArgumentException.fromString(e.getMessage());
            }
        });
        replaceSuggestions(PlayerDisguiseOptionsArgument::suggest);
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionInfo<CommandSender> info, SuggestionsBuilder builder)
        throws CommandSyntaxException {
        String raw = info.currentArg();
        List<Map.Entry<Integer, String>> tokens = new ArrayList<>(MobDisguiseFlagTokanizer.tokenize(raw).entrySet());
        boolean lastComplete = raw.endsWith(" ");
        // Position of the token being typed, or the end of the input when starting a new one.
        int offset = lastComplete || tokens.isEmpty()
            ? info.currentInput().length()
            : info.currentInput().length() - raw.length() + tokens.getLast().getKey();
        SuggestionsBuilder fromHere = builder.createOffset(offset);

        String last = tokens.isEmpty() ? null : tokens.getLast().getValue();
        String previous = tokens.size() < 2 ? null : tokens.get(tokens.size() - 2).getValue();
        boolean typingValue = !lastComplete && previous != null && previous.startsWith("--")
            || lastComplete && last != null && last.startsWith("--");
        if (typingValue) {
            String flag = (lastComplete ? last : previous).substring(2);
            String partial = lastComplete ? "" : last;
            return PlayerDisguiseFlags.valueSuggestions(flag, partial).thenApply(suggestions -> {
                suggestions.getList().stream()
                    .filter(s -> s.getText().toLowerCase().startsWith(fromHere.getRemaining().toLowerCase()))
                    .forEach(s -> fromHere.suggest(s.getText(), s.getTooltip()));
                return fromHere.build();
            });
        }
        List<String> used = tokens.stream().map(Map.Entry::getValue).filter(t -> t.startsWith("--")).toList();
        PlayerDisguiseFlags.names().stream()
            .map(n -> "--" + n)
            .filter(n -> !used.contains(n))
            .filter(n -> n.toLowerCase().startsWith(fromHere.getRemaining().toLowerCase()))
            .forEach(fromHere::suggest);
        return fromHere.buildFuture();
    }
}
