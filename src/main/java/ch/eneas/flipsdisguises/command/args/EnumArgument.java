package ch.eneas.flipsdisguises.command.args;

import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.CustomArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

public class EnumArgument<E extends Enum<E>> extends CustomArgument<E, String> {
    public EnumArgument(String nodeName, @NonNull Class<E> enumType) {
        super(new StringArgument(nodeName), new EnumArgParser<>(enumType));
        replaceSuggestions(ArgumentSuggestions.strings(formatEnumConstants(enumType)));
    }

    @RequiredArgsConstructor
    static class EnumArgParser<E extends Enum<E>> implements CustomArgumentInfoParser<E, String> {
        final Class<E> type;

        @Override
        public E apply(CustomArgumentInfo<String> info) throws CustomArgumentException {
            try {
                return Enum.valueOf(type, info.currentInput().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw CustomArgumentException.fromString("'" + info.currentInput() + "' is not a valid value!" +
                    " Possible values are: " + String.join(", ", formatEnumConstants(type)));
            }
        }
    }

    static List<String> formatEnumConstants(Class<? extends Enum<?>> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
            .map(Enum::name)
            .map(String::toLowerCase)
            .toList();
    }
}
