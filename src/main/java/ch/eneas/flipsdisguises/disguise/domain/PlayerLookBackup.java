package ch.eneas.flipsdisguises.disguise.domain;

import lombok.Value;

@Value
public class PlayerLookBackup {
    SkinSnapshot skin;
    String displayPlainOrNull;
    String listPlainOrNull;
}
