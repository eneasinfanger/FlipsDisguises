package ch.eneas.flipsdisguises.disguise.application;

import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.domain.PlayerLookBackup;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import ch.eneas.flipsdisguises.disguise.port.MobDisguisePort;
import ch.eneas.flipsdisguises.disguise.port.PlayerAppearancePort;
import ch.eneas.flipsdisguises.disguise.port.SkinCatalog;
import ch.eneas.flipsdisguises.edition.ProductEdition;
import ch.eneas.flipsdisguises.plugin.SyncTaskRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Injectable
public final class DisguiseService {

    private static final Logger LOG = LoggerFactory.getLogger(DisguiseService.class);
    private static final EnumSet<EntityType> MOB_MVP = EnumSet.of(
        EntityType.COW,
        EntityType.PIG,
        EntityType.CHICKEN,
        EntityType.SHEEP
    );

    private final SyncTaskRunner syncTasks;
    private final SkinCatalog skins;
    private final PlayerAppearancePort appearance;
    private final MobDisguisePort mobDisguise;
    private final ProductEdition edition;
    private final Map<UUID, PlayerLookBackup> originalsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActionByInitiator = new ConcurrentHashMap<>();

    @Inject
    public DisguiseService(
        SyncTaskRunner syncTasks,
        SkinCatalog skins,
        PlayerAppearancePort appearance,
        MobDisguisePort mobDisguise,
        ProductEdition edition
    ) {
        this.syncTasks = syncTasks;
        this.skins = skins;
        this.appearance = appearance;
        this.mobDisguise = mobDisguise;
        this.edition = edition;
    }

    public boolean isDisguised(UUID playerId) {
        return originalsByPlayer.containsKey(playerId);
    }

    public void forget(UUID playerId) {
        originalsByPlayer.remove(playerId);
        mobDisguise.detachDisconnected(playerId);
    }

    public DisguiseResult undisguise(Player target) {
        if (!edition.disguiseCommandsEnabled() && !edition.programmaticDisguiseApiEnabled()) {
            return DisguiseResult.failure(Component.text("Disguises are disabled.", NamedTextColor.RED));
        }
        PlayerLookBackup original = originalsByPlayer.remove(target.getUniqueId());
        if (original == null) {
            return DisguiseResult.failure(Component.text("Nothing to restore.", NamedTextColor.GRAY));
        }
        if (mobDisguise.isMobDisguised(target.getUniqueId())) {
            mobDisguise.detach(target);
        }
        appearance.restore(target, original, true);
        LOG.info("Disguise removed for {}", target.getUniqueId());
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
                DisguiseResult.failure(Component.text("Player name required.", NamedTextColor.RED))
            );
        }
        return skins.resolveUsername(owner).thenCompose(skin -> scheduleApplyPlayer(target, skin, owner)).exceptionally(ex -> {
            LOG.error("Failed to resolve skin profile '{}' for {}", owner, target.getUniqueId(), ex);
            return DisguiseResult.failure(Component.text("Could not resolve that player.", NamedTextColor.RED));
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
                DisguiseResult.failure(Component.text("Player name required.", NamedTextColor.RED))
            );
        }
        return skins.resolveUsername(owner).thenCompose(skin -> scheduleApplyPlayer(target, skin, owner)).exceptionally(ex -> {
            LOG.error("Failed to resolve skin profile '{}' for {}", owner, target.getUniqueId(), ex);
            return DisguiseResult.failure(Component.text("Could not resolve that player.", NamedTextColor.RED));
        });
    }

    public CompletableFuture<DisguiseResult> disguiseAsMob(CommandSender initiator, Player target, EntityType type) {
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
        if (!MOB_MVP.contains(type)) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text("That mob type is not supported yet.", NamedTextColor.RED))
            );
        }
        CompletableFuture<DisguiseResult> done = new CompletableFuture<>();
        syncTasks.runSync(() -> {
            try {
                if (!target.isOnline()) {
                    done.complete(DisguiseResult.failure(Component.text("Target went offline.", NamedTextColor.RED)));
                    return;
                }
                prepareSubjectForNewDisguise(target, DisguiseSwitchMode.TO_MOB);
                appearance.applyMobLabels(target, type);
                mobDisguise.attach(target, type);
                LOG.info("Applied mob disguise for {} as {}", target.getUniqueId(), type);
                done.complete(DisguiseResult.ok());
            } catch (Exception ex) {
                LOG.error("Failed mob disguise for {}", target.getUniqueId(), ex);
                done.complete(DisguiseResult.failure(Component.text("Could not apply mob disguise.", NamedTextColor.RED)));
            }
        });
        return done;
    }

    public CompletableFuture<DisguiseResult> disguiseAsMobApi(Player target, EntityType type) {
        if (!edition.programmaticDisguiseApiEnabled()) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text("API disguises are disabled.", NamedTextColor.RED))
            );
        }
        if (!MOB_MVP.contains(type)) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text("That mob type is not supported yet.", NamedTextColor.RED))
            );
        }
        CompletableFuture<DisguiseResult> done = new CompletableFuture<>();
        syncTasks.runSync(() -> {
            try {
                if (!target.isOnline()) {
                    done.complete(DisguiseResult.failure(Component.text("Target went offline.", NamedTextColor.RED)));
                    return;
                }
                prepareSubjectForNewDisguise(target, DisguiseSwitchMode.TO_MOB);
                appearance.applyMobLabels(target, type);
                mobDisguise.attach(target, type);
                LOG.info("Applied mob disguise (API) for {} as {}", target.getUniqueId(), type);
                done.complete(DisguiseResult.ok());
            } catch (Exception ex) {
                LOG.error("Failed mob disguise for {}", target.getUniqueId(), ex);
                done.complete(DisguiseResult.failure(Component.text("Could not apply mob disguise.", NamedTextColor.RED)));
            }
        });
        return done;
    }

    public static EntityType parseMobType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(raw.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private CompletableFuture<DisguiseResult> scheduleApplyPlayer(Player target, SkinSnapshot skin, String requestedName) {
        CompletableFuture<DisguiseResult> done = new CompletableFuture<>();
        syncTasks.runSync(() -> {
            try {
                if (!target.isOnline()) {
                    done.complete(DisguiseResult.failure(Component.text("Target went offline.", NamedTextColor.RED)));
                    return;
                }
                prepareSubjectForNewDisguise(target, DisguiseSwitchMode.TO_PLAYER);
                String shown = skin.getDisplayName().isBlank() ? requestedName.trim() : skin.getDisplayName();
                appearance.applyImpersonation(target, skin, shown);
                LOG.info(
                    "Applied disguise for {} as '{}' (skin source {})",
                    target.getUniqueId(),
                    shown,
                    skin.getSkinSourceUuid()
                );
                done.complete(DisguiseResult.ok());
            } catch (Exception ex) {
                LOG.error("Failed applying disguise for {}", target.getUniqueId(), ex);
                done.complete(DisguiseResult.failure(Component.text("Could not apply disguise.", NamedTextColor.RED)));
            }
        });
        return done;
    }

    private void prepareSubjectForNewDisguise(Player target, DisguiseSwitchMode mode) {
        boolean wasMob = mobDisguise.isMobDisguised(target.getUniqueId());
        if (wasMob && mode == DisguiseSwitchMode.TO_MOB) {
            mobDisguise.detachDecoyOnly(target);
        } else if (wasMob && mode == DisguiseSwitchMode.TO_PLAYER) {
            mobDisguise.detach(target);
        }

        PlayerLookBackup existing = originalsByPlayer.get(target.getUniqueId());
        if (existing != null) {
            boolean resend = !(wasMob && mode == DisguiseSwitchMode.TO_MOB);
            appearance.restore(target, existing, resend);
            return;
        }
        PlayerLookBackup captured = appearance.capture(target);
        originalsByPlayer.put(target.getUniqueId(), captured);
    }

    private boolean rateAllowed(UUID initiator) {
        long limit = syncTasks.rateLimitMillis();
        if (limit <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long prev = lastActionByInitiator.get(initiator);
        if (prev != null && now - prev < limit) {
            return false;
        }
        lastActionByInitiator.put(initiator, now);
        return true;
    }

    private enum DisguiseSwitchMode {
        TO_PLAYER,
        TO_MOB
    }
}
