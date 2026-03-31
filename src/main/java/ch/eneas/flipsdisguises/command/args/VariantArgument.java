package ch.eneas.flipsdisguises.command.args;

import io.papermc.paper.registry.RegistryKey;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bukkit.entity.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VariantArgument {
    public static class CowVariant extends RegistryArgument<Cow.Variant> {
        public CowVariant(String nodeName) {
            super(nodeName, RegistryKey.COW_VARIANT);
        }
    }

    public static class CatVariant extends RegistryArgument<Cat.Type> {
        public CatVariant(String nodeName) {
            super(nodeName, RegistryKey.CAT_VARIANT);
        }
    }

    public static class FrogVariant extends RegistryArgument<Frog.Variant> {
        public FrogVariant(String nodeName) {
            super(nodeName, RegistryKey.FROG_VARIANT);
        }
    }

    public static class PigVariant extends RegistryArgument<Pig.Variant> {
        public PigVariant(String nodeName) {
            super(nodeName, RegistryKey.PIG_VARIANT);
        }
    }

    public static class ChickenVariant extends RegistryArgument<Chicken.Variant> {
        public ChickenVariant(String nodeName) {
            super(nodeName, RegistryKey.CHICKEN_VARIANT);
        }
    }

    public static class WolfVariant extends RegistryArgument<Wolf.Variant> {
        public WolfVariant(String nodeName) {
            super(nodeName, RegistryKey.WOLF_VARIANT);
        }
    }
}
