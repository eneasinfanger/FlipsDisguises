package ch.eneas.flipsdisguises.disguise.application;

import ch.eneas.flipsdisguises.disguise.port.DisguiseStore;
import ch.eneas.flipsdisguises.disguise.port.MobDisguisePort;
import ch.eneas.flipsdisguises.disguise.port.PlayerAppearancePort;
import ch.eneas.flipsdisguises.disguise.port.SkinCatalog;
import ch.eneas.flipsdisguises.plugin.SyncTaskRunner;
import ch.eneas.flipsdisguises.disguise.domain.PlayerDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import ch.eneas.flipsdisguises.disguise.domain.StoredDisguise;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void activeDisguiseOfUndisguisedPlayerIsEmpty() {
        DisguiseService svc = new DisguiseService(syncTasks, skins, appearance, mobDisguise, store);
        assertTrue(svc.activeDisguise(target).isEmpty());
        assertFalse(svc.isDisguised(target));
    }

    @Test
    void activeDisguiseOfNonPlayerComesFromMobPort() {
        UUID id = UUID.randomUUID();
        LivingEntity cow = mock(LivingEntity.class);
        when(cow.getUniqueId()).thenReturn(id);
        StoredDisguise stored = StoredDisguise.mob(EntityType.PIG, null, null);
        when(mobDisguise.mobDisguiseOf(id)).thenReturn(Optional.of(stored));
        DisguiseService svc = new DisguiseService(syncTasks, skins, appearance, mobDisguise, store);
        assertEquals(Optional.of(stored), svc.activeDisguise(cow));
        assertTrue(svc.isDisguised(cow));
    }

    @Test
    void undisguiseOfUndisguisedNonPlayerFails() {
        LivingEntity cow = mock(LivingEntity.class);
        when(cow.getUniqueId()).thenReturn(UUID.randomUUID());
        DisguiseService svc = new DisguiseService(syncTasks, skins, appearance, mobDisguise, store);
        assertInstanceOf(DisguiseResult.Failure.class, svc.undisguise(cow));
        verifyNoInteractions(appearance);
    }

    @Test
    void disguiseAsMobFailsForOfflinePlayer() {
        when(target.isOnline()).thenReturn(false);
        DisguiseService svc = new DisguiseService(syncTasks, skins, appearance, mobDisguise, store);
        DisguiseResult r = svc.disguiseAsMob(target, EntityType.ZOMBIE, null);
        assertInstanceOf(DisguiseResult.Failure.class, r);
        verifyNoInteractions(appearance, mobDisguise, store);
    }

    @Test
    void disguiseAsMobFailsForInvalidNonPlayer() {
        LivingEntity cow = mock(LivingEntity.class);
        when(cow.isValid()).thenReturn(false);
        DisguiseService svc = new DisguiseService(syncTasks, skins, appearance, mobDisguise, store);
        DisguiseResult r = svc.disguiseAsMob(cow, EntityType.ZOMBIE, null);
        assertInstanceOf(DisguiseResult.Failure.class, r);
        verifyNoInteractions(appearance, mobDisguise, store);
    }

    @Test
    void disguiseAsPlayerWithBlankNameFails() {
        DisguiseService svc = new DisguiseService(syncTasks, skins, appearance, mobDisguise, store);
        DisguiseResult r = svc.disguiseAsPlayer(target, "  ").join();
        assertInstanceOf(DisguiseResult.Failure.class, r);
        verifyNoInteractions(skins);
    }

    @Test
    void disguiseNonPlayerAsPlayerAttachesPlayerModelAndPersists() {
        LivingEntity zombie = mock(LivingEntity.class);
        when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
        when(zombie.isValid()).thenReturn(true);
        SkinSnapshot skin = new SkinSnapshot(UUID.randomUUID(), "Notch", List.of());
        when(skins.resolveUsername("Notch")).thenReturn(CompletableFuture.completedFuture(skin));
        PlayerDisguiseOptions options = PlayerDisguiseOptions.DEFAULT.withGlowing(true);
        DisguiseService svc = new DisguiseService(syncTasks, skins, appearance, mobDisguise, store);

        DisguiseResult r = svc.disguiseAsPlayer(zombie, " Notch ", options).join();

        assertInstanceOf(DisguiseResult.Ok.class, r);
        verify(mobDisguise).attachPlayerModel(zombie, skin, "Notch", options);
        verify(store).save(zombie, StoredDisguise.player(skin, "Notch", options, null));
        verifyNoInteractions(appearance);
    }

    @Test
    void disguiseInvalidNonPlayerAsPlayerFails() {
        LivingEntity zombie = mock(LivingEntity.class);
        when(zombie.isValid()).thenReturn(false);
        when(skins.resolveUsername("Notch")).thenReturn(
            CompletableFuture.completedFuture(new SkinSnapshot(UUID.randomUUID(), "Notch", List.of()))
        );
        DisguiseService svc = new DisguiseService(syncTasks, skins, appearance, mobDisguise, store);

        DisguiseResult r = svc.disguiseAsPlayer(zombie, "Notch", null).join();

        assertInstanceOf(DisguiseResult.Failure.class, r);
        verifyNoInteractions(appearance, store);
    }

    @Test
    void restorePersistedPlayerModelReattachesIt() {
        LivingEntity zombie = mock(LivingEntity.class);
        when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
        when(zombie.isValid()).thenReturn(true);
        SkinSnapshot skin = new SkinSnapshot(UUID.randomUUID(), "Notch", List.of());
        PlayerDisguiseOptions options = PlayerDisguiseOptions.DEFAULT.withSize(2.0);
        when(store.load(zombie)).thenReturn(Optional.of(StoredDisguise.player(skin, "Notch", options, null)));
        DisguiseService svc = new DisguiseService(syncTasks, skins, appearance, mobDisguise, store);

        svc.restorePersistedLiving(zombie);

        verify(mobDisguise).attachPlayerModel(zombie, skin, "Notch", options);
    }
}
