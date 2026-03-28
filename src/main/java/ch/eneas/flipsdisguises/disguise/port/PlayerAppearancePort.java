package ch.eneas.flipsdisguises.disguise.port;

import ch.eneas.flipsdisguises.disguise.domain.PlayerLookBackup;
import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public interface PlayerAppearancePort {

    PlayerLookBackup capture(Player player);

    void applyImpersonation(Player player, SkinSnapshot skin, String shownNamePlain);

    void applyMobLabels(Player player, EntityType type);

    void restore(Player player, PlayerLookBackup backup, boolean resendViewers);
}
