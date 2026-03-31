package ch.eneas.flipsdisguises.command.args;

import org.bukkit.entity.Horse;

public class HorseStyleArgument extends EnumArgument<Horse.Style> {
    public HorseStyleArgument(String nodeName) {
        super(nodeName, Horse.Style.class);
    }
}
