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
import org.bukkit.Bukkit;
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
    private final CommandRateLimiter rateLimiter;

    @Inject
    public DisguiseCommands(PluginMain plugin, DisguiseService disguiseService, CommandRateLimiter rateLimiter) {
        this.plugin = plugin;
        this.disguiseService = disguiseService;
        this.rateLimiter = rateLimiter;
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
                        "Usage: /disguise mob <target> <mobType> [--flags] (as console, target is required)",
                        NamedTextColor.YELLOW
                    ));
                    return;
                }
                EntityType type = args.getUnchecked("mobType");
                if (type == null) {
                    sender.sendMessage(Component.text(
                        "Unknown mob type. Usage: /disguise mob <target> <mobType> [--flags]",
                        NamedTextColor.YELLOW
                    ));
                    return;
                }
                if (!MobDisguiseEntityMapping.isSupported(type)) {
                    sender.sendMessage(Component.text(
                        "That entity cannot be used as a mob disguise (non-living, blocked, or unmapped).",
                        NamedTextColor.RED
                    ));
                    return;
                }
                if (!allowed(sender)) {
                    return;
                }
                MobDisguiseOptions opts = args.getUnchecked("mobFlags");
                reply(sender, disguiseService.disguiseAsMob(target, type, opts));
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
                    sender.sendMessage(Component.text(
                        "Unknown mob type. Usage: /disguise entity <entities> <mobType> [--flags]",
                        NamedTextColor.YELLOW
                    ));
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
                    sender.sendMessage(Component.text(
                        "No entities matched. Usage: /disguise entity <entities> <mobType> [--flags]",
                        NamedTextColor.YELLOW
                    ));
                    return;
                }
                if (!allowed(sender)) {
                    return;
                }
                int ok = 0;
                int fail = nonLiving;
                for (LivingEntity le : living) {
                    if (disguiseService.disguiseAsMob(le, type, opts) instanceof DisguiseResult.Ok) {
                        ok++;
                    } else {
                        fail++;
                    }
                }
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
    }

    private CommandAPICommand buildDisguiseCommand() {
        return new CommandAPICommand("disguise")
            .withSubcommand(buildPlayerSubcommand())
            .withSubcommand(buildMobSubcommand())
            .withSubcommand(buildEntityMobSubcommand())
            .executes((CommandSender sender, CommandArguments args) ->
                sendUsage(sender));
    }

    /**
     * Single player-disguise entry point: {@code /disguise player <name>} disguises yourself,
     * {@code /disguise player <target> <name>} disguises another player. One code path for both.
     */
    private CommandAPICommand buildPlayerSubcommand() {
        return new CommandAPICommand("player")
            .withPermission("flipsdisguises.disguise.player")
            .withArguments(new GreedyStringArgument("args"))
            .executes((CommandSender sender, CommandArguments args) -> {
                String raw = ((String) args.getUnchecked("args")).trim();
                String[] tokens = raw.split("\\s+");
                Player target;
                String skinName;
                if (tokens.length == 1) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(Component.text(
                            "Console must specify a target: /disguise player <target> <name>",
                            NamedTextColor.RED
                        ));
                        return;
                    }
                    target = p;
                    skinName = tokens[0];
                } else if (tokens.length == 2) {
                    target = Bukkit.getPlayerExact(tokens[0]);
                    if (target == null) {
                        sender.sendMessage(Component.text(
                            "Player '" + tokens[0] + "' is not online.",
                            NamedTextColor.RED
                        ));
                        return;
                    }
                    skinName = tokens[1];
                } else {
                    sender.sendMessage(Component.text(
                        "Usage: /disguise player <name> or /disguise player <target> <name>",
                        NamedTextColor.YELLOW
                    ));
                    return;
                }
                if (!allowed(sender)) {
                    return;
                }
                disguiseService.disguiseAsPlayer(target, skinName).thenAccept(result ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> reply(sender, result)));
            });
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Disguise usage:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  /disguise player <name> — disguise yourself as a player", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /disguise player <target> <name> — disguise another player", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /disguise mob <target> <mobType> [--flags] — disguise a player as a mob", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /disguise entity <entities> <mobType> [--flags] — disguise entities as a mob", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /undisguise [target] — remove a player disguise", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /undisguise entity <entities> — remove entity disguises", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
            "Flags: --size <1-127> (slime/magma/phantom), --name <MiniMessage>, --variant <TYPE_SPECIFIC>",
            NamedTextColor.DARK_GRAY
        ));
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
                        if (e instanceof LivingEntity le
                            && disguiseService.undisguise(le) instanceof DisguiseResult.Ok) {
                            ok++;
                        } else {
                            skip++;
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
                    sender.sendMessage(Component.text(
                        "Usage: /undisguise [target] or /undisguise entity <entities>",
                        NamedTextColor.YELLOW
                    ));
                    return;
                }
                reply(sender, disguiseService.undisguise(target));
            });
    }

    private boolean allowed(CommandSender sender) {
        if (rateLimiter.tryAcquire(sender)) {
            return true;
        }
        sender.sendMessage(Component.text("Slow down.", NamedTextColor.RED));
        return false;
    }

    private static void reply(CommandSender sender, DisguiseResult result) {
        if (result instanceof DisguiseResult.Failure f) {
            sender.sendMessage(f.message());
        } else {
            sender.sendMessage(Component.text("Disguise applied.", NamedTextColor.GREEN));
        }
    }
}
