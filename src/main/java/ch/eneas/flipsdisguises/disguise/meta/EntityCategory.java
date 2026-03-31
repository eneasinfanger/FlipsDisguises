package ch.eneas.flipsdisguises.disguise.meta;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.material.Colorable;

@RequiredArgsConstructor
public enum EntityCategory {
    ALL(Entity.class),
    LIVING(LivingEntity.class),
    AGEABLE(Ageable.class),
    COLORABLE(Colorable.class),
    ;

    final Class<?> clazz;

    public boolean allows(Class<? extends Entity> clazz) {
        return this.clazz.isAssignableFrom(clazz);
    }
}