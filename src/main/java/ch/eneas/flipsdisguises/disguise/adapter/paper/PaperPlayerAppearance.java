package ch.eneas.flipsdisguises.disguise.adapter.paper;

import ch.eneas.Main;
import ch.eneas.flips.loader.api.Inject;
import ch.eneas.flips.loader.api.Injectable;
import ch.eneas.flipsdisguises.disguise.domain.PlayerLookBackup;
import ch.eneas.flipsdisguises.disguise.domain.SkinProperty;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import ch.eneas.flipsdisguises.disguise.port.PlayerAppearancePort;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

@Injectable
public final class PaperPlayerAppearance implements PlayerAppearancePort {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final Main plugin;

    @Inject
    public PaperPlayerAppearance(Main plugin) {
        this.plugin = plugin;
    }

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
        setProfileFromSnapshot(plugin, player, skin);
        Component label = Component.text(shownNamePlain);
        player.displayName(label);
        player.playerListName(label);
    }

    @Override
    public void restore(Player player, PlayerLookBackup backup) {
        setProfileFromSnapshot(plugin, player, backup.getSkin());
        String name = player.getName();
        if (backup.getDisplayPlainOrNull() == null) {
            player.displayName(Component.text(name));
        } else {
            player.displayName(Component.text(backup.getDisplayPlainOrNull()));
        }
        if (backup.getListPlainOrNull() == null) {
            player.playerListName(Component.text(name));
        } else {
            player.playerListName(Component.text(backup.getListPlainOrNull()));
        }
    }

    private static void setProfileFromSnapshot(Main plugin, Player player, SkinSnapshot skin) {
        PlayerProfile profile = plugin.getServer().createProfile(skin.getPlayerId(), skin.getDisplayName());
        for (SkinProperty p : skin.getProperties()) {
            String sig = p.getSignature() == null || p.getSignature().isEmpty() ? null : p.getSignature();
            if (sig == null) {
                profile.setProperty(new ProfileProperty(p.getName(), p.getValue()));
            } else {
                profile.setProperty(new ProfileProperty(p.getName(), p.getValue(), sig));
            }
        }
        player.setPlayerProfile(profile);
    }
}
