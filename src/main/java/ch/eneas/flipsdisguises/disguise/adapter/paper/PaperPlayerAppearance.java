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
    private static final int MAX_PROFILE_NAME_LENGTH = 16;

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
        String profileName = clampProfileName(shownNamePlain);
        applyProfileTexturesForSubject(player, skin, profileName);
        Component label = Component.text(shownNamePlain);
        player.displayName(label);
        player.playerListName(label);
        resendSubjectToOtherViewers(player);
    }

    @Override
    public void restore(Player player, PlayerLookBackup backup) {
        SkinSnapshot skin = backup.getSkin();
        String profileName = clampProfileName(
            skin.getDisplayName() == null || skin.getDisplayName().isBlank()
                ? player.getName()
                : skin.getDisplayName()
        );
        applyProfileTexturesForSubject(player, skin, profileName);
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
        resendSubjectToOtherViewers(player);
    }

    private void applyProfileTexturesForSubject(Player subject, SkinSnapshot skin, String nameOnProfile) {
        PlayerProfile profile = plugin.getServer().createProfile(subject.getUniqueId(), nameOnProfile);
        profile.clearProperties();
        for (SkinProperty p : skin.getProperties()) {
            String sig = p.getSignature() == null || p.getSignature().isEmpty() ? null : p.getSignature();
            if (sig == null) {
                profile.setProperty(new ProfileProperty(p.getName(), p.getValue()));
            } else {
                profile.setProperty(new ProfileProperty(p.getName(), p.getValue(), sig));
            }
        }
        subject.setPlayerProfile(profile);
    }

    private void resendSubjectToOtherViewers(Player subject) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                if (viewer.getUniqueId().equals(subject.getUniqueId())) {
                    continue;
                }
                viewer.hideEntity(plugin, subject);
                viewer.showEntity(plugin, subject);
            }
        });
    }

    private static String clampProfileName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Player";
        }
        String t = raw.trim();
        return t.length() > MAX_PROFILE_NAME_LENGTH ? t.substring(0, MAX_PROFILE_NAME_LENGTH) : t;
    }
}
