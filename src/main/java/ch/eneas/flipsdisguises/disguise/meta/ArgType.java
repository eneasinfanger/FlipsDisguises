package ch.eneas.flipsdisguises.disguise.meta;

import dev.jorel.commandapi.arguments.Argument;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ArgType {
    Class<? extends Argument<?>> value();
}
