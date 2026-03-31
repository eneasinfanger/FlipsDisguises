package ch.eneas.flipsdisguises.command.args;

import org.bukkit.Material;

public class MaterialArgument extends EnumArgument<Material> {
    public MaterialArgument(String nodeName) {
        super(nodeName, Material.class);
    }
}
