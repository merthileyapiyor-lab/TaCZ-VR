# TaCZ VR

Hold [TaCZ](https://modrinth.com/mod/timeless-and-classics-zero) guns in [Vivecraft](https://modrinth.com/mod/vivecraft) VR: real 3D guns in your hands, aim down the barrel, two-handed grip, hand reloads and working scopes. [Visor](https://modrinth.com/mod/visor) is supported too.

Minecraft **1.20.1**, **Forge**. [Türkçe açıklama: OKUBENI.md](OKUBENI.md)

## Install

Put these in your `mods` folder:

1. `taczvr-1.20.1-<version>.jar` (this mod, from [TaCZ-VR-releases](https://github.com/merthileyapiyor-lab/TaCZ-VR-releases/releases))
2. [TaCZ](https://modrinth.com/mod/timeless-and-classics-zero) 1.1.8-hotfix or newer
3. [Vivecraft](https://modrinth.com/mod/vivecraft) 1.20.1 Forge (built with 1.3.15), **or** [Visor](https://modrinth.com/mod/visor) 0.6.0-alpha or newer (see below)
4. Optional: [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) 1.20.1, for the radio

**Updates:** when the game starts, the mod looks for a newer release on [TaCZ-VR-releases](https://github.com/merthileyapiyor-lab/TaCZ-VR-releases/releases). If there is one, the title screen shows **Update TaCZ VR**: **Later** skips it, **Quit Game** downloads the new jar into your mods folder, removes the old one and closes the game. Start it again to play with the new version. Turn it off with `updateCheck = false`.

On a server the mod goes **on the server too**, because bullet direction, magazines, attachments and handing guns over are done there. The server and every player need the **same version**. Flat-screen players can join and play with VR players; they see VR players holding their guns in 3D.

## Controls (Quest default buttons)

| What | How |
|---|---|
| Shoot | Right trigger |
| Aim | Bring the sights or the scope to your eye |
| Reload | **A** (TaCZ's normal animated reload, works on every gun) |
| Drop the magazine | Empty left hand to the gun's magazine, **grip** (the rounds go back to your inventory) |
| Take a new magazine | Left hand to your hip, hold **grip** |
| Insert the magazine | Touch it to the magazine well, it clicks in |
| Chamber a round | Grab the slide/charging handle with **grip**, pull back, let go |
| Two-handed grip | Left hand to the handguard |
| Buttstroke / bayonet | Push the gun forward fast |
| Attach a part | Hold the part in your left hand; a green marker shows where it goes. Bring it close and it snaps on. Red means it doesn't fit |
| Remove a part | Empty left hand to the part (yellow marker), hold **grip** for about a second |
| Hand a gun over | Hold it out to a friend's hand, **grip** |

## Features

- **3D gun in hand:** the grip sits in the controller and the barrel points where the controller points. Reload, shooting and slide animations play.
- **Bullets from the barrel:** shots leave the muzzle in the direction the gun points. The sights are zeroed at 25 blocks (`zeroDistance`).
- **Working scopes:** magnifying scopes show a truly zoomed picture and reticle inside the lens, at the scope's own magnification. Red dots and holo sights work as normal.
- **Hands on the gun:** your own hands are drawn gripping the gun, and the left hand carries the magazine during TaCZ's reload animation.
- **Other players see it:** VR players' arms reach from the shoulder to the grip, with muzzle flash, slide and reload animations.
- **Hit feedback:** your arm buzzes on a hit, stronger on headshots, long on kills (`hitFeedback`).
- **VR comfort:** no camera-turning recoil and no walking sway. The gun kicks up and buzzes instead. No HUD crosshair.
- **Which part fits which gun:** in VR, a small label above your hand shows whether the part you're holding fits the gun in your hand, and which guns in your inventory it fits (`attachmentHints`).
- **Dual pistols:** a gun in each hand; the left one fires with the left trigger and **A** reloads both (`dualWield`).
- **Left-handed players:** with Main Hand: Left, the flat-screen first-person gun is mirrored, others see the gun in your left hand, and in VR the arms swap (`leftHandedGuns`).
- **Riot shields** stop bullets while you hold them in front of you, and **lasers** are visible to everyone.
- **Atmosphere:** gunshot echo in caves (`gunEcho`), bullet whiz past your head, muzzle flash lighting up the dark (`muzzleLight`), and VR high fives (`highFive`).

## TaCZ VR items

All in their own **TaCZ VR** creative tab, all craftable, and all except the goggles are real 3D models in hand and in the world.

- **Hand grenade:** press **A** to pull the pin (4 s fuse), swing your arm and let go. It flies with your throw.
- **Flashbang:** whites out the screen of whoever looks at it, with ringing ears. Less if you look away, almost nothing behind a wall. Mobs nearby are stunned.
- **Smoke grenade:** a big cloud for 20 seconds that mobs can't see through.
- **Combat knife:** stab forward in VR. From behind it does 2.5× damage.
- **Medical syringe:** heals 4 hearts. In VR, push it into your other forearm or a friend. It also gets a downed friend back up.
- **Night vision goggles:** worn as a helmet. Toggle with **B**, or touch them in VR and press grip.
- **Grappling hook:** grabs where your hand points, up to 200 blocks away, the moment you click. It pulls you there and you hang with no fall damage. Click again to let go.
- **Radio:** hold use (in VR, hold it to your mouth) and everyone with a radio hears you, however far away, with a crackly radio sound. In team matches only your team hears you. Needs Simple Voice Chat on everyone.

## Game menu

A **⚔ Game** button appears at the top left of the Esc menu for ops and the world host. Nobody really dies: at 0 health you fall and lose nothing, and everyone gets their game mode back at the end.

- **First to fall loses**
- **Last one standing**
- **Team match:** random red and blue teams, no friendly fire
- **Three lives**
- **Zombie waves:** survive together, no friendly fire
  - Downed players crawl and can be revived by a friend crouching next to them for 3 seconds, or with a syringe.
  - Points for kills, revives and waves, and a shop between waves (**J**). Everything bought with points is taken back when the round ends.
  - A giant boss zombie every 5 waves.

## With Visor

[Visor](https://modrinth.com/mod/visor) works instead of Vivecraft. It needs Visor 0.6.0-alpha or newer, and Visor itself needs **Forge 47.4.0** or newer. With both installed, Vivecraft is used.

With Visor you get:
- The 3D gun in your hand, shots from the barrel, two-handed grip and aiming down the sights
- The **trigger** fires and **A** reloads (TaCZ's normal animated reload)
- An attachment held in the left hand snaps on at its spot on the gun
- Hit buzz, no HUD crosshair in VR, recoil that doesn't turn your view
- Visor's own hand is hidden while you hold a gun, the hand is drawn on the grip, and swinging the gun doesn't break blocks

Not with Visor yet: everything on the **grip** button (pulling a magazine by hand, taking attachments off, handing guns over, switching the goggles), the left trigger for a second pistol, and the zoomed picture inside magnifying scopes. These come in a later version.

Other players need the same VR mod to see you holding your gun in 3D. Shots always leave the barrel, even on a server without a VR mod.

## Settings

| Command | What it does |
|---|---|
| `/taczvr` | Shows the current values |
| `/taczvr scale 0.3` | Gun size |
| `/taczvr grip 0 -0.01 0.03` | Where the grip sits relative to the controller, in metres |
| `/taczvr pitch 10` | Tilts the barrel up or down, in degrees |
| `/taczvr aimline` | Draws the bullet path as a red line |
| `/taczvr reset` | Back to defaults |

Everything else is in `config/taczvr-client.toml` and `config/taczvr-common.toml`; each feature can be turned off there.

## Known limits

- Shaders (Oculus/Iris) can break the gun in hand and the scope picture.
- Other players don't see a VR player's manual magazine changes.

## Building

Needs JDK 17. Gradle downloads TaCZ, Vivecraft and Simple Voice Chat from Modrinth's maven.

```
./gradlew build
```

The jar is `build/libs/taczvr-1.20.1-<version>.jar`.

`./gradlew runClient` starts a dev client, `-Pvisor` with Visor (and Forge 47.4.0) instead of Vivecraft. `./gradlew runClient -Pselftest` runs the built-in self-test: it fakes VR poses and checks shooting, reloading, attachments, the items and the game modes, then closes the client. With `-Pvisor` it runs the Visor checks instead. `-Ptestonly=hook` (also `models`, `deagle`, `zombie`, `hint`, `lr`, `update`) runs only one part. `-Pnovoice` starts without Simple Voice Chat.

## Licence

[MIT](LICENSE). TaCZ, Vivecraft and Simple Voice Chat are separate mods under their own licences and are not included.
