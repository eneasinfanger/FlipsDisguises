package ch.eneas.flipsdisguises.command.args;

import org.bukkit.entity.Axolotl;

public class AxolotlVariantArgument extends EnumArgument<Axolotl.Variant> {
    public AxolotlVariantArgument(String nodeName) {
        super(nodeName, Axolotl.Variant.class);
    }
}
