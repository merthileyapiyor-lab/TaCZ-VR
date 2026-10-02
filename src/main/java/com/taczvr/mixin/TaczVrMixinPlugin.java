package com.taczvr.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Leaves out the mixins that change or call into Vivecraft when it isn't installed (with Visor, or flat screen only).
 */
public class TaczVrMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> NEED_VIVECRAFT = Set.of(
            "com.taczvr.mixin.client.GameRendererMixin",
            "com.taczvr.mixin.client.SwingTrackerMixin",
            "com.taczvr.mixin.client.TeleportTrackerMixin",
            "com.taczvr.mixin.client.VRArmHelperMixin",
            "com.taczvr.mixin.client.VRDataMixin",
            "com.taczvr.mixin.client.VRPlayerModelAccessor",
            "com.taczvr.mixin.client.VRPlayerModelMixin",
            "com.taczvr.mixin.client.VRPlayerModelWithArmsMixin",
            "com.taczvr.mixin.client.VRRendererMixin");

    private boolean vivecraft;

    @Override
    public void onLoad(String mixinPackage) {
        this.vivecraft = FMLLoader.getLoadingModList().getModFileById("vivecraft") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.vivecraft || !NEED_VIVECRAFT.contains(mixinClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
