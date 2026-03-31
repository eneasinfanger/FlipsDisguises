package ch.eneas.flipsdisguises.disguise.adapter.packet;

import ch.eneas.flipsdisguises.disguise.domain.MobDisguiseOptions;
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
import com.github.retrooper.packetevents.protocol.entity.pig.PigVariant;
import com.github.retrooper.packetevents.protocol.entity.pig.PigVariants;
import com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfVariant;
import com.github.retrooper.packetevents.protocol.entity.wolfvariant.WolfVariants;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.bukkit.DyeColor;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MobDisguiseSyntheticMetadata {

    private static final int IDX_CUSTOM_NAME = 2;
    private static final int IDX_CUSTOM_NAME_VISIBLE = 3;
    private static final int IDX_SHEEP_WOOL = 17;
    private static final int IDX_SLIME_SIZE = 17;
    private static final int IDX_PHANTOM_SIZE = 17;
    private static final int IDX_AXOLOTL_VARIANT = 17;
    private static final int IDX_FROG_VARIANT = 17;
    private static final int IDX_COW_VARIANT = 17;
    private static final int IDX_CHICKEN_VARIANT = 17;
    private static final int IDX_CAT_VARIANT = 19;
    private static final int IDX_WOLF_VARIANT = 22;
    private static final int IDX_PIG_VARIANT = 18;

    private MobDisguiseSyntheticMetadata() {
    }

    public static List<EntityData<?>> fragment(ClientVersion cv, EntityType mob, MobDisguiseOptions o) {
        List<EntityData<?>> list = new ArrayList<>();
        if (o.displayName() != null) {
            list.add(new EntityData<>(IDX_CUSTOM_NAME, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(o.displayName())));
            list.add(new EntityData<>(IDX_CUSTOM_NAME_VISIBLE, EntityDataTypes.BOOLEAN, true));
        }
        if (mob == EntityType.SLIME || mob == EntityType.MAGMA_CUBE) {
            int sz = o.size() != null ? o.effectiveSlimeOrMagmaSize() : 1;
            list.add(new EntityData<>(IDX_SLIME_SIZE, EntityDataTypes.INT, sz));
        }
        if (mob == EntityType.PHANTOM && o.size() != null) {
            list.add(new EntityData<>(IDX_PHANTOM_SIZE, EntityDataTypes.INT, o.effectiveSlimeOrMagmaSize()));
        }
//        o.variant().ifPresent(v -> addVariantEntries(list, cv, mob, v));
        return list;
    }

    private static void addVariantEntries(List<EntityData<?>> list, ClientVersion cv, EntityType mob, String raw) {
        String key = raw.trim();
        if (key.isEmpty()) {
            return;
        }
        String u = key.toUpperCase(Locale.ROOT);
        try {
            switch (mob) {
                case SHEEP -> {
                    DyeColor dye = DyeColor.valueOf(u);
                    list.add(new EntityData<>(IDX_SHEEP_WOOL, EntityDataTypes.BYTE, (byte) (dye.ordinal() & 0x0F)));
                }
                case COW -> {
                    CowVariant cvv = resolveCow(u);
                    if (cvv != null) {
                        list.add(new EntityData<>(IDX_COW_VARIANT, EntityDataTypes.COW_VARIANT, cvv));
                    }
                }
                case CAT -> {
                    CatVariant cat = resolveCat(u);
                    if (cat != null) {
                        list.add(new EntityData<>(IDX_CAT_VARIANT, EntityDataTypes.TYPED_CAT_VARIANT, cat));
                    }
                }
                case WOLF -> {
                    WolfVariant wv = resolveWolf(u);
                    if (wv != null) {
                        list.add(new EntityData<>(IDX_WOLF_VARIANT, EntityDataTypes.TYPED_WOLF_VARIANT, wv));
                    }
                }
                case AXOLOTL -> {
                    AxolotlVariant av = resolveAxolotl(u);
                    if (av != null) {
                        list.add(new EntityData<>(IDX_AXOLOTL_VARIANT, EntityDataTypes.INT, av.getId(cv)));
                    }
                }
                case FROG -> {
                    FrogVariant fv = resolveFrog(u);
                    if (fv != null) {
                        list.add(new EntityData<>(IDX_FROG_VARIANT, EntityDataTypes.TYPED_FROG_VARIANT, fv));
                    }
                }
                case CHICKEN -> {
                    ChickenVariant ch = resolveChicken(u);
                    if (ch != null) {
                        list.add(new EntityData<>(IDX_CHICKEN_VARIANT, EntityDataTypes.CHICKEN_VARIANT, ch));
                    }
                }
                case PIG -> {
                    PigVariant pv = resolvePig(u);
                    if (pv != null) {
                        list.add(new EntityData<>(IDX_PIG_VARIANT, EntityDataTypes.PIG_VARIANT, pv));
                    }
                }
                default -> {
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static CowVariant resolveCow(String u) {
        return switch (u) {
            case "COLD" -> CowVariants.COLD;
            case "TEMPERATE" -> CowVariants.TEMPERATE;
            case "WARM" -> CowVariants.WARM;
            default -> null;
        };
    }

    private static CatVariant resolveCat(String u) {
        return switch (u) {
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
        return switch (u) {
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

    private static AxolotlVariant resolveAxolotl(String u) {
        return switch (u) {
            case "LUCY" -> AxolotlVariants.LUCY;
            case "WILD" -> AxolotlVariants.WILD;
            case "GOLD" -> AxolotlVariants.GOLD;
            case "CYAN" -> AxolotlVariants.CYAN;
            case "BLUE" -> AxolotlVariants.BLUE;
            default -> null;
        };
    }

    private static FrogVariant resolveFrog(String u) {
        return switch (u) {
            case "TEMPERATE" -> FrogVariants.TEMPERATE;
            case "COLD" -> FrogVariants.COLD;
            case "WARM" -> FrogVariants.WARM;
            default -> null;
        };
    }

    private static ChickenVariant resolveChicken(String u) {
        return switch (u) {
            case "COLD" -> ChickenVariants.COLD;
            case "TEMPERATE" -> ChickenVariants.TEMPERATE;
            case "WARM" -> ChickenVariants.WARM;
            default -> null;
        };
    }

    private static PigVariant resolvePig(String u) {
        return switch (u) {
            case "COLD" -> PigVariants.COLD;
            case "TEMPERATE" -> PigVariants.TEMPERATE;
            case "WARM" -> PigVariants.WARM;
            default -> null;
        };
    }
}
