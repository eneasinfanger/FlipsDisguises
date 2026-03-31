package ch.eneas.flipsdisguises.command.args.disgopts;

import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.meta.EntityCategory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.Set;
import java.util.function.BiConsumer;

@RequiredArgsConstructor
class MobDisguiseFlag {
    @Getter
    private final String name;
    @Getter
    private final MobDisguiseOptionType<?> type;
    private final BiConsumer<MobDisguiseOptions, Object> setter;
    private final Set<EntityType> allowedTypes;
    private final Set<EntityCategory> allowedCategories;

    public void setValue(MobDisguiseOptions opts, Object value) {
        setter.accept(opts, value);
    }

    public boolean isValidFor(EntityType type) {
        if (!allowedTypes.isEmpty() && !allowedTypes.contains(type)) {
            return false;
        }
        Class<? extends Entity> clazz = type.getEntityClass();
        return clazz == null || allowedCategories.stream()
            .anyMatch(c -> c.allows(clazz));
    }
}
