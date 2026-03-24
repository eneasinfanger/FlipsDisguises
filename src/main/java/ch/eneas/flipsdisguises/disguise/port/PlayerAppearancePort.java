package ch.eneas.flipsdisguises.disguise.port;

import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;
import org.bukkit.entity.Player;

public interface PlayerAppearancePort {

    SkinSnapshot capture(Player player);

    void applySkin(Player player, SkinSnapshot skin);
}
