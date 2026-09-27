package com.taczvr.mixin.client;

import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.HashMap;

@Mixin(value = BedrockModel.class, remap = false)
public interface BedrockModelAccessor {
    @Accessor("modelMap")
    HashMap<String, ModelRendererWrapper> taczvr$getModelMap();
}
