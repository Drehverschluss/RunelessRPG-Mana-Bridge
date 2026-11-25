package com.drehverschluss.runelessrpg_bridge.core.client;

import com.drehverschluss.runelessrpg.api.RunelessApi;
import com.drehverschluss.runelessrpg_bridge.core.AdapterHandle;
import com.drehverschluss.runelessrpg_bridge.core.Adapters;
import com.drehverschluss.runelessrpg_bridge.core.BridgeClientConfig;
import com.drehverschluss.runelessrpg_bridge.core.Pool;
import com.drehverschluss.runelessrpg_bridge.core.network.ClientBridgeState;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of the display (plan 5): which mana bars are shown ({@code manaDisplay}), the display unit (sets
 * RunelessRPG's display factor) and helpers that convert other mods' mana amounts into that unit.
 */
public final class DisplayManager {

    private DisplayManager() {}

    // ------------------------------------------------------------------ bars

    /** Mod ids of the bars chosen with {@code manaDisplay} (without the extra bar for debt). */
    public static Set<String> chosenBars() {
        Set<String> set = new LinkedHashSet<>();
        String leader = ClientBridgeState.leader();
        String choice = value(BridgeClientConfig.MANA_DISPLAY.get()).trim().toLowerCase(Locale.ROOT);
        if (choice.equals("all")) {
            for (AdapterHandle handle : Adapters.all()) {
                if (handle.isLeader() || handle.isActive() && !handle.adapter().hudLayers().isEmpty()) {
                    set.add(handle.modId());
                }
            }
            return set;
        }
        if (!choice.isEmpty() && !choice.equals("auto")) {
            AdapterHandle handle = Adapters.getOrNull(choice);
            if (handle != null && (handle.isLeader() || handle.isActive() && !handle.adapter().hudLayers().isEmpty())) {
                set.add(choice);
                return set;
            }
        }
        set.add(leader);
        return set;
    }

    /** Bars shown right now: the chosen ones, plus RunelessRPG's bar while in debt ({@code showRunelessBarOnDebt}). */
    public static Set<String> shownBars(@Nullable Player player) {
        Set<String> set = chosenBars();
        if (player != null && BridgeClientConfig.SHOW_RUNELESS_BAR_ON_DEBT.get() && RunelessApi.getMana(player) < 0.0) {
            set.add(Adapters.RUNELESS);
        }
        return set;
    }

    static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        ResourceLocation layer = event.getName();
        for (AdapterHandle handle : Adapters.all()) {
            if (!handle.adapter().hudLayers().contains(layer)) {
                continue;
            }
            // bars of adapters that are off show their own, separate pool: leave them alone
            if (!handle.isLeader() && !handle.isActive()) {
                return;
            }
            if (!shownBars(Minecraft.getInstance().player).contains(handle.modId())) {
                event.setCanceled(true);
            }
            return;
        }
    }

    /** @return true if the player holds a casting item of an active adapter other than {@code ownModId} */
    public static boolean holdsOtherCastingItem(Player player, String ownModId) {
        if (!BridgeClientConfig.SHOW_FOREIGN_BAR_WITH_SPELL_WEAPONS.get()) {
            return false;
        }
        for (AdapterHandle handle : Adapters.all()) {
            if (handle.modId().equals(ownModId) || !(handle.isLeader() || handle.isActive())) {
                continue;
            }
            for (ItemStack stack : player.getHandSlots()) {
                if (handle.adapter().isCastingItem(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ display unit

    /** Display units per RunelessRPG mana (plan 5.1), for {@link RunelessApi#setDisplayFactor}. */
    public static double displayFactor(@Nullable Player player) {
        if (player == null || !ClientBridgeState.received()) {
            return 1.0; // no bridge on the server: RunelessRPG shows its own unit
        }
        BridgeClientConfig.DisplayUnit unit = BridgeClientConfig.DISPLAY_UNIT.get();
        if (unit == BridgeClientConfig.DisplayUnit.RUNELESS) {
            return 1.0;
        }
        if (unit == BridgeClientConfig.DisplayUnit.AUTO) {
            // exactly one bar of a mod that does not lead (also RunelessRPG's while another mod leads): its unit
            Set<String> chosen = chosenBars();
            if (chosen.size() == 1) {
                String only = chosen.iterator().next();
                AdapterHandle handle = Adapters.getOrNull(only);
                if (handle != null && !handle.isLeader() && handle.isActive()) {
                    return 1.0 / Pool.factor(player, only);
                }
            }
        }
        return leaderUnit(player);
    }

    /** Unit of the leading pool: 1 for RunelessRPG, 1 ÷ factor for another mod (5 for Iron's by default). */
    private static double leaderUnit(Player player) {
        String leader = ClientBridgeState.leader();
        AdapterHandle handle = Adapters.getOrNull(leader);
        if (leader.equals(Adapters.RUNELESS) || handle == null || !handle.isConnected()) {
            return 1.0;
        }
        return 1.0 / Pool.factor(player, leader);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        refresh();
    }

    /**
     * Sets RunelessRPG's display factor if it differs from the current one: every client tick (client config changed)
     * and at once when the server's values arrive (login, config reload, leader change).
     */
    public static void refresh() {
        LocalPlayer player = Minecraft.getInstance().player;
        double factor = player == null ? 1.0 : displayFactor(player);
        if (factor != RunelessApi.getDisplayFactor()) {
            RunelessApi.setDisplayFactor(factor);
        }
    }

    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBridgeState.reset();
        RunelessApi.setDisplayFactor(1.0);
    }

    // ------------------------------------------------------------------ converting other mods' amounts

    /** @return true if amounts in other mods' tooltips are converted ({@code convertForeignTooltips}) */
    public static boolean convertTooltips() {
        return BridgeClientConfig.CONVERT_FOREIGN_TOOLTIPS.get();
    }

    /** An amount in the mod's own units, converted into the display unit: × factor × display factor. */
    public static double toDisplay(Player player, String modId, double ownAmount) {
        return ownAmount * Pool.factor(player, modId) * RunelessApi.getDisplayFactor();
    }

    /** At most two decimals without trailing zeros ("4", "7.5", "0.13"), like RunelessRPG's own displays. */
    public static String format(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.signum() == 0 ? "0" : rounded.toPlainString();
    }

    /** @return true if the value shows as a whole number */
    public static boolean isWhole(double value) {
        return Math.abs(value - Math.rint(value)) < 0.005;
    }

    /**
     * Text of {@code key} with the number in place of its first placeholder. Whole numbers keep the mod's own
     * translatable text with an integer; other numbers are filled into the mod's translated template.
     */
    public static MutableComponent numberText(String key, double value) {
        if (isWhole(value)) {
            return Component.translatable(key, (int) Math.rint(value));
        }
        String template = Language.getInstance().getOrDefault(key);
        String text = template.replaceFirst("%(\\d+\\$)?[sd]", java.util.regex.Matcher.quoteReplacement(format(value)));
        return Component.literal(text);
    }

    private static String value(@Nullable String s) {
        return s == null ? "" : s;
    }
}
