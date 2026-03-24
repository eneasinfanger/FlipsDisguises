package ch.eneas.flipsdisguises.api;

import ch.eneas.flipsdisguises.disguise.application.DisguiseResult;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface FlipsDisguisesApi {

    CompletableFuture<DisguiseResult> disguisePlayerAsPlayer(Player target, String skinUsername);

    DisguiseResult undisguise(Player target);

    boolean isDisguised(UUID playerId);
}
