package com.taczvr;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class TaczVRConfig {
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();

        ForgeConfigSpec.Builder commonBuilder = new ForgeConfigSpec.Builder();
        COMMON = new Common(commonBuilder);
        COMMON_SPEC = commonBuilder.build();
    }

    private TaczVRConfig() {
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.BooleanValue updateCheck;

        public final ForgeConfigSpec.DoubleValue gunScale;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> scaleOverrides;
        public final ForgeConfigSpec.DoubleValue gripOffsetX;
        public final ForgeConfigSpec.DoubleValue gripOffsetY;
        public final ForgeConfigSpec.DoubleValue gripOffsetZ;
        public final ForgeConfigSpec.DoubleValue gripPitch;

        public final ForgeConfigSpec.BooleanValue twoHanded;
        public final ForgeConfigSpec.DoubleValue twoHandStartOffset;
        public final ForgeConfigSpec.DoubleValue twoHandKeepOffset;
        public final ForgeConfigSpec.DoubleValue twoHandMinDistance;

        public final ForgeConfigSpec.BooleanValue aimDownSights;
        public final ForgeConfigSpec.DoubleValue adsMaxEyeOffset;
        public final ForgeConfigSpec.DoubleValue adsMaxEyeDistance;

        public final ForgeConfigSpec.BooleanValue buttonReload;
        public final ForgeConfigSpec.BooleanValue manualMagazine;
        public final ForgeConfigSpec.BooleanValue meleeThrust;
        public final ForgeConfigSpec.DoubleValue meleeThrustSpeed;
        public final ForgeConfigSpec.BooleanValue handoff;
        public final ForgeConfigSpec.BooleanValue handAttachments;
        public final ForgeConfigSpec.BooleanValue attachmentHints;
        public final ForgeConfigSpec.BooleanValue scopes;
        public final ForgeConfigSpec.DoubleValue scopeLensRadius;
        public final ForgeConfigSpec.BooleanValue magazineTapReload;
        public final ForgeConfigSpec.DoubleValue magazineTapRadius;

        public final ForgeConfigSpec.DoubleValue zeroDistance;
        public final ForgeConfigSpec.BooleanValue haptics;
        public final ForgeConfigSpec.BooleanValue hitFeedback;
        public final ForgeConfigSpec.BooleanValue gunEcho;
        public final ForgeConfigSpec.BooleanValue leftHandedGuns;
        public final ForgeConfigSpec.BooleanValue highFive;
        public final ForgeConfigSpec.BooleanValue visualRecoil;
        public final ForgeConfigSpec.DoubleValue recoilKickDegrees;

        public final ForgeConfigSpec.BooleanValue disableCameraRecoil;
        public final ForgeConfigSpec.BooleanValue hideTaczCrosshair;
        public final ForgeConfigSpec.BooleanValue disableSwingWithGun;
        public final ForgeConfigSpec.BooleanValue showAimLine;

        public final ForgeConfigSpec.IntValue remoteRenderDistance;
        public final ForgeConfigSpec.BooleanValue handsOnGun;

        Client(ForgeConfigSpec.Builder b) {
            b.push("general");
            enabled = b.comment("Master switch for the VR gun handling.")
                    .define("enabled", true);
            updateCheck = b.comment("On the title screen, look on GitHub for a newer TaCZ VR and offer to install it.")
                    .define("updateCheck", true);
            b.pop();

            b.push("hold");
            gunScale = b.comment("Scale of the gun model in VR. TACZ models are made for the flat screen and are ~3x too big.",
                            "0.3 makes an AK roughly 80 cm long.")
                    .defineInRange("gunScale", 0.3, 0.02, 2.0);
            scaleOverrides = b.comment("Multipliers on top of gunScale, as \"key=value\". Key is a gun id (tacz:glock_17) or a gun type (pistol, rifle, smg, shotgun, sniper, mg, rpg).")
                    .defineList("scaleOverrides", List.of("pistol=0.75"), o -> o instanceof String s && s.contains("="));
            gripOffsetX = b.comment("Where the pistol grip sits relative to the controller, in meters (controller space: +X right, +Y up, +Z back).")
                    .defineInRange("gripOffsetX", 0.0, -0.5, 0.5);
            gripOffsetY = b.defineInRange("gripOffsetY", -0.01, -0.5, 0.5);
            gripOffsetZ = b.defineInRange("gripOffsetZ", 0.03, -0.5, 0.5);
            gripPitch = b.comment("Tilts the gun relative to the controller, in degrees. Positive raises the muzzle.")
                    .defineInRange("gripPitch", 0.0, -90.0, 90.0);
            b.pop();

            b.push("twoHanded");
            twoHanded = b.comment("Bring the off-hand to the handguard to hold the gun with both hands. The barrel then points from the main hand to the off-hand.")
                    .define("enabled", true);
            twoHandStartOffset = b.comment("The off-hand must be this close (meters) to the barrel line to start a two-handed grip.")
                    .defineInRange("startOffset", 0.09, 0.01, 0.5);
            twoHandKeepOffset = b.comment("Distance (meters) from the barrel line at which an existing two-handed grip lets go.")
                    .defineInRange("keepOffset", 0.16, 0.01, 0.5);
            twoHandMinDistance = b.comment("The off-hand must be at least this far (meters) in front of the main hand.")
                    .defineInRange("minDistance", 0.12, 0.0, 1.0);
            b.pop();

            b.push("aim");
            aimDownSights = b.comment("Bringing the sights in front of your eye counts as aiming (TACZ ADS accuracy).")
                    .define("aimDownSights", true);
            adsMaxEyeOffset = b.comment("Max distance (meters) of the eye from the sight line to count as aiming.")
                    .defineInRange("maxEyeOffset", 0.07, 0.01, 0.5);
            adsMaxEyeDistance = b.comment("Max distance (meters) of the eye behind the sights to count as aiming.")
                    .defineInRange("maxEyeDistance", 0.7, 0.05, 2.0);
            zeroDistance = b.comment("Distance (blocks) at which the bullet path crosses the sight line.")
                    .defineInRange("zeroDistance", 25.0, 1.0, 500.0);
            b.pop();

            b.push("controls");
            buttonReload = b.comment("Reload with the 'use' button (A on Quest controllers) while holding a gun. Guns can't be 'used' anyway.")
                    .define("buttonReload", true);
            manualMagazine = b.comment("Guns with a detachable magazine are reloaded by hand: the reload button drops the magazine,",
                            "grab a new one from your belt with the off-hand (grip), push it into the magazine well,",
                            "then pull back the charging handle / slide if the chamber is empty. Off = TACZ's normal reload.")
                    .define("manualMagazine", true);
            meleeThrust = b.comment("Jab the gun forward to hit with the stock or bayonet (TACZ melee).")
                    .define("meleeThrust", true);
            meleeThrustSpeed = b.comment("How fast (m/s) the gun has to move forward to count as a jab.")
                    .defineInRange("meleeThrustSpeed", 2.5, 0.5, 10.0);
            handoff = b.comment("Hold a gun, ammo or attachment against another player's hand and press grip to give it to them.")
                    .define("handoff", true);
            handAttachments = b.comment("Hold an attachment in the off-hand against its spot on the gun and press grip to mount it.",
                            "With an empty off-hand, hold grip on a mounted attachment to take it off.")
                    .define("handAttachments", true);
            attachmentHints = b.comment("Holding an attachment in VR, a label above the hand says which of your guns it fits.")
                    .define("attachmentHints", true);
            b.pop();

            b.push("scopes");
            scopes = b.comment("Magnifying scopes show a zoomed picture when you look through them.")
                    .define("enabled", true);
            scopeLensRadius = b.comment("Radius (meters) of the eyepiece picture at gunScale 0.3, it scales with the gun.")
                    .defineInRange("lensRadius", 0.018, 0.005, 0.1);
            b.pop();

            b.push("reload");
            magazineTapReload = b.comment("Touch the magazine with your off-hand to reload.")
                    .define("magazineTapReload", true);
            magazineTapRadius = b.defineInRange("magazineTapRadius", 0.08, 0.01, 0.5);
            b.pop();

            b.push("feel");
            haptics = b.define("haptics", true);
            hitFeedback = b.comment("Buzz and a hit sound when your bullet hits something, stronger for headshots and kills.")
                    .define("hitFeedback", true);
            gunEcho = b.comment("Gunshots echo in caves and rooms, and sound dry outside.")
                    .define("gunEcho", true);
            leftHandedGuns = b.comment("Left-handed players (Main Hand: Left): your first person gun is mirrored to the left, and",
                            "left-handed players are drawn holding their gun in the left hand (TACZ hides it otherwise).")
                    .define("leftHandedGuns", true);
            highFive = b.comment("Slap your hand against another player's hand for a high five (sound and buzz).")
                    .define("highFive", true);
            visualRecoil = b.comment("Kick the gun model up when firing instead of rotating your view.")
                    .define("visualRecoil", true);
            recoilKickDegrees = b.defineInRange("recoilKickDegrees", 4.0, 0.0, 45.0);
            disableCameraRecoil = b.comment("TACZ turns your camera on recoil. That is sickening in VR.")
                    .define("disableCameraRecoil", true);
            hideTaczCrosshair = b.comment("TACZ draws its crosshair on the HUD, which floats in the wrong place in VR.")
                    .define("hideTaczCrosshair", true);
            disableSwingWithGun = b.comment("Stop Vivecraft roomscale swings (block breaking/hitting) while holding a gun.")
                    .define("disableSwingWithGun", true);
            showAimLine = b.comment("Debug: draw the exact bullet path from the muzzle. Useful while tuning the grip.")
                    .define("showAimLine", false);
            b.pop();

            b.push("others");
            remoteRenderDistance = b.comment("Render guns of other VR players up to this many blocks away.")
                    .defineInRange("renderDistance", 64, 4, 256);
            handsOnGun = b.comment("Fit the hands to the gun: in your own view a hand is drawn on the grip (and the handguard when",
                            "held two-handed) instead of Vivecraft's hand, on other VR players the model arm ends with its fist on the grip.")
                    .define("handsOnGun", true);
            b.pop();
        }
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue serverVrAim;
        public final ForgeConfigSpec.DoubleValue maxAimOriginDistance;
        public final ForgeConfigSpec.BooleanValue allowHandoff;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> assistPlayers;
        public final ForgeConfigSpec.BooleanValue grenadeBreaksBlocks;
        public final ForgeConfigSpec.BooleanValue muzzleLight;
        public final ForgeConfigSpec.BooleanValue dualWield;

        Common(ForgeConfigSpec.Builder b) {
            b.push("server");
            serverVrAim = b.comment("Fire bullets of VR players from their gun's muzzle in the direction the gun points.",
                            "Also aims VR stock/bayonet hits where the gun points.")
                    .define("vrAim", true);
            allowHandoff = b.comment("Let VR players hand guns, ammo and attachments to other players.")
                    .define("allowHandoff", true);
            maxAimOriginDistance = b.comment("Reject aim origins further than this (blocks) from the player's head.")
                    .defineInRange("maxAimOriginDistance", 3.0, 0.5, 16.0);
            muzzleLight = b.comment("Muzzle flashes briefly light up dark places (an invisible light block for a few ticks).")
                    .define("muzzleLight", true);
            dualWield = b.comment("VR players with a gun in each hand fire the off-hand one with the left trigger (no teleport while doing that).")
                    .define("dualWield", true);
            b.pop();

            b.push("grenade");
            grenadeBreaksBlocks = b.comment("Grenade explosions break blocks like TNT. Off: they only hurt.")
                    .define("breaksBlocks", false);
            b.pop();

            b.push("assist");
            assistPlayers = b.comment("Player names that get the assist menu in the pause screen (aim assist, infinite ammo, glowing targets).",
                            "Set by whoever runs the world or server, empty means nobody.")
                    .defineList("players", List.of(), o -> o instanceof String);
            b.pop();
        }
    }
}
