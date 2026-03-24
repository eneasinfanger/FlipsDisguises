package ch.eneas.flipsdisguises.disguise.domain;

import java.util.Objects;

public final class SkinProperty {

    private final String name;
    private final String value;
    private final String signature;

    public SkinProperty(String name, String value, String signature) {
        this.name = Objects.requireNonNull(name);
        this.value = Objects.requireNonNull(value);
        this.signature = signature;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public String signature() {
        return signature;
    }
}
