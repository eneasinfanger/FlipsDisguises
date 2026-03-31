package ch.eneas.flipsdisguises.command;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.SequencedMap;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MobDisguiseFlagTokanizer {
    /**
     * @return map[0] = start idx of input for token, map[1] = token
     */
    public static SequencedMap<Integer, String> tokenize(String input) {
        SequencedMap<Integer, String> out = new LinkedHashMap<>();
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
                out.put(i, input.substring(i, j));
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
                out.put(i - sb.length(), sb.toString());
            } else {
                int j = i;
                while (j < n && !Character.isWhitespace(input.charAt(j))) {
                    j++;
                }
                out.put(i, input.substring(i, j));
                i = j;
            }
        }
        return out;
    }
}
