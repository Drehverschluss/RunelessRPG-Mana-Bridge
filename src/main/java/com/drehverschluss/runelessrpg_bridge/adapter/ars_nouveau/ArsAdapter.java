package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau;

import com.drehverschluss.runelessrpg.api.ManaStorage;
import com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.client.ArsClientHooks;
import com.drehverschluss.runelessrpg_bridge.api.AdapterStatus;
import com.drehverschluss.runelessrpg_bridge.api.BridgeBonus;
import com.drehverschluss.runelessrpg_bridge.api.ConflictCheck;
import com.drehverschluss.runelessrpg_bridge.api.LeadInfo;
import com.drehverschluss.runelessrpg_bridge.api.ManaAdapter;
import com.drehverschluss.runelessrpg_bridge.core.ArsNSpells;
import com.drehverschluss.runelessrpg_bridge.core.Attributes;
import com.drehverschluss.runelessrpg_bridge.core.BridgeConfig;
import com.drehverschluss.runelessrpg_bridge.core.Reflect;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter for Ars Nouveau: the description part. This class uses no class of Ars and is created also while Ars is
 * missing (its config section stays). The hooks that use Ars classes ({@link ArsHooks}, {@link ArsEvents},
 * {@link ArsLeech}, {@link ArsClientHooks}, the mixins) are only reached from methods the core calls while Ars is loaded.
 * Ars follows the leading pool (RunelessRPG or Iron's) or leads it itself ({@code manaSystem = ars_nouveau}): then Ars' mana is
 * RunelessRPG's storage ({@link ArsStorage}), Ars runs as without this bridge (its regeneration, progress, gear, familiars'
 * reserve and rules for respawn and death) and the other mods' bonuses become modifiers on Ars' own attributes.
 */
public final class ArsAdapter implements ManaAdapter {
    public static final String ID = "ars_nouveau";
    public static final String MAX_MANA = "ars_nouveau:ars_nouveau.perk.max_mana";
    public static final String MANA_REGEN = "ars_nouveau:ars_nouveau.perk.mana_regen";
    /** Default glyphs after which damage comes from a lasting zone or orbit (no Spell Mana Leech). */
    static final List<String> DEFAULT_ZONE_GLYPHS = List.of("ars_nouveau:glyph_linger", "ars_nouveau:glyph_wall", "ars_nouveau:glyph_orbit");

    static ModConfigSpec.BooleanValue BRIDGE_PROGRESSION;
    static ModConfigSpec.BooleanValue APPLY_FAMILIAR_RESERVE;
    static ModConfigSpec.BooleanValue DISABLE_OWN_REGEN;
    static ModConfigSpec.BooleanValue SPELL_MANA_LEECH;
    static ModConfigSpec.ConfigValue<List<? extends String>> LEECH_ZONE_GLYPHS;
    static ModConfigSpec.BooleanValue FORWARD_INSUFFICIENT_FLASH;

    static AdapterStatus status;
    /** Players for whom Ars leads the pool (server); the first bonus while leading computes Ars' max again. */
    static final Set<UUID> LEADING = ConcurrentHashMap.newKeySet();

    @Override
    public String modId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Ars Nouveau";
    }

    /** Ars' {@code baseMax} from its server config (default 100); its max mana attribute has the base value 0. */
    @Override
    public double baseMaxMana(@Nullable Player player) {
        return ArsHooks.baseMaxMana();
    }

    /** Ars' {@code baseMax} and {@code baseRegen} (per second) from its server config; glyphs and book tier add to both. */
    @Override
    public LeadInfo leadInfo(@Nullable Player player) {
        return new LeadInfo(ArsHooks.baseMaxMana(), ArsHooks.baseRegen(), 0.0);
    }

    /** Gear bonuses: modifiers on Ars' max mana and mana regen attributes and changes of Ars addons in its two events. */
    @Override
    public BridgeBonus readBonus(Player player) {
        return ArsHooks.readGearBonus(player);
    }

    /** Ars progress (glyphs, book tier, switch bridgeProgression) and the familiars' reserve (switch applyFamiliarReserve). */
    @Override
    public BridgeBonus readExtraBonus(Player player) {
        return ArsHooks.readExtraBonus(player, bridgeProgression(), applyFamiliarReserve());
    }

    @Override
    public boolean canLead() {
        return true;
    }

    /** Ars' mana as RunelessRPG's storage (only called while Ars is loaded). */
    @Override
    public ManaStorage createStorage() {
        return ArsStorage.INSTANCE;
    }

    /**
     * While Ars leads: the other mods' bonuses as modifiers of the same operation on {@code ars_nouveau.perk.max_mana} and
     * {@code ars_nouveau.perk.mana_regen} (flat regeneration included, Ars regenerates it); Ars' max is computed again at
     * once when they change. No reserve modifier: Ars takes its familiars' reserve off its max itself.
     */
    @Override
    public void applyBonus(Player player, BridgeBonus bonus) {
        boolean changed = Attributes.applyBridge(player, MAX_MANA, MANA_REGEN, MANA_REGEN, bonus);
        if (!leading()) {
            LEADING.remove(player.getUUID());
            return;
        }
        // the first bonus while Ars leads, or a changed one: Ars' max at once (Ars itself recomputes it every few ticks)
        if (LEADING.add(player.getUUID()) | changed) {
            ArsHooks.refreshMax(player);
        }
    }

    @Override
    public void defineConfig(ModConfigSpec.Builder b) {
        BRIDGE_PROGRESSION = b.comment(
                        "Ars progress counts for the leading pool too while Ars does not lead: max mana and regeneration from learned",
                        "glyphs and the spell book tier (Ars' glyphmax, tierMax, glyphRegen, tierRegen), converted with manaFactor",
                        "(50 glyphs and tier 3: +180 max mana and +3.9/s in RunelessRPG mana). Off: only gear counts. While Ars leads,",
                        "its progress works as without this bridge.")
                .translation(BridgeConfig.key("bridgeProgression"))
                .define("bridgeProgression", true);
        APPLY_FAMILIAR_RESERVE = b.comment(
                        "Mana that Ars familiars reserve is taken from the shared max mana (as a percent modifier on the leading pool,",
                        "so the bar shows it). While Ars leads, Ars takes the reserve off its max itself.")
                .translation(BridgeConfig.key("applyFamiliarReserve"))
                .define("applyFamiliarReserve", true);
        DISABLE_OWN_REGEN = b.comment("While Ars does not lead: turn off Ars' own mana regeneration for players; the leading pool regenerates.")
                .translation(BridgeConfig.key("disableOwnRegen"))
                .define("disableOwnRegen", true);
        SPELL_MANA_LEECH = b.comment(
                        "runelessrpg:spell_mana_leech also works on direct hits of Ars spells the player cast himself (projectile, touch,",
                        "self, underfoot, burst, delay). Not: damage after a glyph in leechZoneGlyphs, runes, turrets, summons, effects.")
                .translation(BridgeConfig.key("spellManaLeech"))
                .define("spellManaLeech", true);
        LEECH_ZONE_GLYPHS = b.comment(
                        "Glyphs after which damage counts as a lasting zone or orbit, so Spell Mana Leech does not apply (glyph IDs).")
                .translation(BridgeConfig.key("leechZoneGlyphs"))
                .defineListAllowEmpty("leechZoneGlyphs", DEFAULT_ZONE_GLYPHS, () -> "ars_nouveau:glyph_", o -> o instanceof String);
    }

    @Override
    public void defineClientConfig(ModConfigSpec.Builder b) {
        FORWARD_INSUFFICIENT_FLASH = b.comment(
                        "Ars Nouveau's mana bar flashes red whenever a spell of any mod fails for lack of mana (also Spell Engine and",
                        "Iron's spells), like it does for Ars spells. Only while Ars is connected to the shared pool.")
                .translation(BridgeConfig.key("forwardInsufficientFlash"))
                .define("forwardInsufficientFlash", true);
    }

    @Override
    public void init(IEventBus modBus, AdapterStatus status) {
        ArsAdapter.status = status;
        NeoForge.EVENT_BUS.register(ArsEvents.class);
        modBus.addListener(FMLCommonSetupEvent.class, event -> ArsLeech.register());
    }

    @Override
    public void initClient(IEventBus modBus, AdapterStatus status) {
        ArsClientHooks.register();
    }

    @Override
    public List<String> requiredClasses() {
        return List.of(
                "com.hollingsworth.arsnouveau.common.capability.ManaCap",
                "com.hollingsworth.arsnouveau.common.event.ManaCapEvents",
                "com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry",
                "com.hollingsworth.arsnouveau.setup.config.ServerConfig",
                "com.hollingsworth.arsnouveau.api.util.ManaUtil",
                "com.hollingsworth.arsnouveau.api.spell.SpellResolver",
                "com.hollingsworth.arsnouveau.api.spell.SpellContext",
                "com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart",
                "com.hollingsworth.arsnouveau.api.spell.wrapped_caster.LivingCaster",
                "com.hollingsworth.arsnouveau.api.spell.wrapped_caster.PlayerCaster",
                "com.hollingsworth.arsnouveau.api.event.SpellDamageEvent",
                "com.hollingsworth.arsnouveau.api.event.MaxManaCalcEvent",
                "com.hollingsworth.arsnouveau.api.event.ManaRegenCalcEvent",
                "com.hollingsworth.arsnouveau.api.client.IDisplayMana",
                "com.hollingsworth.arsnouveau.api.item.ICasterTool");
    }

    @Override
    public String mixinConfig() {
        return "runelessrpg_bridge.ars_nouveau.mixins.json";
    }

    @Override
    public List<ResourceLocation> hudLayers() {
        return List.of(ResourceLocation.fromNamespaceAndPath(ID, "mana_hud"));
    }

    @Override
    public boolean isCastingItem(ItemStack stack) {
        return !stack.isEmpty() && ArsHooks.isCastingItem(stack);
    }

    /**
     * Known bridges on the Ars side (checked with their jars, plan 9.1). Ars 'n Spells: active while its mana unification
     * is on in a joining mode; in ARS_PRIMARY Ars is its one pool, so this adapter stays on in a chain
     * ({@code chainWithArsNSpells}; in ISS_PRIMARY only the Iron's adapter runs). Ars Mana Bridge and Mana Unifier (no
     * switch): always; Unified Mana: while its Ars integration is on.
     */
    @Override
    public List<ConflictCheck> conflicts() {
        return List.of(
                new ConflictCheck("ars_n_spells", ArsNSpells::unificationActive,
                        () -> BridgeConfig.get(BridgeConfig.CHAIN_WITH_ARS_N_SPELLS, true) && ArsNSpells.arsPrimary()),
                ConflictCheck.always("arsmanabridge"),
                ConflictCheck.always("manaunifier"),
                new ConflictCheck("unifiedmana", () -> {
                    try {
                        return Reflect.bool(Reflect.staticValue("com.unifiedmana.config.UnifiedManaConfig", "ARS_NOUVEAU_ENABLED"));
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                        return true;
                    }
                }));
    }

    /** Ars follows the leading pool: the switch of Ars' own mana applies. */
    static boolean active() {
        return status != null && status.isActive();
    }

    /** Ars is connected: costs, leech and converted displays apply. */
    static boolean connected() {
        return status != null && status.isConnected();
    }

    /** Ars is connected and leads the pool ({@code manaSystem = ars_nouveau}). */
    static boolean leading() {
        return status != null && status.isConnected() && status.isLeading();
    }

    static boolean bridgeProgression() {
        return BridgeConfig.get(BRIDGE_PROGRESSION, true);
    }

    static boolean applyFamiliarReserve() {
        return BridgeConfig.get(APPLY_FAMILIAR_RESERVE, true);
    }

    static boolean disableOwnRegen() {
        return BridgeConfig.get(DISABLE_OWN_REGEN, true);
    }

    static boolean spellManaLeech() {
        return BridgeConfig.get(SPELL_MANA_LEECH, true);
    }

    public static boolean forwardInsufficientFlash() {
        return BridgeConfig.get(FORWARD_INSUFFICIENT_FLASH, true);
    }

    /** @return the glyph IDs of {@code leechZoneGlyphs} (lower case); the defaults while the config is not loaded */
    static Set<String> zoneGlyphs() {
        List<? extends String> list;
        try {
            list = LEECH_ZONE_GLYPHS == null ? DEFAULT_ZONE_GLYPHS : LEECH_ZONE_GLYPHS.get();
        } catch (IllegalStateException e) {
            list = DEFAULT_ZONE_GLYPHS;
        }
        return list.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
}
