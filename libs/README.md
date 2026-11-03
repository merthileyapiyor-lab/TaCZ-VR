# libs

Nothing here is needed to build the mod. Gradle fetches TACZ, Vivecraft and Simple Voice Chat from Modrinth, and takes SimpleBedrockModel out of the TACZ jar.

One optional jar goes here, only for the dev client:

- `lrtactical-1.19.2-0.4.3.jar`: a Minecraft 1.19.2 build of LesRaisins Tactical Equipments (LesRaisins itself only comes for 1.20.1), to check that its items show in the VR hand and swing with its own attacks. It is never bundled into TaCZ VR. Without it, or with `-Pnolr`, `runClient` starts without that mod.
