package ch.eneas.flipsdisguises.api;

import ch.eneas.flipsdisguises.disguise.application.DisguiseResult;
import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface FlipsDisguisesApi {

    CompletableFuture<DisguiseResult> disguisePlayerAsPlayer(Player target, String skinUsername);

    CompletableFuture<DisguiseResult> disguisePlayerAsMob(Player target, EntityType mobType);

    CompletableFuture<DisguiseResult> disguisePlayerAsMob(Player target, EntityType mobType, MobDisguiseOptions options);

    CompletableFuture<DisguiseResult> disguiseLivingAsMob(LivingEntity target, EntityType mobType);

    CompletableFuture<DisguiseResult> disguiseLivingAsMob(LivingEntity target, EntityType mobType, MobDisguiseOptions options);

    DisguiseResult undisguise(Player target);

    DisguiseResult undisguiseMob(LivingEntity target);

    boolean isDisguised(UUID playerId);

    boolean isMobModelDisguised(UUID entityUuid);
}
