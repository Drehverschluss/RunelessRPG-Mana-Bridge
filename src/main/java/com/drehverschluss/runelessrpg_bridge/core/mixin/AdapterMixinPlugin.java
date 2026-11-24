package com.drehverschluss.runelessrpg_bridge.core.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Base of the mixin plugin of every adapter: its mixins are only applied while the adapter's mod is loaded, and every
 * applied mixin is recorded with the injectors that really found their target ({@link MixinRecord}), for the self test.
 * Injectors use {@code require = 0}, so a changed mod never stops the game; the self test turns the adapter off instead.
 */
public abstract class AdapterMixinPlugin implements IMixinConfigPlugin {
    private static final List<String> INJECTORS = List.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;");

    /** @return the mod id of the adapter's mod */
    protected abstract String modId();

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean loaded = LoadingModList.get() != null && LoadingModList.get().getModFileById(modId()) != null;
        if (loaded) {
            MixinRecord.expect(modId(), mixinClassName, targetClassName);
        }
        return loaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        List<String> handlers = new java.util.ArrayList<>();
        for (MethodNode method : mixinInfo.getClassNode(0).methods) {
            if (isInjector(method)) {
                handlers.add(method.name);
            }
        }
        // MixinExtras inserts some wrappers after this call: the self test looks at the class node later
        MixinRecord.applied(modId(), mixinClassName, targetClassName, handlers, targetClass);
    }

    private static boolean isInjector(MethodNode method) {
        if (method.visibleAnnotations == null) {
            return false;
        }
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (INJECTORS.contains(annotation.desc)) {
                return true;
            }
        }
        return false;
    }

    /** True if the target calls a merged handler whose name ends with the handler's name (Mixin renames handlers). */
    static boolean isCalled(ClassNode target, String handlerName) {
        String suffix = "$" + handlerName;
        for (MethodNode method : target.methods) {
            if (method.instructions == null) {
                continue;
            }
            for (var insn : method.instructions) {
                if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && (call.name.equals(handlerName) || call.name.endsWith(suffix))
                        && call.owner.equals(target.name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
