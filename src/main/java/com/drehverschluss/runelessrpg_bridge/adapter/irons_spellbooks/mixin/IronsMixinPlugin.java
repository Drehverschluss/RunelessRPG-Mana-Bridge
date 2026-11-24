package com.drehverschluss.runelessrpg_bridge.adapter.irons_spellbooks.mixin;

import com.drehverschluss.runelessrpg_bridge.core.mixin.AdapterMixinPlugin;

/** Mixins of the Iron's adapter apply only while Iron's Spells 'n Spellbooks is loaded. */
public final class IronsMixinPlugin extends AdapterMixinPlugin {
    @Override
    protected String modId() {
        return "irons_spellbooks";
    }
}
