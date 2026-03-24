package ch.eneas.flipsdisguises.disguise.domain;

import java.util.Objects;

public final class PlayerLookBackup {

    private final SkinSnapshot skin;
    private final String displayPlainOrNull;
    private final String listPlainOrNull;

    public PlayerLookBackup(SkinSnapshot skin, String displayPlainOrNull, String listPlainOrNull) {
        this.skin = Objects.requireNonNull(skin);
        this.displayPlainOrNull = displayPlainOrNull;
        this.listPlainOrNull = listPlainOrNull;
    }

    public SkinSnapshot skin() {
        return skin;
    }

    public String displayPlainOrNull() {
        return displayPlainOrNull;
    }

    public String listPlainOrNull() {
        return listPlainOrNull;
    }
}
