package ch.eneas.flipsdisguises.command;

import ch.eneas.PluginMain;
import ch.eneas.flips.loader.api.CommandRegister;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.command.args.disgopts.MobDisguiseOptionsArgument;
import ch.eneas.flipsdisguises.disguise.adapter.packet.MobDisguiseEntityMapping;
import ch.eneas.flipsdisguises.disguise.application.DisguiseResult;
import ch.eneas.flipsdisguises.disguise.application.DisguiseService;
import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.*;
import dev.jorel.commandapi.ExecutableCommand;
import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Injectable
public final class DisguiseCommands implements CommandRegister {
    private final PluginMain plugin;
    private final DisguiseService disguiseService;

    @Inject
    public DisguiseCommands(PluginMain plugin, DisguiseService disguiseService) {
        this.plugin = plugin;
        this.disguiseService = disguiseService;
    }

    @Override
    public Collection<? extends ExecutableCommand<?, CommandSender>> registerAll() {
        return List.of(buildDisguiseCommand(), buildUndisguiseCommand());
    }

    private CommandAPICommand buildMobSubcommand() {
        EntitySelectorArgument.OnePlayer targetArg = new EntitySelectorArgument.OnePlayer("target");
        Argument<EntityType> mobArg = new EntityTypeArgument("mobType");
        Argument<MobDisguiseOptions> flagArg = new MobDisguiseOptionsArgument("mobFlags", "mobType");
        flagArg.setOptional(true);
        return new CommandAPICommand("mob")
            .withPermission("flipsdisguises.disguise.mob")
            .withArguments(targetArg, mobArg, flagArg)
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
                EntityType type = args.getUnchecked("mobType");
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
                MobDisguiseOptions opts = args.getUnchecked("mobFlags");
                disguiseService.disguiseAsMob(sender, target, type, opts).thenAccept(result ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> reply(sender, result)));
            });
    }

    private CommandAPICommand buildEntityMobSubcommand() {
        EntitySelectorArgument.ManyEntities entitiesArg = new EntitySelectorArgument.ManyEntities("entities");
        Argument<EntityType> mobArg = new EntityTypeArgument("mobType");
        Argument<MobDisguiseOptions> flagArg = new MobDisguiseOptionsArgument("mobFlags", "mobType");
        flagArg.setOptional(true);
        return new CommandAPICommand("entity")
            .withPermission("flipsdisguises.disguise.entity")
            .withArguments(entitiesArg, mobArg, flagArg)
            .executes((CommandSender sender, CommandArguments args) -> {
                @SuppressWarnings("unchecked")
                Collection<Entity> selected = (Collection<Entity>) args.getUnchecked("entities");
                EntityType type = args.getUnchecked("mobType");
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
                MobDisguiseOptions opts = args.getUnchecked("mobFlags");
                List<LivingEntity> living = new ArrayList<>();
                int nonLiving = 0;
                for (Entity e : selected) {
                    if (e instanceof LivingEntity le && !(le instanceof Player)) {
                        living.add(le);
                    } else {
                        nonLiving++;
                    }
                }
                if (living.isEmpty() && nonLiving == 0) {
                    sender.sendMessage(Component.text("No entities matched.", NamedTextColor.RED));
                    return;
                }
                final int skippedSelections = nonLiving;
                disguiseService.disguiseNonPlayerLivingMobsBatch(sender, living, type, opts).thenAccept(counts -> {
                    int ok = counts[0];
                    int fail = counts[1] + skippedSelections;
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (ok > 0) {
                            sender.sendMessage(Component.text(
                                "Applied mob disguise to " + ok + " entit" + (ok == 1 ? "y" : "ies") + ".",
                                NamedTextColor.GREEN
                            ));
                        }
                        if (fail > 0) {
                            sender.sendMessage(Component.text(
                                fail + " selection(s) skipped or failed.",
                                NamedTextColor.YELLOW
                            ));
                        }
                    });
                });
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
            .withSubcommand(buildEntityMobSubcommand())
            .executes((CommandSender sender, CommandArguments args) ->
                sender.sendMessage(Component.text(
                    "Usage: /disguise as <playerName> | /disguise player <target> <playerName> | /disguise mob <target> <mobType> [--flags] | /disguise entity <entities> <mobType> [--flags]. Flags: --size <1-127> (slime/magma/phantom), --name <MiniMessage>, --variant <TYPE_SPECIFIC>",
                    NamedTextColor.YELLOW
                )));
    }

    private CommandAPICommand buildUndisguiseCommand() {
        EntitySelectorArgument.OnePlayer targetArg = new EntitySelectorArgument.OnePlayer("target");
        targetArg.setOptional(true);
        return new CommandAPICommand("undisguise")
            .withPermission("flipsdisguises.undisguise")
            .withArguments(targetArg)
            .withSubcommand(new CommandAPICommand("entity")
                .withPermission("flipsdisguises.undisguise")
                .withArguments(new EntitySelectorArgument.ManyEntities("entities"))
                .executes((CommandSender sender, CommandArguments args) -> {
                    @SuppressWarnings("unchecked")
                    Collection<Entity> selected = (Collection<Entity>) args.getUnchecked("entities");
                    int ok = 0;
                    int skip = 0;
                    for (Entity e : selected) {
                        DisguiseResult r;
                        if (e instanceof Player p) {
                            r = disguiseService.undisguise(p);
                        } else if (e instanceof LivingEntity le) {
                            r = disguiseService.undisguiseLivingMob(le);
                        } else {
                            skip++;
                            continue;
                        }
                        if (r instanceof DisguiseResult.Failure) {
                            skip++;
                        } else {
                            ok++;
                        }
                    }
                    if (ok == 0 && skip == 0) {
                        sender.sendMessage(Component.text("No entities matched.", NamedTextColor.RED));
                        return;
                    }
                    if (ok > 0) {
                        sender.sendMessage(Component.text(
                            "Removed mob disguise from " + ok + " entit" + (ok == 1 ? "y" : "ies") + ".",
                            NamedTextColor.GREEN
                        ));
                    }
                    if (skip > 0) {
                        sender.sendMessage(Component.text(
                            skip + " selection(s) had no mob disguise or were not living entities.",
                            NamedTextColor.YELLOW
                        ));
                    }
                }))
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
