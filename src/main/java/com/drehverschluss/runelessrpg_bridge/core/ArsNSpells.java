package com.drehverschluss.runelessrpg_bridge.core;

import java.util.Locale;

/**
 * Switches of Ars 'n Spells (mod id {@code ars_n_spells}), read by reflection without a dependency (plan 9.1). Its
 * server config {@code ars_n_spells-server.toml} has {@code enable_mana_unification} and {@code mana_unification_mode}
 * ({@code iss_primary}, {@code ars_primary}, {@code hybrid}, {@code separate}, {@code disabled}). Both the Iron's and the
 * Ars adapter ask here. If a value cannot be read, the unification counts as active and as neither ISS_PRIMARY nor
 * ARS_PRIMARY (the safe side: both adapters off).
 */
public final class ArsNSpells {
    private static final String CONFIG = "com.otectus.arsnspells.config.AnsConfig";

    private ArsNSpells() {}

    /** @return the mode in lower case, or "" if the unification is off; throws if it cannot be read */
    private static String mode() throws ReflectiveOperationException {
        boolean on = Reflect.bool(Reflect.staticValue(CONFIG, "ENABLE_MANA_UNIFICATION"));
        return on ? String.valueOf(Reflect.staticValue(CONFIG, "MANA_UNIFICATION_MODE")).trim().toLowerCase(Locale.ROOT) : "";
    }

    /** @return true while the mana unification is on in a mode that joins the pools (not separate or disabled) */
    public static boolean unificationActive() {
        try {
            String mode = mode();
            return !mode.isEmpty() && !mode.equals("separate") && !mode.equals("disabled");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return true;
        }
    }

    /** @return true while the mana unification is on in the mode {@code iss_primary} (Iron's is the one pool) */
    public static boolean issPrimary() {
        return modeIs("iss_primary");
    }

    /**
     * @return true while the mana unification is on in the mode {@code ars_primary} (Ars Nouveau is the one pool and Iron's
     *         reads and writes it)
     * @since 0.1.0 (phase C4)
     */
    public static boolean arsPrimary() {
        return modeIs("ars_primary");
    }

    /** @return the mode for logs and change detection ("" = unification off, "?" = cannot be read) */
    public static String describe() {
        try {
            String mode = mode();
            return mode.isEmpty() ? "off" : mode;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return "?";
        }
    }

    private static boolean modeIs(String wanted) {
        try {
            return mode().equals(wanted);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }
}
