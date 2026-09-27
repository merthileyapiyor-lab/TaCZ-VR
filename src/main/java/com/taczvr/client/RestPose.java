package com.taczvr.client;

import com.taczvr.mixin.client.AnimationControllerAccessor;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The pose a gun sits in when nothing is playing. TACZ only puts the hands onto the grip and handguard through its
 * "static_idle" animation, which normally only runs for your own first person gun. For other players we apply that
 * animation's first frame ourselves, on a private copy so the local player's animation state isn't touched.
 */
final class RestPose {
    private static final String[] NAMES = {"static_idle", "idle"};
    private static final Object NONE = new Object();
    private static final Map<BedrockGunModel, Object> POSES = new WeakHashMap<>();

    private RestPose() {
    }

    /**
     * Writes the rest pose into the model's bones. Undo with {@link BedrockGunModel#cleanAnimationTransform()}.
     */
    static void apply(GunDisplayInstance display, BedrockGunModel model) {
        Object pose = POSES.computeIfAbsent(model, m -> create(display, model));
        if (pose instanceof ObjectAnimation animation) {
            animation.update(false, 0.0F);
        }
    }

    private static Object create(GunDisplayInstance display, BedrockGunModel model) {
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = display.getAnimationStateMachine();
        if (stateMachine == null) {
            return NONE;
        }
        Map<String, ObjectAnimation> prototypes =
                ((AnimationControllerAccessor) stateMachine.getAnimationController()).taczvr$getPrototypes();
        for (String name : NAMES) {
            ObjectAnimation prototype = prototypes.get(name);
            if (prototype != null) {
                ObjectAnimation copy = new ObjectAnimation(prototype);
                copy.applyAnimationListeners(model);
                return copy;
            }
        }
        return NONE;
    }
}
