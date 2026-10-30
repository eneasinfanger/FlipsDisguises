package ch.eneas.flipsdisguises.disguise.application;

import ch.eneas.flipsdisguises.disguise.port.DisguiseStore;
import ch.eneas.flipsdisguises.disguise.port.MobDisguisePort;
import ch.eneas.flipsdisguises.disguise.port.PlayerAppearancePort;
import ch.eneas.flipsdisguises.disguise.port.SkinCatalog;
import ch.eneas.flipsdisguises.plugin.SyncTaskRunner;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisguiseServiceTest {

    @Mock
    private SyncTaskRunner syncTasks;
    @Mock
    private SkinCatalog skins;
    @Mock
    private PlayerAppearancePort appearance;
    @Mock
    private MobDisguisePort mobDisguise;
    @Mock
    private DisguiseStore store;
    @Mock
    private Player target;

    @BeforeEach
    void setUp() {
        lenient().when(syncTasks.rateLimitMillis()).thenReturn(0L);
        lenient().doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(syncTasks).runSync(any());
        lenient().when(mobDisguise.isMobDisguised(any())).thenReturn(false);
        lenient().when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        lenient().when(target.isOnline()).thenReturn(true);
    }

    @Test
    void undisguiseWithoutPriorStateFails() {
        DisguiseService svc = new DisguiseService(syncTasks, skins, appearance, mobDisguise, store);
        DisguiseResult r = svc.undisguise(target);
        assertInstanceOf(DisguiseResult.Failure.class, r);
        verifyNoInteractions(appearance);
    }

    @Test
    void rateLimitBlocksSecondCommand() {
        when(syncTasks.rateLimitMillis()).thenReturn(60_000L);
        when(skins.resolveUsername(any())).thenReturn(new CompletableFuture<>());
        DisguiseService svc = new DisguiseService(syncTasks, skins, appearance, mobDisguise, store);
        Player initiator = mock(Player.class);
        when(initiator.getUniqueId()).thenReturn(UUID.randomUUID());
        svc.disguiseAsNamedPlayer(initiator, target, "first");
        DisguiseResult second = svc.disguiseAsNamedPlayer(initiator, target, "second").join();
        assertInstanceOf(DisguiseResult.Failure.class, second);
    }
}
