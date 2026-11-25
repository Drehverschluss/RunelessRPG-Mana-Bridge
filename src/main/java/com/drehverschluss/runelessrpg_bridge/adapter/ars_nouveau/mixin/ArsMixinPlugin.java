package com.drehverschluss.runelessrpg_bridge.adapter.ars_nouveau.mixin;

import com.drehverschluss.runelessrpg_bridge.core.mixin.AdapterMixinPlugin;

/** Mixins of the Ars adapter apply only while Ars Nouveau is loaded. */
public final class ArsMixinPlugin extends AdapterMixinPlugin {
    @Override
    protected String modId() {
        return "ars_nouveau";
    }
}
