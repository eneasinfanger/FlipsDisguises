package ch.eneas.flipsdisguises.command;

import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MobDisguiseFlagParser {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private MobDisguiseFlagParser() {
    }

    public static MobDisguiseOptions parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MobDisguiseOptions.DEFAULT;
        }
        List<String> tokens = tokenize(raw.trim());
        int size = 0;
        Optional<Component> name = Optional.empty();
        Optional<String> variant = Optional.empty();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (!t.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected token (flags must start with --): " + t);
            }
            String flag = t.substring(2).toLowerCase(Locale.ROOT);
            switch (flag) {
                case "size" -> {
                    requireHasValue(tokens, i, "--size");
                    size = parsePositiveInt(tokens.get(++i), "--size");
                }
                case "name" -> {
                    requireHasValue(tokens, i, "--name");
                    name = Optional.of(MINI.deserialize(tokens.get(++i)));
                }
                case "variant" -> {
                    requireHasValue(tokens, i, "--variant");
                    variant = Optional.of(tokens.get(++i));
                }
                default -> throw new IllegalArgumentException("Unknown flag: --" + flag);
            }
        }
        return new MobDisguiseOptions(size, name, variant);
    }

    private static void requireHasValue(List<String> tokens, int i, String flag) {
        if (i + 1 >= tokens.size()) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
    }

    private static int parsePositiveInt(String s, String flag) {
        try {
            int v = Integer.parseInt(s);
            if (v < 1 || v > 127) {
                throw new IllegalArgumentException(flag + " must be between 1 and 127");
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(flag + " expects an integer");
        }
    }

    static List<String> tokenize(String input) {
        List<String> out = new ArrayList<>();
        int i = 0;
        int n = input.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(input.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            if (input.startsWith("--", i)) {
                int j = i + 2;
                while (j < n && !Character.isWhitespace(input.charAt(j))) {
                    j++;
                }
                out.add(input.substring(i, j));
                i = j;
                continue;
            }
            char c = input.charAt(i);
            if (c == '"' || c == '\'') {
                char q = c;
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < n) {
                    char ch = input.charAt(i);
                    if (ch == '\\' && i + 1 < n) {
                        sb.append(input.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (ch == q) {
                        i++;
                        break;
                    }
                    sb.append(ch);
                    i++;
                }
                out.add(sb.toString());
            } else {
                int j = i;
                while (j < n && !Character.isWhitespace(input.charAt(j))) {
                    j++;
                }
                out.add(input.substring(i, j));
                i = j;
            }
        }
        return out;
    }
}
