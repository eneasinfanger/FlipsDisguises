package ch.eneas.flipsdisguises.command.args.disgopts;

import ch.eneas.flips.loader.api.CommandRegister;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flips.loader.api.Service;
import com.mojang.brigadier.context.CommandContext;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.ExecutableCommand;
import dev.jorel.commandapi.arguments.CustomArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

@Slf4j
@Injectable
public class MobDisguiseOptionsCtxInitializer implements CommandRegister, Service {
    private static final String COMMAND_NAME = "disguiseoptsinit";

    @Override
    public ExecutableCommand<?, CommandSender> register() {
        return new CommandAPICommand(COMMAND_NAME)
            .withRequirement(ConsoleCommandSender.class::isInstance)
            .withArguments(new CaptureCtxArg("captured"))
            .executesConsole((sender, args) -> {
                Boolean captured = args.getUnchecked("captured");
                if (Boolean.TRUE.equals(captured)) {
                    log.debug("CommandCtx initialized!");
                    return 1;
                }
                log.warn("Initsopts triggered after init already happened!");
                return 0;
            });
    }

    private static class CaptureCtxArg extends CustomArgument<Boolean, String> {
        public CaptureCtxArg(String nodeName) {
            super(new GreedyStringArgument(nodeName), null);
        }

        @Override
        public Boolean parseArgument(CommandContext cmdCtx, String key, CommandArguments previousArgs) {
            if (MobDisguiseUtil.CTX_SOURCE_REF == null && cmdCtx != null && cmdCtx.getSource() != null) {
                MobDisguiseUtil.CTX_SOURCE_REF = cmdCtx.getSource();
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public void startup(JavaPlugin myPlugin) {
        myPlugin.getServer().getScheduler().runTask(myPlugin, () -> {
            String commandLine = COMMAND_NAME + " void 0";
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandLine)) {
                throw new IllegalStateException("Dispatch command failed: command not registered?");
            }
            if (MobDisguiseUtil.CTX_SOURCE_REF == null) {
                throw new IllegalStateException("Dispatch succeeded, but Context not initialized!");
            }
            CommandAPI.unregister(COMMAND_NAME);
            CommandAPI.unregister(myPlugin.namespace() + ":" + COMMAND_NAME);
            log.debug("Unregistered initopts command.");
        });
    }

    @Override
    public void shutdown(JavaPlugin myPlugin) {
        // noop
    }
}
