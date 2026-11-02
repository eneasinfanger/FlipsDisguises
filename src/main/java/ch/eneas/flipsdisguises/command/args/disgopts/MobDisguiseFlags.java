package ch.eneas.flipsdisguises.command.args.disgopts;

import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.meta.ArgType;
import ch.eneas.flipsdisguises.disguise.meta.EntityCategory;
import ch.eneas.flipsdisguises.disguise.meta.ValidFor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.bukkit.entity.EntityType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class MobDisguiseFlags {
    private static final Map<String, MobDisguiseFlag> ALL_FLAGS;

    public static Stream<MobDisguiseFlag> allFlags() {
        return ALL_FLAGS.values().stream();
    }

    public static MobDisguiseFlag ofFlag(String flag) {
        return ALL_FLAGS.get(flag);
    }

    /** Names of all flags that apply to {@code type}, sorted. */
    public static List<String> namesFor(EntityType type) {
        return allFlags().filter(f -> f.isValidFor(type)).map(MobDisguiseFlag::getName).toList();
    }

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        BinaryOperator<MobDisguiseFlag> throwIfSameFlag = (opt1, opt2) -> {
            throw new IllegalStateException("Two options can't have the same flag: " + opt1 + " and " + opt2);
        };
        ALL_FLAGS = Arrays.stream(MobDisguiseOptions.class.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers()))
            .map(opt -> createMobDisguiseOpt(opt, lookup))
            .collect(Collectors.toMap(MobDisguiseFlag::getName, Function.identity(), throwIfSameFlag, TreeMap::new));
    }

    private static MobDisguiseFlag createMobDisguiseOpt(Field optsField, MethodHandles.Lookup lookup) {
        ValidFor validFor = optsField.getAnnotation(ValidFor.class);
        checkState(validFor != null, "missing @ValidFor on MobDisguiseOptions field '%s'!", optsField.getName());
        ArgType argType = optsField.getAnnotation(ArgType.class);
        checkState(argType != null, "missing @ArgType on MobDisguiseOptions field '%s'!", optsField.getName());

        checkState(validFor.value().length + validFor.categories().length > 0,
            "@ValidFor on MobDisguiseOptions field '%s' lists no types or categories!", optsField.getName());

        Set<EntityType> allowedTypes = EnumSet.noneOf(EntityType.class);
        Collections.addAll(allowedTypes, validFor.value());
        Set<EntityCategory> allowedCategories = EnumSet.noneOf(EntityCategory.class);
        Collections.addAll(allowedCategories, validFor.categories());

        optsField.setAccessible(true);

        return new MobDisguiseFlag(
            optsField.getName(),
            MobDisguiseOptionType.forArgType(argType.value()),
            makeSetter(lookup, optsField),
            allowedTypes,
            allowedCategories
        );
    }

    @SneakyThrows
    private static BiConsumer<MobDisguiseOptions, Object> makeSetter(MethodHandles.Lookup lookup, Field f) {
        return new BiConsumer<>() {
            final MethodHandle handle = lookup.unreflectSetter(f);

            @SneakyThrows
            @Override
            public void accept(MobDisguiseOptions opts, Object val) {
                handle.invoke(opts, val);
            }
        };
    }
}
