package ch.eneas.flipsdisguises.disguise.adapter.paper;

import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.domain.SkinProperty;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import ch.eneas.flipsdisguises.disguise.port.SkinCatalog;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Injectable
public final class PaperSkinCatalog implements SkinCatalog {

    private final AsyncLoadingCache<String, SkinSnapshot> cache = Caffeine.newBuilder()
        .maximumSize(512)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .buildAsync((String key, java.util.concurrent.Executor executor) -> load(key));

    private static CompletableFuture<SkinSnapshot> load(String username) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(username);
        PlayerProfile profile = (PlayerProfile) Bukkit.createPlayerProfile(off.getUniqueId(), username);
        return profile.update().thenApply(PaperSkinCatalog::toSnapshot);
    }

    static SkinSnapshot toSnapshot(PlayerProfile profile) {
        List<SkinProperty> props = new ArrayList<>();
        profile.getProperties().forEach(prop -> props.add(new SkinProperty(prop.getName(), prop.getValue(),
            prop.getSignature() == null ? "" : prop.getSignature())));
        String display = profile.getName() == null ? "" : profile.getName();
        return new SkinSnapshot(profile.getId(), display, props);
    }

    @Override
    public CompletableFuture<SkinSnapshot> resolveUsername(String username) {
        String key = username.trim().toLowerCase(Locale.ROOT);
        return cache.get(key);
    }
}
