package ch.eneas.flipsdisguises.command.args;

import net.kyori.adventure.util.TriState;

public class TriStateArgument extends EnumArgument<TriState> {
    public TriStateArgument(String nodeName) {
        super(nodeName, TriState.class);
    }
}
