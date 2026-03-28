package ch.eneas.flipsdisguises.command;

import ch.eneas.Main;
import ch.eneas.flips.loader.api.CommandRegister;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.adapter.packet.MobDisguiseEntityMapping;
import ch.eneas.flipsdisguises.disguise.application.DisguiseResult;
import ch.eneas.flipsdisguises.disguise.application.DisguiseService;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.ExecutableCommand;
import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

@Injectable
public final class DisguiseCommands implements CommandRegister {

    private final Main plugin;
    private final DisguiseService disguiseService;

    @Inject
    public DisguiseCommands(Main plugin, DisguiseService disguiseService) {
        this.plugin = plugin;
        this.disguiseService = disguiseService;
    }

    @Override
    public Collection<? extends ExecutableCommand<?, CommandSender>> registerAll() {
        return List.of(buildDisguiseCommand(), buildUndisguiseCommand());
    }

    private static EntityType parseMobOrNull(String raw) {
        return DisguiseService.parseMobType(raw);
    }

    private CommandAPICommand buildMobSubcommand() {
        EntitySelectorArgument.OnePlayer targetArg = new EntitySelectorArgument.OnePlayer("target");
        Argument<String> mobArg = new StringArgument("mobType")
            .replaceSuggestions(ArgumentSuggestions.strings(info -> MobDisguiseEntityMapping.sortedSuggestionNames()));
        return new CommandAPICommand("mob")
            .withPermission("flipsdisguises.disguise.mob")
            .withArguments(targetArg, mobArg)
            .executes((CommandSender sender, CommandArguments args) -> {
                Player target = args.getOptionalUnchecked("target")
                    .map(Player.class::cast)
                    .orElseGet(() -> sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text(
                        "Specify a player target (console) or run as a player (self).",
                        NamedTextColor.RED
                    ));
                    return;
                }
                String raw = (String) args.getUnchecked("mobType");
                EntityType type = parseMobOrNull(raw);
                if (type == null) {
                    sender.sendMessage(Component.text("Unknown entity type name.", NamedTextColor.RED));
                    return;
                }
                if (!MobDisguiseEntityMapping.isSupported(type)) {
                    sender.sendMessage(Component.text(
                        "That entity cannot be used as a mob disguise (non-living, blocked, or unmapped).",
                        NamedTextColor.RED
                    ));
                    return;
                }
                disguiseService.disguiseAsMob(sender, target, type).thenAccept(result ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> reply(sender, result)));
            });
    }

    private CommandAPICommand buildDisguiseCommand() {
        return new CommandAPICommand("disguise")
            .withSubcommand(new CommandAPICommand("as")
                .withPermission("flipsdisguises.disguise.player")
                .withArguments(new StringArgument("playerName"))
                .executes((CommandSender sender, CommandArguments args) -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("Only players can use /disguise as.", NamedTextColor.RED));
                        return;
                    }
                    String name = (String) args.getUnchecked("playerName");
                    disguiseService.disguiseAsNamedPlayer(player, player, name).thenAccept(result ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> reply(player, result)));
                }))
            .withSubcommand(new CommandAPICommand("player")
                .withPermission("flipsdisguises.disguise.player")
                .withArguments(new EntitySelectorArgument.OnePlayer("target"), new StringArgument("playerName"))
                .executes((CommandSender sender, CommandArguments args) -> {
                    Player target = (Player) args.getUnchecked("target");
                    String skin = (String) args.getUnchecked("playerName");
                    disguiseService.disguiseAsNamedPlayer(sender, target, skin).thenAccept(result ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> reply(sender, result)));
                }))
            .withSubcommand(buildMobSubcommand())
            .executes((CommandSender sender, CommandArguments args) ->
                sender.sendMessage(Component.text(
                    "Usage: /disguise as <playerName> | /disguise player <target> <playerName> | /disguise mob [target] <mobType>",
                    NamedTextColor.YELLOW
                )));
    }

    private CommandAPICommand buildUndisguiseCommand() {
        EntitySelectorArgument.OnePlayer targetArg = new EntitySelectorArgument.OnePlayer("target");
        targetArg.setOptional(true);
        return new CommandAPICommand("undisguise")
            .withPermission("flipsdisguises.undisguise")
            .withArguments(targetArg)
            .executes((CommandSender sender, CommandArguments args) -> {
                Player target = args.getOptionalUnchecked("target")
                    .map(Player.class::cast)
                    .orElseGet(() -> sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Specify a player.", NamedTextColor.RED));
                    return;
                }
                reply(sender, disguiseService.undisguise(target));
            });
    }

    private static void reply(CommandSender sender, DisguiseResult result) {
        if (result instanceof DisguiseResult.Failure f) {
            sender.sendMessage(f.message());
        } else {
            sender.sendMessage(Component.text("Disguise applied.", NamedTextColor.GREEN));
        }
    }
}
