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

    /** A flag applies to an entity type if it is listed explicitly <em>or</em> the type is in an allowed category. */
    public boolean isValidFor(EntityType entityType) {
        if (allowedTypes.contains(entityType)) {
            return true;
        }
        Class<? extends Entity> clazz = entityType.getEntityClass();
        if (clazz == null) {
            return allowedCategories.contains(EntityCategory.ALL);
        }
        return allowedCategories.stream().anyMatch(c -> c.allows(clazz));
    }
}
