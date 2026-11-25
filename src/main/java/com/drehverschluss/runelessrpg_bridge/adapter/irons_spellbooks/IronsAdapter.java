package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks;

import com.drehverschluss.runelessrpg.api.ManaStorage;
import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.api.AdapterStatus;
import com.drehverschluss.runelessrpg_bridge.api.BridgeBonus;
import com.drehverschluss.runelessrpg_bridge.api.ConflictCheck;
import com.drehverschluss.runelessrpg_bridge.api.ManaAdapter;
import com.drehverschluss.runelessrpg_bridge.core.ArsNSpells;
import com.drehverschluss.runelessrpg_bridge.core.Attributes;
import com.drehverschluss.runelessrpg_bridge.core.BridgeConfig;
import com.drehverschluss.runelessrpg_bridge.core.Reflect;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter for Iron's Spells 'n Spellbooks: the description part. This class uses no class of Iron's and is created
 * also while Iron's is missing (its config section stays). The hooks that use Iron's classes ({@link IronsHooks},
 * {@link IronsEvents}, {@link IronsLeech}, {@link IronsStorage}, the mixins) are only reached from methods the core calls
 * while Iron's is loaded. Iron's follows the leading pool or leads it itself ({@code manaSystem = irons_spellbooks}).
 */
public final class IronsAdapter implements ManaAdapter {
    public static final String ID = "irons_spellbooks";
    public static final String MAX_MANA = "irons_spellbooks:max_mana";
    public static final String MANA_REGEN = "irons_spellbooks:mana_regen";

    static ModConfigSpec.BooleanValue DISABLE_OWN_REGEN;
    static ModConfigSpec.BooleanValue IGNORE_OWN_RESPAWN_RULE;
    static ModConfigSpec.BooleanValue SPELL_MANA_LEECH;

    static AdapterStatus status;

    @Override
    public String modId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Iron's Spells 'n Spellbooks";
    }

    /** Base value of {@code irons_spellbooks:max_mana} (default 100). */
    @Override
    public double baseMaxMana(@Nullable Player player) {
        return Attributes.baseValue(player, MAX_MANA, 100.0);
    }

    @Override
    public boolean canLead() {
        return true;
    }

    /** Iron's mana as RunelessRPG's storage (only called while Iron's is loaded). */
    @Override
    public ManaStorage createStorage() {
        return IronsStorage.INSTANCE;
    }

    /**
     * Iron's bonuses above its base values, without this bridge's modifiers. {@code max_mana} by operation (Iron's
     * mana); {@code mana_regen} is a factor on Iron's regeneration (base 1.0), so its flat and base-percent modifiers
     * together become one percent ((base + flat) × (1 + percent) ÷ base − 1) and its total modifiers stay a total factor.
     */
    @Override
    public BridgeBonus readBonus(Player player) {
        double[] max = Attributes.modifiersByOperation(player, MAX_MANA, ManaBridge.MODID);
        double[] regen = Attributes.modifiersByOperation(player, MANA_REGEN, ManaBridge.MODID);
        double regenBaseValue = Attributes.baseValue(player, MANA_REGEN, 1.0);
        double regenPercent = regenBaseValue > 0.0 ? (regenBaseValue + regen[0]) * (1.0 + regen[1]) / regenBaseValue - 1.0 : 0.0;
        return new BridgeBonus(max[0], max[1], max[2], 0.0, regenPercent, regen[2]);
    }

    /**
     * While Iron's leads: the other mods' bonuses as modifiers of the same operation on {@code irons_spellbooks:max_mana}
     * and {@code irons_spellbooks:mana_regen}. Flat regeneration is paid by RunelessRPG ({@link IronsStorage#bonusRegenPerSecond}).
     */
    @Override
    public void applyBonus(Player player, BridgeBonus bonus) {
        Attributes.applyBridge(player, MAX_MANA, MANA_REGEN, null, bonus);
    }

    @Override
    public void defineConfig(ModConfigSpec.Builder b) {
        DISABLE_OWN_REGEN = b.comment("While Iron's does not lead: turn off Iron's own mana regeneration for players; the leading pool regenerates.")
                .translation(BridgeConfig.key("disableOwnRegen"))
                .define("disableOwnRegen", true);
        IGNORE_OWN_RESPAWN_RULE = b.comment("While Iron's does not lead: Iron's does not set the mana after a respawn (its manaSpawnPercent); the leading pool decides.")
                .translation(BridgeConfig.key("ignoreOwnRespawnRule"))
                .define("ignoreOwnRespawnRule", true);
        SPELL_MANA_LEECH = b.comment(
                        "runelessrpg:spell_mana_leech also works on direct hits of Iron's spells (the player's own instant, channeled,",
                        "projectile and cone spells; no lingering zones, summons or damage over time). Entity tags",
                        "runelessrpg_bridge:spell_leech/direct and runelessrpg_bridge:spell_leech/excluded change the rule.")
                .translation(BridgeConfig.key("spellManaLeech"))
                .define("spellManaLeech", true);
    }

    @Override
    public void init(IEventBus modBus, AdapterStatus status) {
        IronsAdapter.status = status;
        NeoForge.EVENT_BUS.register(IronsEvents.class);
        modBus.addListener(FMLCommonSetupEvent.class, event -> IronsLeech.register());
    }

    @Override
    public List<String> requiredClasses() {
        return List.of(
                "io.redspace.ironsspellbooks.api.magic.MagicData",
                "io.redspace.ironsspellbooks.api.spells.AbstractSpell",
                "io.redspace.ironsspellbooks.api.spells.CastResult",
                "io.redspace.ironsspellbooks.api.spells.CastSource",
                "io.redspace.ironsspellbooks.api.events.SpellOnCastEvent",
                "io.redspace.ironsspellbooks.api.events.ChangeManaEvent",
                "io.redspace.ironsspellbooks.api.registry.SpellRegistry",
                "io.redspace.ironsspellbooks.capabilities.magic.MagicManager",
                "io.redspace.ironsspellbooks.damage.SpellDamageSource",
                "io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile",
                "io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile",
                "io.redspace.ironsspellbooks.entity.spells.AoeEntity");
    }

    @Override
    public String mixinConfig() {
        return "runelessrpg_bridge.irons_spellbooks.mixins.json";
    }

    @Override
    public List<ResourceLocation> hudLayers() {
        return List.of(ResourceLocation.fromNamespaceAndPath(ID, "mana_overlay"));
    }

    @Override
    public boolean isCastingItem(ItemStack stack) {
        return !stack.isEmpty() && IronsHooks.isCastingItem(stack);
    }

    /**
     * Known bridges (checked with their jars, plan 9.1). Each switch is read by reflection; if it cannot be read, the
     * bridge counts as active.
     */
    @Override
    public List<ConflictCheck> conflicts() {
        return List.of(
                // Ars 'n Spells: only while its mana unification is on (ISS_PRIMARY, ARS_PRIMARY, HYBRID); in ISS_PRIMARY
                // Iron's is its one pool, so this adapter stays on in a chain (chainWithArsNSpells, plan 9.1)
                new ConflictCheck("ars_n_spells", ArsNSpells::unificationActive,
                        () -> BridgeConfig.get(BridgeConfig.CHAIN_WITH_ARS_N_SPELLS, true) && ArsNSpells.issPrimary()),
                // Ars Mana Bridge: no switch, always active
                ConflictCheck.always("arsmanabridge"),
                // Mana Unifier (Ars <-> Iron's through events, Ars is its pool): no switch, always active
                ConflictCheck.always("manaunifier"),
                // Mana Transform (Iron's <-> Dragon Survival): while ENABLED
                new ConflictCheck("mana_transform", () -> {
                    try {
                        return Reflect.bool(Reflect.staticCall("by.sapphirus.manatransform.config.ManaTransformConfig", "enabled"));
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                        return true;
                    }
                }),
                // Unified Mana: while its Iron's integration is on
                new ConflictCheck("unifiedmana", () -> {
                    try {
                        return Reflect.bool(Reflect.staticValue("com.unifiedmana.config.UnifiedManaConfig", "IRON_SPELLS_ENABLED"));
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                        return true;
                    }
                }));
    }

    /** Iron's follows the leading pool: the switch of Iron's own mana applies. */
    static boolean active() {
        return status != null && status.isActive();
    }

    /** Iron's is connected (leading or following): costs, leech and converted displays apply. */
    static boolean connected() {
        return status != null && status.isConnected();
    }

    static boolean disableOwnRegen() {
        return BridgeConfig.get(DISABLE_OWN_REGEN, true);
    }

    static boolean ignoreOwnRespawnRule() {
        return BridgeConfig.get(IGNORE_OWN_RESPAWN_RULE, true);
    }

    static boolean spellManaLeech() {
        return BridgeConfig.get(SPELL_MANA_LEECH, true);
    }
}
