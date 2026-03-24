package ch.eneas.flipsdisguises.disguise.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SkinSnapshot {

    private final UUID playerId;
    private final String displayName;
    private final List<SkinProperty> properties;

    public SkinSnapshot(UUID playerId, String displayName, List<SkinProperty> properties) {
        this.playerId = Objects.requireNonNull(playerId);
        this.displayName = Objects.requireNonNull(displayName);
        this.properties = List.copyOf(properties);
    }

    public UUID playerId() {
        return playerId;
    }

    public String displayName() {
        return displayName;
    }

    public List<SkinProperty> properties() {
        return properties;
    }
}
