package com.taczvr.mixin.client;

import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = AnimationController.class, remap = false)
public interface AnimationControllerAccessor {
    @Accessor("prototypes")
    Map<String, ObjectAnimation> taczvr$getPrototypes();
}
