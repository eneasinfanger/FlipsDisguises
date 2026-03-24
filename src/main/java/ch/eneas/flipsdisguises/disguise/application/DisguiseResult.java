package ch.eneas.flipsdisguises.disguise.application;

import net.kyori.adventure.text.Component;

public sealed interface DisguiseResult permits DisguiseResult.Ok, DisguiseResult.Failure {

    static Ok ok() {
        return new Ok();
    }

    static Failure failure(Component message) {
        return new Failure(message);
    }

    record Ok() implements DisguiseResult {}

    record Failure(Component message) implements DisguiseResult {}
}
