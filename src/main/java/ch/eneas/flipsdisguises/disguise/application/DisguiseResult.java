package ch.eneas.flipsdisguises.disguise.application;

public sealed interface DisguiseResult permits DisguiseResult.Ok, DisguiseResult.Failure {

    static Ok ok() {
        return new Ok();
    }

    static Failure failure(String message) {
        return new Failure(message);
    }

    record Ok() implements DisguiseResult {}

    record Failure(String message) implements DisguiseResult {}
}
