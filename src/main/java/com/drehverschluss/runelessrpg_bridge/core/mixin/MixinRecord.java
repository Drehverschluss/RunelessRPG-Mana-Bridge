package com.drehverschluss.runelessrpg_bridge.core.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.tree.ClassNode;

/**
 * What the mixin plugins saw: which mixins of an adapter were expected (accepted for a target) and which were applied,
 * with the injectors that did not find their target. Read by the self test.
 */
public final class MixinRecord {
    /** One mixin on one target. */
    public static final class Entry {
        public final String mixin;
        public final String target;
        volatile boolean applied;
        volatile List<String> handlers = List.of();
        volatile ClassNode targetNode;
        volatile String missingHandlers;

        Entry(String mixin, String target) {
            this.mixin = mixin;
            this.target = target;
        }

        public boolean applied() {
            return applied;
        }

        /** Injectors without a call in the target (checked once, then the class node is released). */
        public String missingHandlers() {
            if (missingHandlers == null) {
                ClassNode node = targetNode;
                StringBuilder missing = new StringBuilder();
                for (String handler : handlers) {
                    if (node == null || !AdapterMixinPlugin.isCalled(node, handler)) {
                        if (!missing.isEmpty()) {
                            missing.append(", ");
                        }
                        missing.append(handler);
                    }
                }
                missingHandlers = missing.toString();
                targetNode = null;
            }
            return missingHandlers;
        }

        public int handlers() {
            return handlers.size();
        }
    }

    private static final Map<String, Map<String, Entry>> BY_MOD = new ConcurrentHashMap<>();

    private MixinRecord() {}

    static void expect(String modId, String mixin, String target) {
        BY_MOD.computeIfAbsent(modId, k -> new ConcurrentHashMap<>()).computeIfAbsent(mixin + "@" + target, k -> new Entry(mixin, target));
    }

    static void applied(String modId, String mixin, String target, List<String> handlers, ClassNode targetNode) {
        Entry entry = BY_MOD.computeIfAbsent(modId, k -> new ConcurrentHashMap<>()).computeIfAbsent(mixin + "@" + target, k -> new Entry(mixin, target));
        entry.handlers = List.copyOf(handlers);
        entry.targetNode = targetNode;
        entry.missingHandlers = null;
        entry.applied = true;
    }

    /** @return the recorded mixins of the adapter's mod */
    public static List<Entry> entries(String modId) {
        Map<String, Entry> map = BY_MOD.get(modId);
        return map == null ? List.of() : new ArrayList<>(map.values());
    }
}
