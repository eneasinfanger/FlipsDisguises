package ch.eneas.flipsdisguises.command.args.disgopts;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Shared syntax for {@code --flag value --flag value ...} command tails: tokenizing, pairing flags with their
 * values and tab-completion. Knows nothing about which flags exist; callers supply that.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FlagSyntax {

    /**
     * @param start index of the first character of the token (the opening quote for quoted tokens)
     * @param end   index after the last character
     * @param text  the token without quotes and with escapes resolved
     */
    record Token(int start, int end, String text, boolean quoted) {
        /** Only unquoted tokens starting with {@code --} are flags, so values may be quoted to start with {@code --}. */
        boolean isFlag() {
            return !quoted && text.startsWith("--");
        }

        String flagName() {
            return text.substring(2);
        }
    }

    /** A flag together with its value token ({@code null} if none was given). */
    record Entry(Token flag, Token value) {
        String name() {
            return flag.flagName();
        }
    }

    /** Malformed flag input; the message is meant for the command sender. */
    static final class SyntaxException extends Exception {
        SyntaxException(String message) {
            super(message);
        }
    }

    @FunctionalInterface
    interface ValueSuggester {
        CompletableFuture<Suggestions> suggest(String flagName, String partialValue) throws CommandSyntaxException;
    }

    static List<Token> tokenize(String input) {
        List<Token> out = new ArrayList<>();
        int n = input.length();
        int i = 0;
        while (i < n) {
            if (Character.isWhitespace(input.charAt(i))) {
                i++;
                continue;
            }
            int start = i;
            char c = input.charAt(i);
            if (c == '"' || c == '\'') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < n) {
                    char ch = input.charAt(i);
                    if (ch == '\\' && i + 1 < n) {
                        sb.append(input.charAt(i + 1));
                        i += 2;
                    } else if (ch == c) {
                        i++;
                        break;
                    } else {
                        sb.append(ch);
                        i++;
                    }
                }
                out.add(new Token(start, i, sb.toString(), true));
            } else {
                while (i < n && !Character.isWhitespace(input.charAt(i))) {
                    i++;
                }
                out.add(new Token(start, i, input.substring(start, i), false));
            }
        }
        return out;
    }

    /** Pairs every flag with the token following it, unless that token is itself a flag. */
    static List<Entry> group(List<Token> tokens) throws SyntaxException {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token flag = tokens.get(i);
            if (!flag.isFlag()) {
                throw new SyntaxException("Unexpected '" + flag.text() + "' (flags must start with --)");
            }
            Token value = i + 1 < tokens.size() && !tokens.get(i + 1).isFlag() ? tokens.get(++i) : null;
            entries.add(new Entry(flag, value));
        }
        return entries;
    }

    /**
     * Completes whatever the user is currently typing: a flag name, or the value of the preceding flag.
     *
     * @param currentInput the whole command line typed so far
     * @param raw          the part of it that belongs to the flag argument
     * @param flagNames    the flags that may be offered (already typed ones are removed here)
     */
    static CompletableFuture<Suggestions> suggest(String currentInput, String raw, SuggestionsBuilder builder,
                                                  Collection<String> flagNames, ValueSuggester values) throws CommandSyntaxException {
        List<Token> tokens = tokenize(raw);
        boolean startingNewToken = raw.isEmpty() || Character.isWhitespace(raw.charAt(raw.length() - 1));
        int argStart = currentInput.length() - raw.length();

        Token last = tokens.isEmpty() ? null : tokens.getLast();
        if (startingNewToken) {
            SuggestionsBuilder here = builder.createOffset(currentInput.length());
            return last != null && last.isFlag()
                ? suggestValues(here, values, last.flagName(), "")
                : suggestFlags(here, flagNames, tokens, "");
        }

        SuggestionsBuilder here = builder.createOffset(argStart + last.start());
        if (last.isFlag()) {
            return suggestFlags(here, flagNames, tokens.subList(0, tokens.size() - 1), last.text());
        }
        Token previous = tokens.size() < 2 ? null : tokens.get(tokens.size() - 2);
        return previous != null && previous.isFlag()
            ? suggestValues(here, values, previous.flagName(), last.text())
            : here.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestFlags(SuggestionsBuilder here, Collection<String> flagNames,
                                                               List<Token> alreadyTyped, String prefix) {
        flagNames.stream()
            .filter(name -> alreadyTyped.stream().noneMatch(t -> t.isFlag() && t.flagName().equals(name)))
            .map(name -> "--" + name)
            .filter(flag -> startsWith(flag, prefix))
            .forEach(here::suggest);
        return here.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestValues(SuggestionsBuilder here, ValueSuggester values,
                                                                String flagName, String partial) throws CommandSyntaxException {
        return values.suggest(flagName, partial).thenApply(suggestions -> {
            suggestions.getList().stream()
                .filter(s -> startsWith(s.getText(), partial))
                .forEach(s -> here.suggest(s.getText(), s.getTooltip()));
            return here.build();
        });
    }

    private static boolean startsWith(String text, String prefix) {
        return text.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }
}
