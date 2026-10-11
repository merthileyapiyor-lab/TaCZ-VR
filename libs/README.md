# libs

Nothing here is needed to build the mod. Gradle fetches TACZ, Vivecraft and Simple Voice Chat from Modrinth, and takes SimpleBedrockModel out of the TACZ jar.

Optional jars go here, only for the dev client:

- `lrtactical-1.20.1-0.4.3.jar`: LesRaisins Tactical Equipments, to check that its items show in the VR hand. Build it from its own source. It is never bundled into TaCZ VR (GPL licence). Without it, `runClient` starts without that mod.
- `deltaforce-melee-pack-0.2.0.jar` (renamed from "DeltaForce Melee Pack-0.2.0.jar"): knives built on LesRaisins, only loaded together with lrtactical, to test they show in the VR hand. Never bundled (all rights reserved).
- `carryon-forge-1.20.1-2.1.2.7.jar`: Carry On, to test the grappling hook while carrying something. TaCZ VR only looks its classes up by name and never bundles it (LGPL licence). Without it, or with `-Pnocarryon`, that test is skipped.
- `enhancedvisuals-1.20.1-1.8.29.jar` and `creativecore-1.20.1-2.12.38.jar` (renamed from EnhancedVisuals_FORGE_v1.8.29_mc1.20.1.jar and CreativeCore_FORGE_v2.12.38_mc1.20.1.jar): only with `-Pev`, to test that EnhancedVisuals stays off in VR (`-Ptestonly=ev -Prundir=<a NullVR game folder>` reads Vivecraft's HUD panel back). Never bundled (LGPL).
- `vivecraft-1.20.1-1.3.15-openxr-forge.jar`: an OpenXR build of Vivecraft. With `-Popenxr` the dev client builds and runs against it instead of the normal Vivecraft.
