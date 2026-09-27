package com.taczvr.mixin.client;

import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MuzzleFlashRender.class, remap = false)
public interface MuzzleFlashRenderAccessor {
    @Accessor("shootTimeStamp")
    static long taczvr$getShootTimeStamp() {
        throw new AssertionError();
    }

    @Accessor("shootTimeStamp")
    static void taczvr$setShootTimeStamp(long value) {
        throw new AssertionError();
    }

    @Accessor("muzzleFlashStartMark")
    static void taczvr$setMuzzleFlashStartMark(boolean value) {
        throw new AssertionError();
    }
}
