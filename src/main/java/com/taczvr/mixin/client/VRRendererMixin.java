package com.taczvr.mixin.client;

import com.taczvr.client.ScopeView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.provider.VRRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Vivecraft only renders its spyglass pass for spyglasses. Also render it while looking through a TACZ scope.
 */
@Mixin(value = VRRenderer.class, remap = false)
public abstract class VRRendererMixin {

    @Inject(method = "getRenderPasses(Z)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void taczvr$addScopePass(boolean includeNonRendered, CallbackInfoReturnable<List<RenderPass>> cir) {
        if (!ScopeView.isViewing()) {
            return;
        }
        List<RenderPass> passes = cir.getReturnValue();
        if (passes.contains(RenderPass.SCOPER)) {
            return;
        }
        List<RenderPass> withScope = new ArrayList<>(passes);
        withScope.add(RenderPass.SCOPER);
        cir.setReturnValue(withScope);
    }
}
