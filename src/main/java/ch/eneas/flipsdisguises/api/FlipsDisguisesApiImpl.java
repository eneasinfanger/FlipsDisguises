package ch.eneas.flipsdisguises.api;

import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.application.DisguiseResult;
import ch.eneas.flipsdisguises.disguise.application.DisguiseService;
import org.bukkit.entity.EntityType;
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
        return disguiseService.disguiseAsNamedPlayerApi(target, skinUsername);
    }

    @Override
    public CompletableFuture<DisguiseResult> disguisePlayerAsMob(Player target, EntityType mobType) {
        return disguiseService.disguiseAsMobApi(target, mobType);
    }

    @Override
    public DisguiseResult undisguise(Player target) {
        return disguiseService.undisguise(target);
    }

    @Override
    public boolean isDisguised(UUID playerId) {
        return disguiseService.isDisguised(playerId);
    }
}
