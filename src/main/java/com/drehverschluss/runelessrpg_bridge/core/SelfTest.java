package com.drehverschluss.runelessrpg_bridge.core;

import com.drehverschluss.runelessrpg_bridge.ManaBridge;
import com.drehverschluss.runelessrpg_bridge.core.mixin.MixinRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Self test of every following adapter at startup: required classes present, mixins applied, every injector found its
 * target. A failed adapter turns off with exactly one warning; the game always starts.
 * <p>
 * For tests the system property {@code runelessrpg_bridge.failSelfTest} (comma-separated mod ids) makes the test of these
 * adapters fail on purpose. Without the property nothing changes.
 */
public final class SelfTest {
    public static final String FAIL_PROPERTY = "runelessrpg_bridge.failSelfTest";

    private SelfTest() {}

    /** Common part (both sides): classes and common mixins. */
    static void runCommon() {
        for (AdapterHandle handle : Adapters.followers()) {
            String problem = check(handle);
            handle.setSelfTestProblem(problem);
            if (problem.isEmpty()) {
                ManaBridge.LOGGER.info("Adapter {}: self test passed ({} mixins)", handle.modId(), MixinRecord.entries(handle.modId()).size());
            } else {
                ManaBridge.LOGGER.warn("Adapter {} is turned off, its mod works as without this bridge. Self test failed: {}", handle.modId(), problem);
            }
        }
    }

    /** Client part: mixins of the client list. A failure turns the adapter off on this client only. */
    public static void runClient() {
        for (AdapterHandle handle : Adapters.followers()) {
            if (!handle.selfTestProblem().isEmpty()) {
                continue; // already off with its warning
            }
            String problem = checkMixins(handle);
            if (!problem.isEmpty()) {
                handle.setSelfTestProblem(problem);
                ManaBridge.LOGGER.warn("Adapter {} is turned off on this client. Self test failed: {}", handle.modId(), problem);
            }
        }
    }

    private static String check(AdapterHandle handle) {
        String forced = System.getProperty(FAIL_PROPERTY, "");
        if (Arrays.stream(forced.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).anyMatch(handle.modId()::equals)) {
            return "forced by the system property " + FAIL_PROPERTY;
        }
        List<String> missing = new ArrayList<>();
        for (String name : handle.adapter().requiredClasses()) {
            try {
                Class.forName(name, false, SelfTest.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            return "missing classes " + missing;
        }
        return checkMixins(handle);
    }

    private static String checkMixins(AdapterHandle handle) {
        if (handle.adapter().mixinConfig() == null) {
            return "";
        }
        List<MixinRecord.Entry> entries = MixinRecord.entries(handle.modId());
        if (entries.isEmpty()) {
            return "mixin config " + handle.adapter().mixinConfig() + " was not loaded";
        }
        List<String> problems = new ArrayList<>();
        for (MixinRecord.Entry entry : entries) {
            if (!entry.applied()) {
                // mixins are applied when the target class loads: load it now (without running it)
                try {
                    Class.forName(entry.target, false, SelfTest.class.getClassLoader());
                } catch (ClassNotFoundException | LinkageError e) {
                    problems.add("target " + entry.target + " not found");
                    continue;
                }
            }
            if (!entry.applied()) {
                problems.add(simple(entry.mixin) + " not applied to " + entry.target);
            } else if (!entry.missingHandlers().isEmpty()) {
                problems.add(simple(entry.mixin) + ": no target for " + entry.missingHandlers());
            }
        }
        return String.join("; ", problems);
    }

    private static String simple(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
