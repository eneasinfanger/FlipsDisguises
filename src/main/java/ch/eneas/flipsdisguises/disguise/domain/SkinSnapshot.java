package ch.eneas.flipsdisguises.disguise.domain;

import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
public class SkinSnapshot {
    UUID playerId;
    String displayName;
    List<SkinProperty> properties;
}
