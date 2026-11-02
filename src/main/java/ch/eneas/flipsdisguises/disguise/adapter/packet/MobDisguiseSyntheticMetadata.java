package ch.eneas.flipsdisguises.disguise.adapter.packet;

import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
import ch.eneas.flipsdisguises.disguise.domain.PlayerDisguiseOptions;
import com.github.retrooper.packetevents.protocol.player.HumanoidArm;
import com.github.retrooper.packetevents.protocol.entity.axolotl.AxolotlVariant;
import com.github.retrooper.packetevents.protocol.entity.axolotl.AxolotlVariants;
import com.github.retrooper.packetevents.protocol.entity.cat.CatVariant;
import com.github.retrooper.packetevents.protocol.entity.cat.CatVariants;
import com.github.retrooper.packetevents.protocol.entity.chicken.ChickenVariant;
import com.github.retrooper.packetevents.protocol.entity.chicken.ChickenVariants;
import com.github.retrooper.packetevents.protocol.entity.cow.CowVariant;
import com.github.retrooper.packetevents.protocol.entity.cow.CowVariants;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.frog.FrogVariant;
import com.github.retrooper.packetevents.protocol.entity.frog.FrogVariants;
import com.github.retrooper.packetevents.protocol.entity.horse.HorseVariant;
import com.github.retrooper.packetevents.protocol.entity.horse.HorseVariants;
import com.github.retrooper.packetevents.protocol.entity.pig.PigVariant;
import com.github.retrooper.packetevents.protocol.entity.pig.PigVariants;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.entity.villager.VillagerData;
import com.github.retrooper.packetevents.protocol.entity.villager.profession.VillagerProfession;
import com.github.retrooper.packetevents.protocol.entity.villager.profession.VillagerProfessions;
import com.github.retrooper.packetevents.protocol.entity.villager.type.VillagerType;
import com.github.retrooper.packetevents.protocol.entity.villager.type.VillagerTypes;
import com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfVariant;
import com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfVariants;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import net.kyori.adventure.util.TriState;
import org.bukkit.DyeColor;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Pose;
import org.bukkit.util.OldEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MobDisguiseSyntheticMetadata {

    private static final int IDX_SHARED_FLAGS = 0;
    private static final int IDX_CUSTOM_NAME = 2;
    private static final int IDX_CUSTOM_NAME_VISIBLE = 3;
    private static final int IDX_SILENT = 4;
    private static final int IDX_POSE = 6;
    private static final int IDX_BABY = 16;
    private static final int IDX_SLIME_OR_PHANTOM_SIZE = 16;
    private static final int IDX_SHEEP_WOOL = 17;
    private static final int IDX_COW_VARIANT = 17;
    private static final int IDX_AXOLOTL_VARIANT = 17;
    private static final int IDX_FROG_VARIANT = 17;
    private static final int IDX_CHICKEN_VARIANT = 17;
    private static final int IDX_CREEPER_POWERED = 17;
    private static final int IDX_PIG_VARIANT = 18;
    private static final int IDX_CAT_VARIANT = 19;
    private static final int IDX_VILLAGER_DATA = 18;
    private static final int IDX_WOLF_COLLAR = 20;
    private static final int IDX_WOLF_VARIANT = 22;
    private static final int IDX_CAT_COLLAR = 22;
    private static final int IDX_ZOMBIE_VILLAGER_DATA = 20;
    private static final int IDX_HORSE_FLAGS = 17;
    private static final int IDX_HORSE_TYPE_VARIANT = 18;
    // Avatar (player) indices since 1.21.9: main arm first, then the displayed skin layers.
    private static final int IDX_AVATAR_MAIN_HAND = 15;
    private static final int IDX_AVATAR_SKIN_PARTS = 16;

    private MobDisguiseSyntheticMetadata() {
    }

    public static List<EntityData<?>> fragment(ClientVersion cv, EntityType mob, MobDisguiseOptions o) {
        MobDisguiseOptions opts = o == null ? MobDisguiseOptions.DEFAULT : o;
        List<EntityData<?>> list = new ArrayList<>();
        addSharedFlags(list, opts);
        if (opts.displayName() != null) {
            list.add(new EntityData<>(IDX_CUSTOM_NAME, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(opts.displayName())));
            list.add(new EntityData<>(IDX_CUSTOM_NAME_VISIBLE, EntityDataTypes.BOOLEAN, true));
        }
        if (opts.silent() != null) {
            list.add(new EntityData<>(IDX_SILENT, EntityDataTypes.BOOLEAN, opts.silent()));
        }
        if (opts.pose() != null) {
            EntityPose ep = bukkitPoseToPe(opts.pose());
            if (ep != null) {
                list.add(new EntityData<>(IDX_POSE, EntityDataTypes.ENTITY_POSE, ep));
            }
        }
        if (opts.baby() != null && hasBabyKey(mob)) {
            list.add(new EntityData<>(IDX_BABY, EntityDataTypes.BOOLEAN, opts.baby()));
        }
        if (mob == EntityType.SLIME || mob == EntityType.MAGMA_CUBE) {
            int sz = opts.size() != null ? opts.effectiveSlimeOrMagmaSize() : 1;
            list.add(new EntityData<>(IDX_SLIME_OR_PHANTOM_SIZE, EntityDataTypes.INT, sz));
        }
        if (mob == EntityType.PHANTOM && opts.size() != null) {
            list.add(new EntityData<>(IDX_SLIME_OR_PHANTOM_SIZE, EntityDataTypes.INT, opts.effectiveSlimeOrMagmaSize()));
        }
        if (mob == EntityType.SHEEP) {
            byte wool = sheepWoolByte(opts);
            list.add(new EntityData<>(IDX_SHEEP_WOOL, EntityDataTypes.BYTE, wool));
        }
        if (mob == EntityType.COW && opts.cowVariant() != null) {
            CowVariant cvv = bukkitCowVariant(opts.cowVariant());
            if (cvv != null) {
                list.add(new EntityData<>(IDX_COW_VARIANT, EntityDataTypes.COW_VARIANT, cvv));
            }
        }
        if (mob == EntityType.CAT) {
            if (opts.catVariant() != null) {
                CatVariant cat = resolveCat(oldEnumName(opts.catVariant()));
                if (cat != null) {
                    list.add(new EntityData<>(IDX_CAT_VARIANT, EntityDataTypes.TYPED_CAT_VARIANT, cat));
                }
            }
            if (opts.color() != null) {
                list.add(new EntityData<>(IDX_CAT_COLLAR, EntityDataTypes.BYTE, (byte) (opts.color().ordinal() & 0x0F)));
            }
        }
        if (mob == EntityType.WOLF) {
            if (opts.wolfVariant() != null) {
                WolfVariant wv = resolveWolf(registryPathUpper(opts.wolfVariant()));
                if (wv != null) {
                    list.add(new EntityData<>(IDX_WOLF_VARIANT, EntityDataTypes.TYPED_WOLF_VARIANT, wv));
                }
            }
            if (opts.color() != null) {
                list.add(new EntityData<>(IDX_WOLF_COLLAR, EntityDataTypes.BYTE, (byte) (opts.color().ordinal() & 0x0F)));
            }
        }
        if (mob == EntityType.AXOLOTL && opts.axolotlVariant() != null) {
            AxolotlVariant av = resolveAxolotl(opts.axolotlVariant().name());
            if (av != null) {
                list.add(new EntityData<>(IDX_AXOLOTL_VARIANT, EntityDataTypes.INT, av.getId(cv)));
            }
        }
        if (mob == EntityType.FROG && opts.frogVariant() != null) {
            FrogVariant fv = resolveFrog(registryPathUpper(opts.frogVariant()));
            if (fv != null) {
                list.add(new EntityData<>(IDX_FROG_VARIANT, EntityDataTypes.TYPED_FROG_VARIANT, fv));
            }
        }
        if (mob == EntityType.CHICKEN && opts.chickenVariant() != null) {
            ChickenVariant ch = resolveChicken(registryPathUpper(opts.chickenVariant()));
            if (ch != null) {
                list.add(new EntityData<>(IDX_CHICKEN_VARIANT, EntityDataTypes.CHICKEN_VARIANT, ch));
            }
        }
        if (mob == EntityType.PIG && opts.pigVariant() != null) {
            PigVariant pv = resolvePig(registryPathUpper(opts.pigVariant()));
            if (pv != null) {
                list.add(new EntityData<>(IDX_PIG_VARIANT, EntityDataTypes.PIG_VARIANT, pv));
            }
        }
        if (mob == EntityType.CREEPER && opts.charged() != null) {
            list.add(new EntityData<>(IDX_CREEPER_POWERED, EntityDataTypes.BOOLEAN, opts.charged()));
        }
        if (mob == EntityType.VILLAGER) {
            VillagerData vd = villagerData(opts);
            list.add(new EntityData<>(IDX_VILLAGER_DATA, EntityDataTypes.VILLAGER_DATA, vd));
        }
        if (mob == EntityType.ZOMBIE_VILLAGER) {
            VillagerData vd = villagerData(opts);
            list.add(new EntityData<>(IDX_ZOMBIE_VILLAGER_DATA, EntityDataTypes.VILLAGER_DATA, vd));
        }
        if (mob == EntityType.HORSE) {
            list.add(new EntityData<>(IDX_HORSE_FLAGS, EntityDataTypes.BYTE, horseFamilyFlags(opts)));
            HorseVariant hv = peHorseColor(opts.horseColor());
            list.add(new EntityData<>(IDX_HORSE_TYPE_VARIANT, EntityDataTypes.INT, hv.getId(cv)));
        }
        if (mob == EntityType.SKELETON_HORSE || mob == EntityType.ZOMBIE_HORSE) {
            list.add(new EntityData<>(IDX_HORSE_FLAGS, EntityDataTypes.BYTE, horseFamilyFlags(opts)));
        }
        if (mob == EntityType.DONKEY || mob == EntityType.MULE) {
            list.add(new EntityData<>(IDX_HORSE_FLAGS, EntityDataTypes.BYTE, horseFamilyFlags(opts)));
        }
        return list;
    }

    /**
     * Metadata for a player-model disguise. Only the shared flags and the avatar indices are sent, never the
     * original entity's metadata: the overhead name of a player comes from its profile and team, so no custom name
     * is sent either.
     */
    public static List<EntityData<?>> playerFragment(PlayerDisguiseOptions o) {
        PlayerDisguiseOptions opts = o == null ? PlayerDisguiseOptions.DEFAULT : o;
        List<EntityData<?>> list = new ArrayList<>();
        int bits = 0;
        if (Boolean.TRUE.equals(opts.glowing())) {
            bits |= 0x40;
        }
        if (Boolean.TRUE.equals(opts.sneaking())) {
            bits |= 0x02;
        }
        if (opts.burning() == TriState.TRUE) {
            bits |= 0x01;
        }
        list.add(new EntityData<>(IDX_SHARED_FLAGS, EntityDataTypes.BYTE, (byte) (bits & 0xFF)));
        if (opts.silent() != null) {
            list.add(new EntityData<>(IDX_SILENT, EntityDataTypes.BOOLEAN, opts.silent()));
        }
        Pose pose = opts.pose() != null ? opts.pose() : Boolean.TRUE.equals(opts.sneaking()) ? Pose.SNEAKING : null;
        if (pose != null) {
            list.add(new EntityData<>(IDX_POSE, EntityDataTypes.ENTITY_POSE, bukkitPoseToPe(pose)));
        }
        list.add(new EntityData<>(
            IDX_AVATAR_MAIN_HAND,
            EntityDataTypes.HUMANOID_ARM,
            Boolean.TRUE.equals(opts.leftHanded()) ? HumanoidArm.LEFT : HumanoidArm.RIGHT
        ));
        list.add(new EntityData<>(IDX_AVATAR_SKIN_PARTS, EntityDataTypes.BYTE, opts.skinPartsMask()));
        return list;
    }

    private static boolean hasBabyKey(EntityType mob) {
        Class<?> ec = mob.getEntityClass();
        return ec != null && Ageable.class.isAssignableFrom(ec);
    }

    private static void addSharedFlags(List<EntityData<?>> list, MobDisguiseOptions o) {
        int bits = 0;
        if (Boolean.TRUE.equals(o.glowing())) {
            bits |= 0x40;
        }
        if (Boolean.TRUE.equals(o.sneaking())) {
            bits |= 0x02;
        }
        if (o.burning() == TriState.TRUE) {
            bits |= 0x01;
        }
        list.add(new EntityData<>(IDX_SHARED_FLAGS, EntityDataTypes.BYTE, (byte) (bits & 0xFF)));
    }

    private static byte sheepWoolByte(MobDisguiseOptions o) {
        DyeColor dye = o.color() != null ? o.color() : DyeColor.WHITE;
        byte wool = (byte) (dye.ordinal() & 0x0F);
        if (Boolean.TRUE.equals(o.sheared())) {
            wool |= 0x10;
        }
        return wool;
    }

    private static VillagerData villagerData(MobDisguiseOptions o) {
        VillagerType vt = VillagerTypes.PLAINS;
        if (o.villagerType() != null) {
            VillagerType t = VillagerTypes.getByName(o.villagerType().getKey().toString());
            if (t != null) {
                vt = t;
            }
        }
        VillagerProfession vp = VillagerProfessions.NONE;
        if (o.profession() != null) {
            VillagerProfession p = VillagerProfessions.getByName(o.profession().getKey().toString());
            if (p != null) {
                vp = p;
            }
        }
        return new VillagerData(vt, vp, 1);
    }

    private static byte horseFamilyFlags(MobDisguiseOptions o) {
        int f = 0;
        if (Boolean.TRUE.equals(o.saddled())) {
            f |= 0x02;
        }
        return (byte) (f & 0xFF);
    }

    private static HorseVariant peHorseColor(Horse.Color c) {
        if (c == null) {
            return HorseVariants.BROWN;
        }
        return switch (c) {
            case WHITE -> HorseVariants.WHITE;
            case CREAMY -> HorseVariants.CREAMY;
            case CHESTNUT -> HorseVariants.CHESTNUT;
            case BROWN -> HorseVariants.BROWN;
            case BLACK -> HorseVariants.BLACK;
            case GRAY -> HorseVariants.GRAY;
            case DARK_BROWN -> HorseVariants.DARK_BROWN;
        };
    }

    private static EntityPose bukkitPoseToPe(Pose p) {
        return switch (p) {
            case STANDING -> EntityPose.STANDING;
            case FALL_FLYING -> EntityPose.FALL_FLYING;
            case SLEEPING -> EntityPose.SLEEPING;
            case SWIMMING -> EntityPose.SWIMMING;
            case SPIN_ATTACK -> EntityPose.SPIN_ATTACK;
            case SNEAKING -> EntityPose.CROUCHING;
            case LONG_JUMPING -> EntityPose.LONG_JUMPING;
            case DYING -> EntityPose.DYING;
            case CROAKING -> EntityPose.CROAKING;
            case USING_TONGUE -> EntityPose.USING_TONGUE;
            case SITTING -> EntityPose.SITTING;
            case ROARING -> EntityPose.ROARING;
            case SNIFFING -> EntityPose.SNIFFING;
            case EMERGING -> EntityPose.EMERGING;
            case DIGGING -> EntityPose.DIGGING;
            case SLIDING -> EntityPose.SLIDING;
            case SHOOTING -> EntityPose.SHOOTING;
            case INHALING -> EntityPose.INHALING;
        };
    }

    private static String oldEnumName(OldEnum<?> e) {
        return e.name();
    }

    private static String registryPathUpper(org.bukkit.Keyed k) {
        return k.getKey().getKey().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static CowVariant bukkitCowVariant(Cow.Variant v) {
        return resolveCow(registryPathUpper(v));
    }

    private static CowVariant resolveCow(String u) {
        String key = u.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "COLD" -> CowVariants.COLD;
            case "TEMPERATE" -> CowVariants.TEMPERATE;
            case "WARM" -> CowVariants.WARM;
            default -> null;
        };
    }

    private static CatVariant resolveCat(String u) {
        String key = u.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "ALL_BLACK" -> CatVariants.ALL_BLACK;
            case "BLACK" -> CatVariants.BLACK;
            case "BRITISH_SHORTHAIR" -> CatVariants.BRITISH_SHORTHAIR;
            case "CALICO" -> CatVariants.CALICO;
            case "JELLIE" -> CatVariants.JELLIE;
            case "PERSIAN" -> CatVariants.PERSIAN;
            case "RAGDOLL" -> CatVariants.RAGDOLL;
            case "RED" -> CatVariants.RED;
            case "SIAMESE" -> CatVariants.SIAMESE;
            case "TABBY" -> CatVariants.TABBY;
            case "WHITE" -> CatVariants.WHITE;
            default -> null;
        };
    }

    private static WolfVariant resolveWolf(String u) {
        String key = u.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "PALE" -> WolfVariants.PALE;
            case "SPOTTED" -> WolfVariants.SPOTTED;
            case "SNOWY" -> WolfVariants.SNOWY;
            case "BLACK" -> WolfVariants.BLACK;
            case "ASHEN" -> WolfVariants.ASHEN;
            case "RUSTY" -> WolfVariants.RUSTY;
            case "WOODS" -> WolfVariants.WOODS;
            case "CHESTNUT" -> WolfVariants.CHESTNUT;
            case "STRIPED" -> WolfVariants.STRIPED;
            default -> null;
        };
    }

    private static FrogVariant resolveFrog(String u) {
        String key = u.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "TEMPERATE" -> FrogVariants.TEMPERATE;
            case "COLD" -> FrogVariants.COLD;
            case "WARM" -> FrogVariants.WARM;
            default -> null;
        };
    }

    private static ChickenVariant resolveChicken(String u) {
        String key = u.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "COLD" -> ChickenVariants.COLD;
            case "TEMPERATE" -> ChickenVariants.TEMPERATE;
            case "WARM" -> ChickenVariants.WARM;
            default -> null;
        };
    }

    private static PigVariant resolvePig(String u) {
        String key = u.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "COLD" -> PigVariants.COLD;
            case "TEMPERATE" -> PigVariants.TEMPERATE;
            case "WARM" -> PigVariants.WARM;
            default -> null;
        };
    }

    private static AxolotlVariant resolveAxolotl(String u) {
        String key = u.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "LUCY" -> AxolotlVariants.LUCY;
            case "WILD" -> AxolotlVariants.WILD;
            case "GOLD" -> AxolotlVariants.GOLD;
            case "CYAN" -> AxolotlVariants.CYAN;
            case "BLUE" -> AxolotlVariants.BLUE;
            default -> null;
        };
    }
}
