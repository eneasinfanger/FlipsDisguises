package ch.eneas.flipsdisguises.disguise.domain;

import ch.eneas.flipsdisguises.command.args.*;
import ch.eneas.flipsdisguises.disguise.meta.ArgType;
import ch.eneas.flipsdisguises.disguise.meta.ValidFor;
import dev.jorel.commandapi.arguments.BooleanArgument;
import dev.jorel.commandapi.arguments.ChatComponentArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import lombok.*;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import org.bukkit.DyeColor;
import org.bukkit.entity.*;

import static ch.eneas.flipsdisguises.disguise.meta.EntityCategory.*;

@With
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@Accessors(fluent = true)
@Getter
public class MobDisguiseOptions {
    public static final MobDisguiseOptions DEFAULT = new MobDisguiseOptions();

    @ValidFor(categories = LIVING)
    @ArgType(BooleanArgument.class)
    private Boolean glowing;
    @ValidFor(categories = ALL)
    @ArgType(ChatComponentArgument.class)
    private Component displayName;
    @ValidFor(categories = LIVING)
    @ArgType(PoseArgument.class)
    private Pose pose;
    @ValidFor(categories = LIVING)
    @ArgType(BooleanArgument.class)
    private Boolean silent;
    @ValidFor(categories = LIVING)
    @ArgType(BooleanArgument.class)
    private Boolean sneaking;
    @ValidFor(categories = AGEABLE)
    @ArgType(BooleanArgument.class)
    private Boolean baby;
    @ValidFor(categories = LIVING)
    @ArgType(TriStateArgument.class)
    private TriState burning;
    @ValidFor({EntityType.PHANTOM, EntityType.SLIME, EntityType.MAGMA_CUBE})
    @ArgType(IntegerArgument.class)
    private Integer size;
    @ValidFor(EntityType.AXOLOTL)
    @ArgType(AxolotlVariantArgument.class)
    private Axolotl.Variant axolotlVariant;
    @ValidFor({EntityType.VILLAGER, EntityType.ZOMBIE_VILLAGER})
    @ArgType(VillagerProfessionArgument.class)
    private Villager.Profession profession;
    @ValidFor({EntityType.VILLAGER, EntityType.ZOMBIE_VILLAGER})
    @ArgType(VillagerTypeArgument.class)
    private Villager.Type type;
    @ValidFor(EntityType.SHEEP)
    @ArgType(BooleanArgument.class)
    private Boolean sheared;
    @ValidFor(categories = COLORABLE)
    @ArgType(DyeColorArgument.class)
    private DyeColor color;
    @ValidFor(EntityType.CREEPER)
    @ArgType(BooleanArgument.class)
    private Boolean charged;
    @ValidFor({
        EntityType.HORSE,
        EntityType.SKELETON_HORSE,
        EntityType.ZOMBIE_HORSE,
        EntityType.DONKEY,
        EntityType.MULE
    })
    @ArgType(BooleanArgument.class)
    private Boolean saddled;
    @ValidFor(EntityType.HORSE)
    @ArgType(HorseColorArgument.class)
    private Horse.Color horseColor;
    @ValidFor(EntityType.HORSE)
    @ArgType(HorseStyleArgument.class)
    private Horse.Style horseStyle;
    @ValidFor(EntityType.COW)
    @ArgType(VariantArgument.CowVariant.class)
    private Cow.Variant cowVariant;
    @ValidFor(EntityType.CAT)
    @ArgType(VariantArgument.CatVariant.class)
    private Cat.Type catVariant;
    @ValidFor(EntityType.FROG)
    @ArgType(VariantArgument.FrogVariant.class)
    private Frog.Variant frogVariant;
    @ValidFor(EntityType.PIG)
    @ArgType(VariantArgument.PigVariant.class)
    private Pig.Variant pigVariant;
    @ValidFor(EntityType.CHICKEN)
    @ArgType(VariantArgument.ChickenVariant.class)
    private Chicken.Variant chickenVariant;
    @ValidFor(EntityType.WOLF)
    @ArgType(VariantArgument.WolfVariant.class)
    private Wolf.Variant wolfVariant;

    public int effectiveSlimeOrMagmaSize() {
        if (size == null || size < 1) {
            return 1;
        }
        return Math.min(size, 127);
    }
}
