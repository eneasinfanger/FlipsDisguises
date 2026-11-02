package ch.eneas.flipsdisguises.command;

import ch.eneas.PluginMain;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandRateLimiterTest {

    @Mock
    private PluginMain plugin;
    @Mock
    private FileConfiguration config;

    private CommandRateLimiter limiter;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(config.getLong(eq("rate-limit-millis"), anyLong())).thenReturn(60_000L);
        limiter = new CommandRateLimiter(plugin);
    }

    private static Player player() {
        Player p = mock(Player.class);
        lenient().when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        return p;
    }

    @Test
    void blocksSecondCommandFromSamePlayer() {
        Player p = player();
        assertTrue(limiter.tryAcquire(p));
        assertFalse(limiter.tryAcquire(p));
    }

    @Test
    void limitsPlayersIndependently() {
        assertTrue(limiter.tryAcquire(player()));
        assertTrue(limiter.tryAcquire(player()));
    }

    @Test
    void forgetAllowsNextCommand() {
        Player p = player();
        limiter.tryAcquire(p);
        limiter.forget(p.getUniqueId());
        assertTrue(limiter.tryAcquire(p));
    }

    @Test
    void zeroLimitDisablesThrottle() {
        when(config.getLong(eq("rate-limit-millis"), anyLong())).thenReturn(0L);
        Player p = player();
        assertTrue(limiter.tryAcquire(p));
        assertTrue(limiter.tryAcquire(p));
    }

    @Test
    void nonPlayerSendersAreNeverLimited() {
        CommandSender console = mock(CommandSender.class);
        assertTrue(limiter.tryAcquire(console));
        assertTrue(limiter.tryAcquire(console));
    }
}
