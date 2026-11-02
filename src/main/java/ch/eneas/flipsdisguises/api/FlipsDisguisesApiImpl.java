package ch.eneas.flipsdisguises.api;

import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.application.DisguiseResult;
import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.application.DisguiseService;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Injectable
public final class FlipsDisguisesApiImpl implements FlipsDisguisesApi {

    private final DisguiseService disguiseService;

    @Inject
    public FlipsDisguisesApiImpl(DisguiseService disguiseService) {
        this.disguiseService = disguiseService;
    }

    @Override
    public CompletableFuture<DisguiseResult> disguisePlayerAsPlayer(Player target, String skinUsername) {
        requireMainThread();
        return disguiseService.disguiseAsNamedPlayer(target, skinUsername);
    }

    @Override
    public DisguiseResult disguisePlayerAsMob(Player target, EntityType mobType) {
        return disguisePlayerAsMob(target, mobType, MobDisguiseOptions.DEFAULT);
    }

    @Override
    public DisguiseResult disguisePlayerAsMob(Player target, EntityType mobType, MobDisguiseOptions options) {
        requireMainThread();
        return disguiseService.disguiseAsMob(target, mobType, options);
    }

    @Override
    public DisguiseResult disguiseLivingAsMob(LivingEntity target, EntityType mobType) {
        return disguiseLivingAsMob(target, mobType, MobDisguiseOptions.DEFAULT);
    }

    @Override
    public DisguiseResult disguiseLivingAsMob(LivingEntity target, EntityType mobType, MobDisguiseOptions options) {
        requireMainThread();
        return disguiseService.disguiseLivingAsMob(target, mobType, options);
    }

    @Override
    public DisguiseResult undisguise(Player target) {
        requireMainThread();
        return disguiseService.undisguise(target);
    }

    @Override
    public DisguiseResult undisguiseMob(LivingEntity target) {
        requireMainThread();
        return disguiseService.undisguiseLivingMob(target);
    }

    @Override
    public boolean isDisguised(UUID playerId) {
        return disguiseService.isDisguised(playerId);
    }

    @Override
    public boolean isMobModelDisguised(UUID entityUuid) {
        return disguiseService.isMobModelDisguised(entityUuid);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                "FlipsDisguises must be called from the main server thread (current thread: "
                    + Thread.currentThread().getName() + ")"
            );
        }
    }
}
