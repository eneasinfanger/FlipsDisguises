package ch.eneas.flipsdisguises.command.args;

import org.bukkit.Registry;
import org.bukkit.entity.Villager;

public class VillagerTypeArgument extends RegistryArgument<Villager.Type> {
    public VillagerTypeArgument(String nodeName) {
        super(nodeName, Registry.VILLAGER_TYPE);
    }
}
