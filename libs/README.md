# libs

Nothing here is needed to build the mod. Gradle fetches TACZ, Vivecraft and Simple Voice Chat from Modrinth, and takes SimpleBedrockModel out of the TACZ jar.

One optional jar goes here, only for the dev client:

- `lrtactical-1.20.1-0.4.3.jar`: LesRaisins Tactical Equipments, to check that its items show in the VR hand. Build it from its own source. It is never bundled into TaCZ VR (GPL licence). Without it, `runClient` starts without that mod.
