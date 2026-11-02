package ch.eneas.flipsdisguises.api;

import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.application.DisguiseResult;
import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.domain.StoredDisguise;
import ch.eneas.flipsdisguises.disguise.application.DisguiseService;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Injectable
public final class FlipsDisguisesApiImpl implements FlipsDisguisesApi {

    private final DisguiseService disguiseService;

    @Inject
    public FlipsDisguisesApiImpl(DisguiseService disguiseService) {
        this.disguiseService = disguiseService;
    }

    @Override
    public CompletableFuture<DisguiseResult> disguiseAsPlayer(Player target, String skinUsername) {
        requireMainThread();
        return disguiseService.disguiseAsPlayer(target, skinUsername);
    }

    @Override
    public DisguiseResult disguiseAsMob(LivingEntity target, EntityType mobType) {
        return disguiseAsMob(target, mobType, MobDisguiseOptions.DEFAULT);
    }

    @Override
    public DisguiseResult disguiseAsMob(LivingEntity target, EntityType mobType, MobDisguiseOptions options) {
        requireMainThread();
        return disguiseService.disguiseAsMob(target, mobType, options);
    }

    @Override
    public DisguiseResult undisguise(LivingEntity target) {
        requireMainThread();
        return disguiseService.undisguise(target);
    }

    @Override
    public Optional<DisguiseInfo> getDisguise(LivingEntity target) {
        return disguiseService.activeDisguise(target).map(FlipsDisguisesApiImpl::toInfo);
    }

    @Override
    public boolean isDisguised(LivingEntity target) {
        return disguiseService.isDisguised(target);
    }

    private static DisguiseInfo toInfo(StoredDisguise disguise) {
        return switch (disguise.kind()) {
            case MOB -> new DisguiseInfo.MobDisguise(disguise.mobType(), disguise.options());
            case PLAYER -> new DisguiseInfo.PlayerDisguise(
                disguise.shownName(),
                disguise.skin() == null ? null : disguise.skin().getSkinSourceUuid(),
                disguise.playerOptions()
            );
        };
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
