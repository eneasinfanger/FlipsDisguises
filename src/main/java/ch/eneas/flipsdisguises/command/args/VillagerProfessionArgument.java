package ch.eneas.flipsdisguises.command.args;

import org.bukkit.Registry;
import org.bukkit.entity.Villager;

public class VillagerProfessionArgument extends RegistryArgument<Villager.Profession> {
    public VillagerProfessionArgument(String nodeName) {
        super(nodeName, Registry.VILLAGER_PROFESSION);
    }
}
