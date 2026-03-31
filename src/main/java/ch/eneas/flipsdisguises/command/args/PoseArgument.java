package ch.eneas.flipsdisguises.command.args;

import org.bukkit.entity.Pose;

public class PoseArgument extends EnumArgument<Pose> {
    public PoseArgument(String nodeName) {
        super(nodeName, Pose.class);
    }
}
