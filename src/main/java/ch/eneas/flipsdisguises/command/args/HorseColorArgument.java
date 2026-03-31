package ch.eneas.flipsdisguises.command.args;

import org.bukkit.entity.Horse;

public class HorseColorArgument extends EnumArgument<Horse.Color> {
    public HorseColorArgument(String nodeName) {
        super(nodeName, Horse.Color.class);
    }
}
