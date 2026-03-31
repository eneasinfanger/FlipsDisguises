package ch.eneas.flipsdisguises.command.args;

import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.CustomArgument;
import dev.jorel.commandapi.arguments.NamespacedKeyArgument;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import lombok.RequiredArgsConstructor;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class RegistryArgument<T extends Keyed> extends CustomArgument<T, NamespacedKey> {
    public RegistryArgument(String nodeName, RegistryKey<T> registryKey) {
        this(nodeName, RegistryAccess.registryAccess().getRegistry(registryKey));
    }

    public RegistryArgument(String nodeName, Registry<T> registry) {
        super(new NamespacedKeyArgument(nodeName), new RegistryArgParser<>(registry));
        replaceSuggestions(ArgumentSuggestions.strings(formatRegistryKeys(registry)));
    }

    @RequiredArgsConstructor
    static class RegistryArgParser<T extends Keyed> implements CustomArgumentInfoParser<T, NamespacedKey> {
        final Registry<T> registry;

        @Override
        public T apply(CustomArgumentInfo<NamespacedKey> info) throws CustomArgumentException {
            return Optional.ofNullable(registry.get(info.currentInput()))
                .orElseThrow(() -> CustomArgumentException.fromString("'" + info.currentInput() + "' is not a valid value!" +
                    " Possible values are:\n - " + String.join("\n - ", formatRegistryKeys(registry))));
        }
    }

    static List<String> formatRegistryKeys(Registry<?> registry) {
        return registry.stream()
            .map(Keyed::getKey)
            .map(NamespacedKey::asString)
            .toList();
    }
}
