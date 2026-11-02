package ch.eneas.flipsdisguises.disguise.application;

import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.domain.PlayerDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.domain.PlayerLookBackup;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import ch.eneas.flipsdisguises.disguise.domain.StoredDisguise;
import ch.eneas.flipsdisguises.disguise.port.DisguiseStore;
import ch.eneas.flipsdisguises.disguise.adapter.packet.MobDisguiseEntityMapping;
import ch.eneas.flipsdisguises.disguise.port.MobDisguisePort;
import ch.eneas.flipsdisguises.disguise.port.PlayerAppearancePort;
import ch.eneas.flipsdisguises.disguise.port.SkinCatalog;
import ch.eneas.flipsdisguises.plugin.SyncTaskRunner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
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
    private final DisguiseStore store;
    private final Map<UUID, PlayerLookBackup> originalsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, StoredDisguise> activeByPlayer = new ConcurrentHashMap<>();

    @Inject
    public DisguiseService(
        SyncTaskRunner syncTasks,
        SkinCatalog skins,
        PlayerAppearancePort appearance,
        MobDisguisePort mobDisguise,
        DisguiseStore store
    ) {
        this.syncTasks = syncTasks;
        this.skins = skins;
        this.appearance = appearance;
        this.mobDisguise = mobDisguise;
        this.store = store;
    }

    /**
     * The skin or mob disguise currently applied to this entity, if any. For players it is the in-memory active
     * disguise; for any other entity it is the mob model attached by the {@link MobDisguisePort}.
     */
    public Optional<StoredDisguise> activeDisguise(LivingEntity target) {
        if (target instanceof Player) {
            return Optional.ofNullable(activeByPlayer.get(target.getUniqueId()));
        }
        return mobDisguise.mobDisguiseOf(target.getUniqueId());
    }

    public boolean isDisguised(LivingEntity target) {
        return activeDisguise(target).isPresent();
    }

    /**
     * Drops the in-memory state of a player who left. The persisted disguise is deliberately kept so it can be
     * re-applied by {@link #restorePersisted(Player)} on the next join.
     */
    public void forget(UUID playerId) {
        originalsByPlayer.remove(playerId);
        activeByPlayer.remove(playerId);
        mobDisguise.detachDisconnected(playerId);
        mobDisguise.clearSpoofedPlayerName(playerId);
    }

    /**
     * Re-applies the disguise saved for this player, if any. Must run on the main thread with the player online.
     */
    public void restorePersisted(Player player) {
        UUID id = player.getUniqueId();
        if (!player.isOnline() || originalsByPlayer.containsKey(id)) {
            return;
        }
        Optional<StoredDisguise> loaded = store.load(player);
        if (loaded.isEmpty()) {
            return;
        }
        StoredDisguise stored = loaded.get();
        try {
            // The saved player data may already carry the disguise's custom name, so only the live skin is taken
            // from a fresh capture; the name fields come from what was recorded before the disguise was applied.
            PlayerLookBackup fresh = appearance.capture(player);
            PlayerLookBackup saved = stored.original();
            PlayerLookBackup original = new PlayerLookBackup(
                fresh.getSkin(),
                saved.getDisplayPlainOrNull(),
                saved.getListPlainOrNull(),
                saved.getCustomNamePlainOrNull(),
                saved.isCustomNameVisibleBefore()
            );
            originalsByPlayer.put(id, original);
            StoredDisguise active = stored.withOriginal(original);
            activeByPlayer.put(id, active);
            applyLabels(player, active);
            if (active.kind() == StoredDisguise.Kind.MOB) {
                mobDisguise.attach((LivingEntity) player, active.mobType(), active.options());
            }
            LOG.info("Restored saved {} disguise for {}", active.kind(), id);
        } catch (Exception ex) {
            originalsByPlayer.remove(id);
            activeByPlayer.remove(id);
            LOG.error("Failed to restore saved disguise for {}", id, ex);
        }
    }

    /**
     * Respawning creates a new player entity, which may come back without the disguise's names/profile.
     */
    public void reapplyAfterRespawn(Player player) {
        StoredDisguise active = activeByPlayer.get(player.getUniqueId());
        if (active == null || !player.isOnline()) {
            return;
        }
        try {
            applyLabels(player, active);
        } catch (Exception ex) {
            LOG.error("Failed to re-apply disguise labels after respawn for {}", player.getUniqueId(), ex);
        }
    }

    private void applyLabels(Player player, StoredDisguise d) {
        if (d.kind() == StoredDisguise.Kind.MOB) {
            if (d.options().displayName() != null) {
                appearance.applyMobLabels(player, d.mobType(), d.options().displayName());
            } else {
                appearance.applyMobLabels(player, d.mobType());
            }
        } else {
            mobDisguise.setSpoofedPlayerName(player.getUniqueId(), d.shownName());
            appearance.applyImpersonation(player, d.skin(), d.shownName());
        }
    }

    private void rememberMob(Player target, EntityType type, MobDisguiseOptions options) {
        remember(target, StoredDisguise.mob(type, options, originalsByPlayer.get(target.getUniqueId())));
    }

    private void remember(Player target, StoredDisguise disguise) {
        activeByPlayer.put(target.getUniqueId(), disguise);
        try {
            store.save(target, disguise);
        } catch (Exception ex) {
            LOG.error("Failed to persist disguise for {}", target.getUniqueId(), ex);
        }
    }

    /**
     * Removes the disguise from a player (restoring their look) or from any other living entity. Must run on the
     * main thread.
     */
    public DisguiseResult undisguise(LivingEntity target) {
        return target instanceof Player player ? undisguisePlayer(player) : undisguiseNonPlayer(target);
    }

    private DisguiseResult undisguisePlayer(Player target) {
        PlayerLookBackup original = originalsByPlayer.remove(target.getUniqueId());
        if (original == null) {
            return DisguiseResult.failure(Component.text("Nothing to restore.", NamedTextColor.GRAY));
        }
        activeByPlayer.remove(target.getUniqueId());
        store.clear(target);
        if (mobDisguise.isMobDisguised(target.getUniqueId())) {
            mobDisguise.detach((LivingEntity) target);
        }
        mobDisguise.clearSpoofedPlayerName(target.getUniqueId());
        appearance.restore(target, original, true);
        LOG.info("Disguise removed for {}", target.getUniqueId());
        return DisguiseResult.ok();
    }

    /**
     * Resolves the skin asynchronously, then applies it on the main thread; the returned future completes there
     * (or on the lookup thread if the lookup itself failed).
     */
    public CompletableFuture<DisguiseResult> disguiseAsPlayer(Player target, String skinOwner) {
        return disguiseAsPlayer(target, skinOwner, PlayerDisguiseOptions.DEFAULT);
    }

    /**
     * Shows the entity as a player model with the skin of {@code skinOwner}. A player gets their profile swapped
     * (and their original look backed up); any other living entity is rewritten to a player model on the wire.
     */
    public CompletableFuture<DisguiseResult> disguiseAsPlayer(
        LivingEntity target,
        String skinOwner,
        PlayerDisguiseOptions options
    ) {
        String owner = skinOwner.trim();
        if (owner.isEmpty()) {
            return CompletableFuture.completedFuture(
                DisguiseResult.failure(Component.text("Player name required.", NamedTextColor.RED))
            );
        }
        PlayerDisguiseOptions opts = options == null ? PlayerDisguiseOptions.DEFAULT : options;
        return skins.resolveUsername(owner).thenCompose(skin -> target instanceof Player player
            ? scheduleApplyPlayer(player, skin, owner, opts)
            : scheduleApplyPlayerModel(target, skin, owner, opts)
        ).exceptionally(ex -> {
            LOG.error("Failed to resolve skin profile '{}' for {}", owner, target.getUniqueId(), ex);
            return DisguiseResult.failure(Component.text("Could not resolve that player.", NamedTextColor.RED));
        });
    }

    /**
     * Shows the entity as a mob. A player keeps their real entity and gets their original look backed up so
     * {@link #undisguise} can restore it; any other entity just has the mob model attached. Must run on the main thread.
     */
    public DisguiseResult disguiseAsMob(LivingEntity target, EntityType type, MobDisguiseOptions options) {
        MobDisguiseOptions opts = options == null ? MobDisguiseOptions.DEFAULT : options;
        return target instanceof Player player
            ? disguisePlayerAsMob(player, type, opts)
            : disguiseNonPlayerAsMob(target, type, opts);
    }

    private DisguiseResult disguisePlayerAsMob(Player target, EntityType type, MobDisguiseOptions opts) {
        if (!target.isOnline()) {
            return DisguiseResult.failure(Component.text("Target is not online.", NamedTextColor.RED));
        }
        if (!MobDisguiseEntityMapping.isSupported(type)) {
            return unsupportedMobType();
        }
        try {
            prepareSubjectForNewDisguise(target, DisguiseSwitchMode.TO_MOB);
            if (opts.displayName() != null) {
                appearance.applyMobLabels(target, type, opts.displayName());
            } else {
                appearance.applyMobLabels(target, type);
            }
            mobDisguise.attach((LivingEntity) target, type, opts);
            rememberMob(target, type, opts);
            LOG.info("Applied mob disguise for {} as {}", target.getUniqueId(), type);
            return DisguiseResult.ok();
        } catch (Exception ex) {
            LOG.error("Failed mob disguise for {}", target.getUniqueId(), ex);
            return DisguiseResult.failure(Component.text("Could not apply mob disguise.", NamedTextColor.RED));
        }
    }

    private DisguiseResult disguiseNonPlayerAsMob(LivingEntity target, EntityType type, MobDisguiseOptions opts) {
        if (!target.isValid()) {
            return DisguiseResult.failure(Component.text("That entity is no longer valid.", NamedTextColor.RED));
        }
        if (!MobDisguiseEntityMapping.isSupported(type)) {
            return unsupportedMobType();
        }
        try {
            mobDisguise.attach(target, type, opts);
            persistLivingMob(target, type, opts);
            LOG.info("Applied entity mob disguise for {} ({}) as {}", target.getUniqueId(), target.getType(), type);
            return DisguiseResult.ok();
        } catch (Exception ex) {
            LOG.error("Failed entity mob disguise for {}", target.getUniqueId(), ex);
            return DisguiseResult.failure(Component.text("Could not apply mob disguise to that entity.", NamedTextColor.RED));
        }
    }

    private static DisguiseResult unsupportedMobType() {
        return DisguiseResult.failure(Component.text(
            "That entity type cannot be used as a mob disguise (non-living, blocked, or unmapped).",
            NamedTextColor.RED
        ));
    }

    private DisguiseResult undisguiseNonPlayer(LivingEntity target) {
        if (!mobDisguise.isMobDisguised(target.getUniqueId())) {
            return DisguiseResult.failure(Component.text("That entity has no mob disguise.", NamedTextColor.GRAY));
        }
        mobDisguise.detach(target);
        store.clear(target);
        LOG.info("Removed mob disguise from entity {}", target.getUniqueId());
        return DisguiseResult.ok();
    }

    /**
     * Re-applies the disguise saved on a non-player mob, e.g. after its chunk loads. Must run on the main thread.
     */
    public void restorePersistedLiving(LivingEntity entity) {
        if (entity instanceof Player || !entity.isValid() || mobDisguise.isMobDisguised(entity.getUniqueId())) {
            return;
        }
        Optional<StoredDisguise> loaded = store.load(entity);
        if (loaded.isEmpty()) {
            return;
        }
        StoredDisguise stored = loaded.get();
        try {
            if (stored.kind() == StoredDisguise.Kind.PLAYER) {
                mobDisguise.attachPlayerModel(entity, stored.skin(), stored.shownName(), stored.playerOptions());
                LOG.info("Restored saved player-model disguise for entity {} ({})", entity.getUniqueId(), entity.getType());
                return;
            }
            mobDisguise.attach(entity, stored.mobType(), stored.options());
            LOG.info("Restored saved mob disguise for entity {} ({}) as {}", entity.getUniqueId(), entity.getType(), stored.mobType());
        } catch (Exception ex) {
            LOG.error("Failed to restore saved mob disguise for entity {}", entity.getUniqueId(), ex);
        }
    }

    private void persistLivingMob(LivingEntity entity, EntityType type, MobDisguiseOptions options) {
        try {
            store.save(entity, StoredDisguise.mob(type, options, null));
        } catch (Exception ex) {
            LOG.error("Failed to persist mob disguise for entity {}", entity.getUniqueId(), ex);
        }
    }

    private CompletableFuture<DisguiseResult> scheduleApplyPlayerModel(
        LivingEntity target,
        SkinSnapshot skin,
        String requestedName,
        PlayerDisguiseOptions options
    ) {
        CompletableFuture<DisguiseResult> done = new CompletableFuture<>();
        syncTasks.runSync(() -> {
            try {
                if (!target.isValid()) {
                    done.complete(DisguiseResult.failure(Component.text("That entity is no longer valid.", NamedTextColor.RED)));
                    return;
                }
                String shown = shownNameOf(skin, requestedName);
                mobDisguise.attachPlayerModel(target, skin, shown, options);
                try {
                    store.save(target, StoredDisguise.player(skin, shown, options, null));
                } catch (Exception ex) {
                    LOG.error("Failed to persist player-model disguise for entity {}", target.getUniqueId(), ex);
                }
                LOG.info("Applied player-model disguise for {} ({}) as '{}'", target.getUniqueId(), target.getType(), shown);
                done.complete(DisguiseResult.ok());
            } catch (Exception ex) {
                LOG.error("Failed player-model disguise for entity {}", target.getUniqueId(), ex);
                done.complete(DisguiseResult.failure(Component.text("Could not apply disguise to that entity.", NamedTextColor.RED)));
            }
        });
        return done;
    }

    private static String shownNameOf(SkinSnapshot skin, String requestedName) {
        return skin.getDisplayName() == null || skin.getDisplayName().isBlank() ? requestedName.trim() : skin.getDisplayName();
    }

    private CompletableFuture<DisguiseResult> scheduleApplyPlayer(
        Player target,
        SkinSnapshot skin,
        String requestedName,
        PlayerDisguiseOptions options
    ) {
        CompletableFuture<DisguiseResult> done = new CompletableFuture<>();
        syncTasks.runSync(() -> {
            try {
                if (!target.isOnline()) {
                    done.complete(DisguiseResult.failure(Component.text("Target went offline.", NamedTextColor.RED)));
                    return;
                }
                prepareSubjectForNewDisguise(target, DisguiseSwitchMode.TO_PLAYER);
                String shown = shownNameOf(skin, requestedName);
                mobDisguise.setSpoofedPlayerName(target.getUniqueId(), shown);
                appearance.applyImpersonation(target, skin, shown);
                remember(target, StoredDisguise.player(skin, shown, options, originalsByPlayer.get(target.getUniqueId())));
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

    private enum DisguiseSwitchMode {
        TO_PLAYER,
        TO_MOB
    }
}
