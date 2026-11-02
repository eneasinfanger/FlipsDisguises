package ch.eneas.flipsdisguises.command.args.disgopts;

import ch.eneas.flipsdisguises.disguise.domain.PlayerDisguiseOptions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.jorel.commandapi.SuggestionInfo;
import dev.jorel.commandapi.arguments.CustomArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import org.bukkit.command.CommandSender;

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
        return FlagSyntax.suggest(info.currentInput(), info.currentArg(), builder, PlayerDisguiseFlags.names(),
            PlayerDisguiseFlags::valueSuggestions);
    }
}
