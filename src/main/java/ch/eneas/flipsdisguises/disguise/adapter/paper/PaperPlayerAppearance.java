package ch.eneas.flipsdisguises.disguise.adapter.paper;

import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.domain.SkinProperty;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import ch.eneas.flipsdisguises.disguise.port.PlayerAppearancePort;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Injectable
public final class PaperPlayerAppearance implements PlayerAppearancePort {

    @Override
    public SkinSnapshot capture(Player player) {
        return PaperSkinCatalog.toSnapshot(player.getPlayerProfile());
    }

    @Override
    public void applySkin(Player player, SkinSnapshot skin) {
        PlayerProfile profile = (PlayerProfile) Bukkit.createPlayerProfile(skin.playerId(), skin.displayName());
        for (SkinProperty p : skin.properties()) {
            String sig = p.signature() == null || p.signature().isEmpty() ? null : p.signature();
            if (sig == null) {
                profile.setProperty(new ProfileProperty(p.name(), p.value()));
            } else {
                profile.setProperty(new ProfileProperty(p.name(), p.value(), sig));
            }
        }
        player.setPlayerProfile(profile);
    }
}
