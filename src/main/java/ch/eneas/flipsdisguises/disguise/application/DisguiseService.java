package ch.eneas.flipsdisguises.disguise.application;

import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.domain.PlayerLookBackup;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import ch.eneas.flipsdisguises.disguise.adapter.packet.MobDisguiseEntityMapping;
import ch.eneas.flipsdisguises.disguise.port.MobDisguisePort;
import ch.eneas.flipsdisguises.disguise.port.PlayerAppearancePort;
import ch.eneas.flipsdisguises.disguise.port.SkinCatalog;
import ch.eneas.flipsdisguises.plugin.SyncTaskRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
    private final MobDisguisePort mobDisguise;
    private final Map<UUID, PlayerLookBackup> originalsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActionByInitiator = new ConcurrentHashMap<>();

    @Inject
    public DisguiseService(
        SyncTaskRunner syncTasks,
        SkinCatalog skins,
        PlayerAppearancePort appearance,
        MobDisguisePort mobDisguise
    ) {
        this.syncTasks = syncTasks;
        this.skins = skins;
        this.appearance = appearance;
        this.mobDisguise = mobDisguise;
    }

    public boolean isDisguised(UUID playerId) {
        return originalsByPlayer.containsKey(playerId);
    }

    public boolean isMobModelDisguised(UUID entityUuid) {
        return mobDisguise.isMobDisguised(entityUuid);
    }

    public void forget(UUID playerId) {
        originalsByPlayer.remove(playerId);
        mobDisguise.detachDisconnected(playerId);
        mobDisguise.clearSpoofedPlayerName(playerId);
    }

    public DisguiseResult undisguise(Player target) {
        PlayerLookBackup original = originalsByPlayer.remove(target.getUniqueId());
        if (original == null) {
            return DisguiseResult.failure(Component.text("Nothing to restore.", NamedTextColor.GRAY));
        }
        if (mobDisguise.isMobDisguised(target.getUniqueId())) {
            mobDisguise.detach((LivingEntity) target);
        }
        mobDisguise.clearSpoofedPlayerName(target.getUniqueId());
        appearance.restore(target, original, true);
        LOG.info("Disguise removed for {}", target.getUniqueId());
        return DisguiseResult.ok();
    }

    public CompletableFuture<DisguiseResult> disguiseAsNamedPlayer(CommandSender initiator, Player target, String skinOwner) {
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
        return disguiseAsMob(initiator, target, type, MobDisguiseOptions.DEFAULT);
    }

    public CompletableFuture<DisguiseResult> disguiseAsMob(
        CommandSender initiator,
        Player target,
        EntityType type,
        MobDisguiseOptions options
    ) {
        if (initiator instanceof Player p && !rateAllowed(p.getUniqueId())) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text("Slow down.", NamedTextColor.RED))
            );
        }
        if (!MobDisguiseEntityMapping.isSupported(type)) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text(
                    "That entity type cannot be used as a mob disguise (non-living, blocked, or unmapped).",
                    NamedTextColor.RED
                ))
            );
        }
        MobDisguiseOptions opts = options == null ? MobDisguiseOptions.DEFAULT : options;
        CompletableFuture<DisguiseResult> done = new CompletableFuture<>();
        syncTasks.runSync(() -> {
            try {
                if (!target.isOnline()) {
                    done.complete(DisguiseResult.failure(Component.text("Target went offline.", NamedTextColor.RED)));
                    return;
                }
                prepareSubjectForNewDisguise(target, DisguiseSwitchMode.TO_MOB);
                if (opts.displayName() != null) {
                    appearance.applyMobLabels(target, type, opts.displayName());
                } else {
                    appearance.applyMobLabels(target, type);
                }
                mobDisguise.attach((LivingEntity) target, type, opts);
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
        return disguiseAsMobApi(target, type, MobDisguiseOptions.DEFAULT);
    }

    public CompletableFuture<DisguiseResult> disguiseAsMobApi(Player target, EntityType type, MobDisguiseOptions options) {
        if (!MobDisguiseEntityMapping.isSupported(type)) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text(
                    "That entity type cannot be used as a mob disguise (non-living, blocked, or unmapped).",
                    NamedTextColor.RED
                ))
            );
        }
        MobDisguiseOptions opts = options == null ? MobDisguiseOptions.DEFAULT : options;
        CompletableFuture<DisguiseResult> done = new CompletableFuture<>();
        syncTasks.runSync(() -> {
            try {
                if (!target.isOnline()) {
                    done.complete(DisguiseResult.failure(Component.text("Target went offline.", NamedTextColor.RED)));
                    return;
                }
                prepareSubjectForNewDisguise(target, DisguiseSwitchMode.TO_MOB);
                if (opts.displayName() != null) {
                    appearance.applyMobLabels(target, type, opts.displayName());
                } else {
                    appearance.applyMobLabels(target, type);
                }
                mobDisguise.attach((LivingEntity) target, type, opts);
                LOG.info("Applied mob disguise (API) for {} as {}", target.getUniqueId(), type);
                done.complete(DisguiseResult.ok());
            } catch (Exception ex) {
                LOG.error("Failed mob disguise for {}", target.getUniqueId(), ex);
                done.complete(DisguiseResult.failure(Component.text("Could not apply mob disguise.", NamedTextColor.RED)));
            }
        });
        return done;
    }

    public CompletableFuture<DisguiseResult> disguiseLivingAsMob(CommandSender initiator, LivingEntity target, EntityType type) {
        return disguiseLivingAsMob(initiator, target, type, MobDisguiseOptions.DEFAULT);
    }

    public CompletableFuture<DisguiseResult> disguiseLivingAsMob(
        CommandSender initiator,
        LivingEntity target,
        EntityType type,
        MobDisguiseOptions options
    ) {
        if (initiator instanceof Player p && !rateAllowed(p.getUniqueId())) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text("Slow down.", NamedTextColor.RED))
            );
        }
        if (!MobDisguiseEntityMapping.isSupported(type)) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text(
                    "That entity type cannot be used as a mob disguise (non-living, blocked, or unmapped).",
                    NamedTextColor.RED
                ))
            );
        }
        if (target instanceof Player) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text("Use /disguise mob for players.", NamedTextColor.RED))
            );
        }
        MobDisguiseOptions opts = options == null ? MobDisguiseOptions.DEFAULT : options;
        CompletableFuture<DisguiseResult> done = new CompletableFuture<>();
        syncTasks.runSync(() -> {
            try {
                if (!target.isValid()) {
                    done.complete(DisguiseResult.failure(Component.text("That entity is no longer valid.", NamedTextColor.RED)));
                    return;
                }
                mobDisguise.attach(target, type, opts);
                LOG.info("Applied entity mob disguise for {} ({}) as {}", target.getUniqueId(), target.getType(), type);
                done.complete(DisguiseResult.ok());
            } catch (Exception ex) {
                LOG.error("Failed entity mob disguise for {}", target.getUniqueId(), ex);
                done.complete(DisguiseResult.failure(Component.text("Could not apply mob disguise to that entity.", NamedTextColor.RED)));
            }
        });
        return done;
    }

    /**
     * Applies the same mob model to several non-player living entities in one server tick (avoids async deadlocks from the command thread).
     */
    public CompletableFuture<int[]> disguiseNonPlayerLivingMobsBatch(CommandSender initiator, List<LivingEntity> targets, EntityType type) {
        return disguiseNonPlayerLivingMobsBatch(initiator, targets, type, MobDisguiseOptions.DEFAULT);
    }

    public CompletableFuture<int[]> disguiseNonPlayerLivingMobsBatch(
        CommandSender initiator,
        List<LivingEntity> targets,
        EntityType type,
        MobDisguiseOptions options
    ) {
        if (initiator instanceof Player p && !rateAllowed(p.getUniqueId())) {
            return CompletableFuture.completedFuture(new int[] {0, targets.size()});
        }
        if (!MobDisguiseEntityMapping.isSupported(type)) {
            return CompletableFuture.completedFuture(new int[] {0, targets.size()});
        }
        MobDisguiseOptions opts = options == null ? MobDisguiseOptions.DEFAULT : options;
        CompletableFuture<int[]> done = new CompletableFuture<>();
        syncTasks.runSync(() -> {
            int ok = 0;
            int skip = 0;
            for (LivingEntity le : targets) {
                if (le instanceof Player) {
                    skip++;
                    continue;
                }
                if (!le.isValid()) {
                    skip++;
                    continue;
                }
                try {
                    mobDisguise.attach(le, type, opts);
                    ok++;
                    LOG.info("Applied entity mob disguise for {} ({}) as {}", le.getUniqueId(), le.getType(), type);
                } catch (Exception ex) {
                    skip++;
                    LOG.error("Failed entity mob disguise for {}", le.getUniqueId(), ex);
                }
            }
            done.complete(new int[] {ok, skip});
        });
        return done;
    }

    public CompletableFuture<DisguiseResult> disguiseLivingAsMobApi(LivingEntity target, EntityType type) {
        return disguiseLivingAsMobApi(target, type, MobDisguiseOptions.DEFAULT);
    }

    public CompletableFuture<DisguiseResult> disguiseLivingAsMobApi(LivingEntity target, EntityType type, MobDisguiseOptions options) {
        if (!MobDisguiseEntityMapping.isSupported(type)) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text(
                    "That entity type cannot be used as a mob disguise (non-living, blocked, or unmapped).",
                    NamedTextColor.RED
                ))
            );
        }
        MobDisguiseOptions opts = options == null ? MobDisguiseOptions.DEFAULT : options;
        CompletableFuture<DisguiseResult> done = new CompletableFuture<>();
        syncTasks.runSync(() -> {
            try {
                if (!target.isValid()) {
                    done.complete(DisguiseResult.failure(Component.text("That entity is no longer valid.", NamedTextColor.RED)));
                    return;
                }
                mobDisguise.attach(target, type, opts);
                LOG.info("Applied entity mob disguise (API) for {} as {}", target.getUniqueId(), type);
                done.complete(DisguiseResult.ok());
            } catch (Exception ex) {
                LOG.error("Failed entity mob disguise (API) for {}", target.getUniqueId(), ex);
                done.complete(DisguiseResult.failure(Component.text("Could not apply mob disguise to that entity.", NamedTextColor.RED)));
            }
        });
        return done;
    }

    public DisguiseResult undisguiseLivingMob(LivingEntity target) {
        if (!mobDisguise.isMobDisguised(target.getUniqueId())) {
            return DisguiseResult.failure(Component.text("That entity has no mob disguise.", NamedTextColor.GRAY));
        }
        mobDisguise.detach(target);
        LOG.info("Removed mob disguise from entity {}", target.getUniqueId());
        return DisguiseResult.ok();
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
                mobDisguise.setSpoofedPlayerName(target.getUniqueId(), shown);
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
        mobDisguise.clearSpoofedPlayerName(target.getUniqueId());
        boolean wasMob = mobDisguise.isMobDisguised(target.getUniqueId());
        if (wasMob && mode == DisguiseSwitchMode.TO_MOB) {
            mobDisguise.detachDecoyOnly((LivingEntity) target);
        } else if (wasMob && mode == DisguiseSwitchMode.TO_PLAYER) {
            mobDisguise.detach((LivingEntity) target);
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
