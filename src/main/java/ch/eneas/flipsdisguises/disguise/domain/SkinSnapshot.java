package ch.eneas.flipsdisguises.disguise.domain;

import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
public class SkinSnapshot {
    UUID skinSourceUuid;
    String displayName;
    List<SkinProperty> properties;
}
