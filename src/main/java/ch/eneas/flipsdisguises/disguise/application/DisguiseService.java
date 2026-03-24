package ch.eneas.flipsdisguises.disguise.application;

import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.plugin.SyncTaskRunner;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import ch.eneas.flipsdisguises.disguise.port.PlayerAppearancePort;
import ch.eneas.flipsdisguises.disguise.port.SkinCatalog;
import ch.eneas.flipsdisguises.edition.ProductEdition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Injectable
public final class DisguiseService {

    private static final Logger LOG = LoggerFactory.getLogger(DisguiseService.class);

    private final SyncTaskRunner syncTasks;
    private final SkinCatalog skins;
    private final PlayerAppearancePort appearance;
    private final ProductEdition edition;
    private final Map<UUID, SkinSnapshot> originalsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActionByInitiator = new ConcurrentHashMap<>();

    @Inject
    public DisguiseService(
        SyncTaskRunner syncTasks,
        SkinCatalog skins,
        PlayerAppearancePort appearance,
        ProductEdition edition
    ) {
        this.syncTasks = syncTasks;
        this.skins = skins;
        this.appearance = appearance;
        this.edition = edition;
    }

    public boolean isDisguised(UUID playerId) {
        return originalsByPlayer.containsKey(playerId);
    }

    public void forget(UUID playerId) {
        originalsByPlayer.remove(playerId);
    }

    public DisguiseResult undisguise(Player target) {
        if (!edition.disguiseCommandsEnabled() && !edition.programmaticDisguiseApiEnabled()) {
            return DisguiseResult.failure(Component.text("Disguises are disabled.", NamedTextColor.RED));
        }
        SkinSnapshot original = originalsByPlayer.remove(target.getUniqueId());
        if (original == null) {
            return DisguiseResult.failure(Component.text("Nothing to restore.", NamedTextColor.GRAY));
        }
        appearance.applySkin(target, original);
        LOG.info("business.disguise_cleared player={}", target.getUniqueId());
        return DisguiseResult.ok();
    }

    public CompletableFuture<DisguiseResult> disguiseAsNamedPlayer(CommandSender initiator, Player target, String skinOwner) {
        if (!edition.disguiseCommandsEnabled()) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text("Disguise commands are disabled.", NamedTextColor.RED))
            );
        }
        if (initiator instanceof Player p && !rateAllowed(p.getUniqueId())) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text("Slow down.", NamedTextColor.RED))
            );
        }
        String owner = skinOwner.trim();
        if (owner.isEmpty()) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text("Skin name required.", NamedTextColor.RED))
            );
        }
        return skins.resolveUsername(owner).thenCompose(skin -> scheduleApply(target, skin)).exceptionally(ex -> {
            LOG.error("business.disguise_resolve_failed target={} skinOwner={}", target.getUniqueId(), owner, ex);
            return DisguiseResult.failure(Component.text("Could not resolve skin.", NamedTextColor.RED));
        });
    }

    public CompletableFuture<DisguiseResult> disguiseAsNamedPlayerApi(Player target, String skinOwner) {
        if (!edition.programmaticDisguiseApiEnabled()) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text("API disguises are disabled.", NamedTextColor.RED))
            );
        }
        String owner = skinOwner.trim();
        if (owner.isEmpty()) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text("Skin name required.", NamedTextColor.RED))
            );
        }
        return skins.resolveUsername(owner).thenCompose(skin -> scheduleApply(target, skin)).exceptionally(ex -> {
            LOG.error("business.disguise_resolve_failed_api target={} skinOwner={}", target.getUniqueId(), owner, ex);
            return DisguiseResult.failure(Component.text("Could not resolve skin.", NamedTextColor.RED));
        });
    }

    private CompletableFuture<DisguiseResult> scheduleApply(Player target, SkinSnapshot skin) {
        CompletableFuture<DisguiseResult> done = new CompletableFuture<>();
        syncTasks.runSync(() -> {
            try {
                if (!target.isOnline()) {
                    done.complete(DisguiseResult.failure(Component.text("Target went offline.", NamedTextColor.RED)));
                    return;
                }
                originalsByPlayer.putIfAbsent(target.getUniqueId(), appearance.capture(target));
                appearance.applySkin(target, skin);
                LOG.info("business.disguise_applied player={} skinProfile={}", target.getUniqueId(), skin.playerId());
                done.complete(DisguiseResult.ok());
            } catch (Exception ex) {
                LOG.error("business.disguise_apply_failed player={}", target.getUniqueId(), ex);
                done.complete(DisguiseResult.failure(Component.text("Could not apply disguise.", NamedTextColor.RED)));
            }
        });
        return done;
    }

    private boolean rateAllowed(UUID initiator) {
        long limit = syncTasks.rateLimitMillis();
        if (limit <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long prev = lastActionByInitiator.put(initiator, now);
        return prev == null || now - prev >= limit;
    }
}
