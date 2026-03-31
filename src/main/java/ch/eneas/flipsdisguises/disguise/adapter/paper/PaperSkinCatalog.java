package ch.eneas.flipsdisguises.disguise.adapter.paper;

import ch.eneas.PluginMain;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.domain.SkinProperty;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import ch.eneas.flipsdisguises.disguise.port.SkinCatalog;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Injectable
public final class PaperSkinCatalog implements SkinCatalog {

    private final PluginMain plugin;
    private final AsyncLoadingCache<String, SkinSnapshot> cache;

    @Inject
    public PaperSkinCatalog(PluginMain plugin) {
        this.plugin = plugin;
        this.cache = Caffeine.newBuilder()
            .maximumSize(512)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .buildAsync((String key, java.util.concurrent.Executor executor) -> load(key));
    }

    private CompletableFuture<SkinSnapshot> load(String username) {
        Player online = plugin.getServer().getPlayerExact(username);
        if (online != null) {
            return CompletableFuture.completedFuture(toSnapshot(online.getPlayerProfile()));
        }
        PlayerProfile profile = plugin.getServer().createProfile(username);
        return CompletableFuture.supplyAsync(() -> {
            profile.complete(true);
            return toSnapshot(profile);
        });
    }

    static SkinSnapshot toSnapshot(PlayerProfile profile) {
        List<SkinProperty> props = new ArrayList<>();
        profile.getProperties().forEach(prop -> props.add(new SkinProperty(prop.getName(), prop.getValue(), prop.getSignature())));
        String display = profile.getName() == null ? "" : profile.getName();
        return new SkinSnapshot(profile.getId(), display, props);
    }

    @Override
    public CompletableFuture<SkinSnapshot> resolveUsername(String username) {
        String key = username.trim().toLowerCase(Locale.ROOT);
        return cache.get(key);
    }
}
