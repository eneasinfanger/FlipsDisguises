package ch.eneas.flipsdisguises.disguise.adapter.paper;

import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.domain.PlayerLookBackup;
import ch.eneas.flipsdisguises.disguise.domain.SkinProperty;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import ch.eneas.flipsdisguises.disguise.port.PlayerAppearancePort;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Injectable
public final class PaperPlayerAppearance implements PlayerAppearancePort {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Override
    public PlayerLookBackup capture(Player player) {
        SkinSnapshot skin = PaperSkinCatalog.toSnapshot(player.getPlayerProfile());
        String username = player.getName();
        String disp = PLAIN.serialize(player.displayName());
        String list = PLAIN.serialize(player.playerListName());
        String displayStored = disp.equals(username) ? null : disp;
        String listStored = list.equals(username) ? null : list;
        return new PlayerLookBackup(skin, displayStored, listStored);
    }

    @Override
    public void applyImpersonation(Player player, SkinSnapshot skin, String shownNamePlain) {
        setProfileFromSnapshot(player, skin);
        Component label = Component.text(shownNamePlain);
        player.displayName(label);
        player.playerListName(label);
    }

    @Override
    public void restore(Player player, PlayerLookBackup backup) {
        setProfileFromSnapshot(player, backup.skin());
        String name = player.getName();
        if (backup.displayPlainOrNull() == null) {
            player.displayName(Component.text(name));
        } else {
            player.displayName(Component.text(backup.displayPlainOrNull()));
        }
        if (backup.listPlainOrNull() == null) {
            player.playerListName(Component.text(name));
        } else {
            player.playerListName(Component.text(backup.listPlainOrNull()));
        }
    }

    private static void setProfileFromSnapshot(Player player, SkinSnapshot skin) {
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
