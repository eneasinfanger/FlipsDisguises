package ch.eneas.flipsdisguises.command.args;

import org.bukkit.DyeColor;

public class DyeColorArgument extends EnumArgument<DyeColor> {
    public DyeColorArgument(String nodeName) {
        super(nodeName, DyeColor.class);
    }
}
