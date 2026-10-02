package com.taczvr.vr;

/**
 * What the VR mod is rendering right now.
 */
public enum VrPass {
    // one of your eyes, or the centered view
    FIRST_PERSON,
    // a third person or mixed reality camera
    THIRD_PERSON,
    // Vivecraft's spyglass view, which we point down a magnified scope
    SCOPE,
    OTHER;

    public boolean isFirstPerson() {
        return this == FIRST_PERSON;
    }
}
