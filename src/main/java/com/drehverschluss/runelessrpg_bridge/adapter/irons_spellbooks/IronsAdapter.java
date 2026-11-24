package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks;

import com.drehverschluss.runelessrpg_bridge.api.AdapterStatus;
import com.drehverschluss.runelessrpg_bridge.api.ConflictCheck;
import com.drehverschluss.runelessrpg_bridge.api.ManaAdapter;
import com.drehverschluss.runelessrpg_bridge.core.Attributes;
import com.drehverschluss.runelessrpg_bridge.core.BridgeConfig;
import com.drehverschluss.runelessrpg_bridge.core.Reflect;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter for Iron's Spells 'n Spellbooks. This class uses no class of Iron's itself; the hooks that do
 * ({@link IronsHooks}, {@link IronsEvents}, {@link IronsLeech}, the mixins) are only loaded through it while Iron's is
 * present. In this version Iron's always follows RunelessRPG's pool.
 */
public final class IronsAdapter implements ManaAdapter {
    public static final String ID = "irons_spellbooks";
    public static final String MAX_MANA = "irons_spellbooks:max_mana";

    static ModConfigSpec.BooleanValue DISABLE_OWN_REGEN;
    static ModConfigSpec.BooleanValue IGNORE_OWN_RESPAWN_RULE;
    static ModConfigSpec.BooleanValue SPELL_MANA_LEECH;

    static AdapterStatus status;

    @Override
    public String modId() {
        return ID;
    }

    /** Base value of {@code irons_spellbooks:max_mana} (default 100). */
    @Override
    public double baseMaxMana(@Nullable Player player) {
        return Attributes.baseValue(player, MAX_MANA, 100.0);
    }

    @Override
    public void defineConfig(ModConfigSpec.Builder b) {
        DISABLE_OWN_REGEN = b.comment("Turn off Iron's own mana regeneration for players; RunelessRPG regenerates the shared pool.")
                .translation(BridgeConfig.key("disableOwnRegen"))
                .define("disableOwnRegen", true);
        IGNORE_OWN_RESPAWN_RULE = b.comment("Iron's does not set the mana after a respawn (its manaSpawnPercent); RunelessRPG refills the pool as usual.")
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
                // Ars 'n Spells: only while its mana unification is on (ISS_PRIMARY, ARS_PRIMARY, HYBRID)
                new ConflictCheck("ars_n_spells", () -> {
                    try {
                        boolean on = Reflect.bool(Reflect.staticValue("com.otectus.arsnspells.config.AnsConfig", "ENABLE_MANA_UNIFICATION"));
                        String mode = String.valueOf(Reflect.staticValue("com.otectus.arsnspells.config.AnsConfig", "MANA_UNIFICATION_MODE"))
                                .toLowerCase(Locale.ROOT);
                        return on && !mode.equals("separate") && !mode.equals("disabled");
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                        return true;
                    }
                }),
                // Ars Mana Bridge: no switch, always active
                ConflictCheck.always("arsmanabridge"),
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

    static boolean active() {
        return status != null && status.isActive();
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
