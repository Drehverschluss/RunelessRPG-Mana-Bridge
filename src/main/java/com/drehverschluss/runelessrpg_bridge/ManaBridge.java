package com.drehverschluss.runelessrpg_bridge;

import com.drehverschluss.runelessrpg_bridge.core.Bridge;
import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ManaBridge.MODID)
public class ManaBridge {
    public static final String MODID = "runelessrpg_bridge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ManaBridge(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RunelessRPG Mana Bridge {} loaded", modContainer.getModInfo().getVersion());

        // Mana mods the bridge can connect to: only looked up by mod id, none of their classes is touched here
        String present = Stream.of("irons_spellbooks", "ars_nouveau")
                .map(id -> ModList.get().getModContainerById(id)
                        .map(c -> c.getModInfo().getDisplayName() + " " + c.getModInfo().getVersion())
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        LOGGER.info("Mana mods present: {}", present.isEmpty() ? "none (nothing to do)" : present);

        Bridge.init(modEventBus, modContainer);
    }
}
