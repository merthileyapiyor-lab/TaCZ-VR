package com.taczvr.vr;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;

/**
 * One tracked device of a VR player in world space: where it is, where it points and how it's turned.
 * Filled from whichever VR mod is installed, Vivecraft or Visor.
 */
public interface VrPart {
    Vec3 getPos();

    /**
     * Where it points, the -Z axis of {@link #getRotation()}.
     */
    Vec3 getDir();

    Quaternionfc getRotation();

    static VrPart of(Vec3 pos, Vec3 dir, Quaternionfc rotation) {
        return new Simple(pos, dir, rotation);
    }

    record Simple(Vec3 pos, Vec3 dir, Quaternionfc rotation) implements VrPart {
        @Override
        public Vec3 getPos() {
            return this.pos;
        }

        @Override
        public Vec3 getDir() {
            return this.dir;
        }

        @Override
        public Quaternionfc getRotation() {
            return this.rotation;
        }
    }
}
