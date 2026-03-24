package ch.eneas.flipsdisguises.command;

import ch.eneas.Main;
import ch.eneas.flips.loader.api.CommandRegister;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.ExecutableCommand;
import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

@Injectable
public final class AdminCommands implements CommandRegister {

    private final Main plugin;

    @Inject
    public AdminCommands(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public Collection<? extends ExecutableCommand<?, CommandSender>> registerAll() {
        return List.of(new CommandAPICommand("flipsdisguises")
            .withAliases("fd")
            .withSubcommand(new CommandAPICommand("reload")
                .withPermission("flipsdisguises.admin")
                .executes((CommandSender sender, CommandArguments args) -> {
                    plugin.reloadConfig();
                    sender.sendMessage(Component.text("Configuration reloaded.", NamedTextColor.GREEN));
                })));
    }
}
