package com.taczvr.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rounds started from the op game menu. Nobody really dies in them: at 0 health a player falls, gets healed and
 * keeps everything.
 * <ul>
 * <li>First to fall loses: everyone fights until one player falls, the others win.</li>
 * <li>Zombie waves: together against growing waves of zombies, until everyone has fallen. No friendly fire.</li>
 * <li>Last one standing: the fallen are out, the last player left wins.</li>
 * <li>Team match: red against blue, no friendly fire, the team with players left wins.</li>
 * <li>Three lives: everyone can fall three times, the last player with lives left wins.</li>
 * </ul>
 * Players who are out watch as spectators and get their game mode back when the round ends.
 */
public final class GameManager {
    public static final int STOP = 0;
    public static final int FIRST_FALL = 1;
    public static final int ZOMBIES = 2;
    public static final int LAST_STANDING = 3;
    public static final int TEAMS = 4;
    public static final int LIVES = 5;

    private static final int COUNTDOWN_TICKS = 60;
    // the shop is open meanwhile, everyone ready starts the next wave early
    private static final int WAVE_BREAK_TICKS = 300;
    // down on the ground this long before you're out, unless a friend gets you up
    private static final int BLEED_TICKS = 600;
    private static final int REVIVE_TICKS = 60;
    private static final double REVIVE_REACH = 1.8;
    private static final double REVIVE_HAND_REACH = 0.8;
    private static final float DOWNED_HEALTH = 4.0F;
    private static final int BOSS_EVERY = 5;
    private static final String BOSS_TAG = "taczvr_boss";
    private static final int START_LIVES = 3;
    // a short breather after losing a life, so one burst doesn't take them all
    private static final int RESPAWN_PROTECTION_TICKS = 60;
    private static final String WAVE_TAG = "taczvr_wave";
    private static final double LOST_DISTANCE = 48.0;
    private static final String RED = "taczvr_red";
    private static final String BLUE = "taczvr_blue";

    private static int mode = STOP;
    private static int countdown = 0;
    private static int wave = 0;
    private static int waveBreak = 0;
    private static final Map<UUID, ServerPlayer> PLAYERS = new LinkedHashMap<>();
    private static final Set<UUID> FALLEN = new HashSet<>();
    private static final Map<UUID, GameType> MODES_BEFORE = new HashMap<>();
    private static final Map<UUID, Integer> LIVES_LEFT = new HashMap<>();
    private static final Map<UUID, Integer> PROTECTED = new HashMap<>();
    private static final Map<UUID, String> TEAM_OF = new HashMap<>();
    private static final Map<UUID, String> TEAM_BEFORE = new HashMap<>();
    private static final Set<UUID> ZOMBIES_ALIVE = new HashSet<>();
    // downed player -> ticks left before they're out
    private static final Map<UUID, Integer> DOWNED = new HashMap<>();
    // downed player -> how far along getting them up is
    private static final Map<UUID, Integer> REVIVING = new HashMap<>();
    @Nullable
    private static ServerBossEvent bossBar;
    @Nullable
    private static UUID boss;
    @Nullable
    private static MinecraftServer server;
    @Nullable
    private static ServerLevel arena;

    private GameManager() {
    }

    /**
     * Ops and the player hosting the world run games.
     */
    public static boolean canControl(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    public static boolean running() {
        return mode != STOP;
    }

    public static int mode() {
        return mode;
    }

    public static int wave() {
        return wave;
    }

    public static int zombiesLeft() {
        return ZOMBIES_ALIVE.size();
    }

    public static int livesLeft(ServerPlayer player) {
        return LIVES_LEFT.getOrDefault(player.getUUID(), 0);
    }

    public static boolean isOut(ServerPlayer player) {
        return FALLEN.contains(player.getUUID());
    }

    @Nullable
    public static String teamOf(ServerPlayer player) {
        return TEAM_OF.get(player.getUUID());
    }

    /**
     * Down on the ground in the zombie waves, waiting for a friend.
     */
    public static boolean isDowned(Entity entity) {
        return !DOWNED.isEmpty() && DOWNED.containsKey(entity.getUUID());
    }

    public static boolean isBreak() {
        return mode == ZOMBIES && waveBreak > 0;
    }

    @Nullable
    public static ServerBossEvent bossBar() {
        return bossBar;
    }

    /**
     * Development self-test only: how long until a downed player is out.
     */
    public static void testBleed(ServerPlayer player, int ticks) {
        DOWNED.computeIfPresent(player.getUUID(), (id, left) -> ticks);
    }

    /**
     * Development self-test only: skip ahead to the wave before the next boss.
     */
    public static void testWave(int number) {
        wave = number;
    }

    /**
     * From the shop screen: buy an offer, or say you're ready for the next wave.
     */
    public static void shopAction(ServerPlayer player, int action) {
        if (mode != ZOMBIES || !PLAYERS.containsKey(player.getUUID()) || FALLEN.contains(player.getUUID())) {
            return;
        }
        if (action == com.taczvr.network.ShopActionPacket.READY) {
            if (ZombieShop.ready(player, alive()) && waveBreak > 1) {
                waveBreak = 1;
            }
        } else {
            ZombieShop.buy(player, action);
        }
    }

    public static String describe() {
        return "mode=" + mode + " countdown=" + countdown + " wave=" + wave + " break=" + waveBreak + " tracked=" + ZOMBIES_ALIVE.size()
                + " fallen=" + FALLEN.size() + "/" + PLAYERS.size() + " downed=" + DOWNED.size();
    }

    public static void command(ServerPlayer by, int action) {
        if (action == STOP) {
            stop();
        } else if (action >= FIRST_FALL && action <= LIVES) {
            List<ServerPlayer> players = new ArrayList<>();
            for (ServerPlayer player : by.server.getPlayerList().getPlayers()) {
                if (!player.isSpectator()) {
                    players.add(player);
                }
            }
            start(by, action, players);
        }
    }

    /**
     * Starts a round with these players. Public for the self-test, which brings fake players along.
     */
    public static void start(ServerPlayer by, int newMode, List<ServerPlayer> players) {
        if (running()) {
            by.sendSystemMessage(Component.translatableWithFallback("taczvr.game.already", "A round is already on"));
            return;
        }
        if (newMode == ZOMBIES && by.serverLevel().getDifficulty() == Difficulty.PEACEFUL) {
            by.sendSystemMessage(Component.translatableWithFallback("taczvr.game.peaceful",
                    "Zombies can't come on Peaceful, set the difficulty higher"));
            return;
        }
        if (newMode == TEAMS && players.size() < 2) {
            by.sendSystemMessage(Component.translatableWithFallback("taczvr.game.two", "A team match needs at least 2 players"));
            return;
        }
        server = by.server;
        arena = by.serverLevel();
        clearState();
        for (ServerPlayer player : players) {
            PLAYERS.put(player.getUUID(), player);
            refresh(player);
            if (newMode == LIVES) {
                LIVES_LEFT.put(player.getUUID(), START_LIVES);
            }
        }
        mode = newMode;
        countdown = COUNTDOWN_TICKS;
        if (newMode == TEAMS) {
            makeTeams(players);
        }
        if (newMode == ZOMBIES) {
            ZombieShop.start(players);
        }
        for (ServerPlayer player : players()) {
            title(player, Component.literal("3"), rule(player));
        }
    }

    private static Component rule(ServerPlayer player) {
        return switch (mode) {
            case ZOMBIES -> Component.translatableWithFallback("taczvr.game.rule.zombies", "Survive the zombie waves together");
            case LAST_STANDING -> Component.translatableWithFallback("taczvr.game.rule.last", "Last one standing wins");
            case TEAMS -> RED.equals(TEAM_OF.get(player.getUUID()))
                    ? Component.translatableWithFallback("taczvr.game.team.red", "You're on the red team").withStyle(ChatFormatting.RED)
                    : Component.translatableWithFallback("taczvr.game.team.blue", "You're on the blue team").withStyle(ChatFormatting.BLUE);
            case LIVES -> Component.translatableWithFallback("taczvr.game.rule.lives", "Everyone has %s lives", START_LIVES);
            default -> Component.translatableWithFallback("taczvr.game.rule", "First to fall loses");
        };
    }

    /**
     * Red and blue, shuffled, as scoreboard teams: coloured names and no friendly fire.
     */
    private static void makeTeams(List<ServerPlayer> players) {
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam red = team(scoreboard, RED, ChatFormatting.RED);
        PlayerTeam blue = team(scoreboard, BLUE, ChatFormatting.BLUE);
        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        for (int i = 0; i < shuffled.size(); i++) {
            ServerPlayer player = shuffled.get(i);
            String name = player.getScoreboardName();
            PlayerTeam before = scoreboard.getPlayersTeam(name);
            if (before != null) {
                TEAM_BEFORE.put(player.getUUID(), before.getName());
            }
            PlayerTeam team = i % 2 == 0 ? red : blue;
            scoreboard.addPlayerToTeam(name, team);
            TEAM_OF.put(player.getUUID(), team.getName());
        }
    }

    private static PlayerTeam team(Scoreboard scoreboard, String name, ChatFormatting color) {
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
        }
        team.setColor(color);
        team.setAllowFriendlyFire(false);
        return team;
    }

    private static void stop() {
        if (!running()) {
            return;
        }
        List<ServerPlayer> everyone = players();
        end();
        for (ServerPlayer player : everyone) {
            title(player, Component.translatableWithFallback("taczvr.game.stopped", "Round stopped"), Component.empty());
        }
    }

    private static void refresh(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.clearFire();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !running()) {
            return;
        }
        if (!PROTECTED.isEmpty()) {
            PROTECTED.replaceAll((id, ticks) -> ticks - 1);
            PROTECTED.values().removeIf(ticks -> ticks <= 0);
        }
        if (countdown > 0) {
            countdown--;
            if (countdown % 20 == 0) {
                int seconds = countdown / 20;
                for (ServerPlayer player : players()) {
                    // the rule (or your team) stays under the numbers
                    title(player, seconds > 0 ? Component.literal(String.valueOf(seconds))
                                    : Component.translatableWithFallback("taczvr.game.go", "Fight!"),
                            seconds > 0 ? rule(player) : Component.empty());
                }
                if (seconds == 0 && mode == ZOMBIES) {
                    nextWave();
                }
            }
            return;
        }
        if (mode == ZOMBIES) {
            tickDowned();
            if (running()) {
                tickWaves();
            }
        }
    }

    /**
     * Still in the round: not out, but maybe down on the ground.
     */
    private static List<ServerPlayer> alive() {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer player : players()) {
            if (!FALLEN.contains(player.getUUID())) {
                list.add(player);
            }
        }
        return list;
    }

    /**
     * On their feet: in the round and not down.
     */
    private static List<ServerPlayer> standing() {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer player : alive()) {
            if (!DOWNED.containsKey(player.getUUID())) {
                list.add(player);
            }
        }
        return list;
    }

    private static void tickWaves() {
        if (waveBreak > 0) {
            if (waveBreak % 20 == 0) {
                for (ServerPlayer player : alive()) {
                    player.displayClientMessage(Component.translatableWithFallback("taczvr.game.break", "Next wave in %s s, shop: J",
                            waveBreak / 20).withStyle(ChatFormatting.GOLD), true);
                }
            }
            if (--waveBreak == 0) {
                ZombieShop.close(alive());
                nextWave();
            }
            return;
        }
        if (arena == null) {
            return;
        }
        List<ServerPlayer> inRound = alive();
        Iterator<UUID> it = ZOMBIES_ALIVE.iterator();
        while (it.hasNext()) {
            Entity zombie = arena.getEntity(it.next());
            if (zombie == null || !zombie.isAlive()) {
                it.remove();
            } else if (inRound.stream().noneMatch(player -> player.distanceToSqr(zombie) < LOST_DISTANCE * LOST_DISTANCE)) {
                // wandered off or stuck somewhere, it would hold up the wave forever
                zombie.discard();
                it.remove();
            } else if (zombie instanceof Zombie mob && mob.getTarget() != null && isDowned(mob.getTarget())) {
                // someone down on the ground is left alone
                mob.setTarget(null);
            }
        }
        if (bossBar != null) {
            Entity big = boss == null ? null : arena.getEntity(boss);
            if (big instanceof Zombie living && living.isAlive()) {
                bossBar.setProgress(living.getHealth() / living.getMaxHealth());
            } else {
                bossBar.removeAllPlayers();
                bossBar = null;
                boss = null;
            }
        }
        // the last ones glow so they can be found
        if (ZOMBIES_ALIVE.size() <= 2) {
            for (UUID id : ZOMBIES_ALIVE) {
                if (arena.getEntity(id) instanceof Zombie zombie && !zombie.hasEffect(MobEffects.GLOWING)) {
                    zombie.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60, 0, false, false));
                }
            }
        }
        if (ZOMBIES_ALIVE.isEmpty()) {
            waveBreak = WAVE_BREAK_TICKS;
            // whoever is down gets up for the break, everyone still in earns the wave bonus
            for (ServerPlayer player : new ArrayList<>(alive())) {
                if (DOWNED.containsKey(player.getUUID())) {
                    revive(player, null);
                }
                ZombieShop.add(player, ZombieShop.WAVE_POINTS * wave);
            }
            announce(Component.translatableWithFallback("taczvr.game.wave.cleared", "Wave %s cleared!", wave),
                    Component.translatableWithFallback("taczvr.game.wave.shop", "+%s points, the shop is open", ZombieShop.WAVE_POINTS * wave));
            ZombieShop.openFor(alive(), WAVE_BREAK_TICKS);
        }
    }

    /**
     * Downed players bleed out unless a friend gets them up: crouching next to them, or in VR a hand on them, for
     * a few seconds. A syringe does it at once (see MedkitItem).
     */
    private static void tickDowned() {
        if (DOWNED.isEmpty()) {
            return;
        }
        for (UUID id : new ArrayList<>(DOWNED.keySet())) {
            ServerPlayer downed = online(id);
            if (downed == null) {
                continue;
            }
            int left = DOWNED.get(id) - 1;
            DOWNED.put(id, left);
            if (downed.tickCount % 20 == 0) {
                downed.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));
                downed.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
            }
            ServerPlayer helper = null;
            for (ServerPlayer friend : standing()) {
                if (friend != downed && friend.distanceTo(downed) < REVIVE_REACH && (friend.isShiftKeyDown() || handOn(friend, downed))) {
                    helper = friend;
                    break;
                }
            }
            int progress = helper == null ? 0 : REVIVING.getOrDefault(id, 0) + 1;
            REVIVING.put(id, progress);
            if (helper != null && progress >= REVIVE_TICKS) {
                revive(downed, helper);
                continue;
            }
            if (left <= 0) {
                bleedOut(downed);
                if (!running()) {
                    return;
                }
                continue;
            }
            if (helper != null) {
                Component bar = Component.translatableWithFallback("taczvr.game.reviving", "Getting up... %s%%", progress * 100 / REVIVE_TICKS)
                        .withStyle(ChatFormatting.GREEN);
                downed.displayClientMessage(bar, true);
                helper.displayClientMessage(bar, true);
            } else if (downed.tickCount % 10 == 0) {
                downed.displayClientMessage(Component.translatableWithFallback("taczvr.game.downed.wait",
                        "You're down: %s s for a friend to get you up", left / 20).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    private static boolean handOn(ServerPlayer friend, ServerPlayer downed) {
        com.taczvr.vr.VrPose pose = com.taczvr.VrCommon.isVRPlayer(friend) ? com.taczvr.VrCommon.getPose(friend) : null;
        if (pose == null) {
            return false;
        }
        Vec3 body = downed.position().add(0.0, 0.3, 0.0);
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            com.taczvr.vr.VrPart part = pose.getHand(hand);
            if (part != null && part.getPos().distanceTo(body) < REVIVE_HAND_REACH) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ServerPlayer online(UUID id) {
        for (ServerPlayer player : players()) {
            if (player.getUUID().equals(id)) {
                return player;
            }
        }
        return null;
    }

    private static void down(ServerPlayer player) {
        DOWNED.put(player.getUUID(), BLEED_TICKS);
        REVIVING.remove(player.getUUID());
        player.setHealth(DOWNED_HEALTH);
        sendDowned(player, true);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        String name = player.getGameProfile().getName();
        announce(Component.translatableWithFallback("taczvr.game.downed", "%s is down!", name).withStyle(ChatFormatting.RED),
                Component.translatableWithFallback("taczvr.game.downed.help", "Crouch by them or use a syringe"));
    }

    /**
     * Back on their feet. Public for the syringe.
     */
    public static boolean revive(ServerPlayer player, @Nullable ServerPlayer by) {
        if (DOWNED.remove(player.getUUID()) == null) {
            return false;
        }
        REVIVING.remove(player.getUUID());
        sendDowned(player, false);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.GLOWING);
        player.setHealth(Math.max(player.getHealth(), 8.0F));
        PROTECTED.put(player.getUUID(), RESPAWN_PROTECTION_TICKS);
        if (by != null) {
            ZombieShop.add(by, ZombieShop.REVIVE_POINTS);
            announce(Component.translatableWithFallback("taczvr.game.revived", "%s got %s up", by.getGameProfile().getName(),
                    player.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), Component.empty());
        }
        return true;
    }

    private static void bleedOut(ServerPlayer player) {
        DOWNED.remove(player.getUUID());
        REVIVING.remove(player.getUUID());
        sendDowned(player, false);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.GLOWING);
        out(player);
        if (standing().isEmpty()) {
            gameOver();
        } else {
            announce(Component.translatableWithFallback("taczvr.game.fell", "%s fell!", player.getGameProfile().getName()),
                    Component.translatableWithFallback("taczvr.game.fell.watch", "watching until the end"));
        }
    }

    // everyone crawls them on their own screen too
    private static void sendDowned(ServerPlayer player, boolean downed) {
        com.taczvr.network.Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                new com.taczvr.network.DownedPacket(player.getId(), downed));
    }

    private static void out(ServerPlayer player) {
        FALLEN.add(player.getUUID());
        MODES_BEFORE.put(player.getUUID(), player.gameMode.getGameModeForPlayer());
        player.setHealth(player.getMaxHealth());
        player.setGameMode(GameType.SPECTATOR);
    }

    private static void gameOver() {
        int reached = wave;
        List<ServerPlayer> everyone = players();
        end();
        for (ServerPlayer player : everyone) {
            title(player, Component.translatableWithFallback("taczvr.game.over", "Game over!"),
                    Component.translatableWithFallback("taczvr.game.over.wave", "You reached wave %s", reached));
            sound(player);
        }
    }

    /**
     * Points for the zombies you kill, more for the boss.
     */
    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (mode != ZOMBIES || !event.getEntity().getTags().contains(WAVE_TAG)) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer && PLAYERS.containsKey(killer.getUUID())) {
            ZombieShop.add(killer, event.getEntity().getTags().contains(BOSS_TAG) ? ZombieShop.BOSS_POINTS : ZombieShop.KILL_POINTS);
        }
    }

    private static void nextWave() {
        wave++;
        List<ServerPlayer> standing = standing();
        if (arena == null || standing.isEmpty()) {
            return;
        }
        Vec3 center = Vec3.ZERO;
        for (ServerPlayer player : standing) {
            center = center.add(player.position());
        }
        center = center.scale(1.0 / standing.size());
        int count = 2 + wave * 2;
        for (int i = 0; i < count; i++) {
            double angle = arena.random.nextDouble() * Math.PI * 2.0;
            double radius = 14.0 + arena.random.nextDouble() * 8.0;
            int x = (int) Math.floor(center.x + Math.cos(angle) * radius);
            int z = (int) Math.floor(center.z + Math.sin(angle) * radius);
            BlockPos pos = arena.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            Zombie zombie = EntityType.ZOMBIE.create(arena);
            if (zombie == null) {
                continue;
            }
            zombie.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, arena.random.nextFloat() * 360.0F, 0.0F);
            zombie.finalizeSpawn(arena, arena.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            // no burning in daylight, and no loot from a game
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            zombie.setDropChance(EquipmentSlot.HEAD, 0.0F);
            if (wave >= 4 && i % 2 == 0) {
                zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
            }
            zombie.addTag(WAVE_TAG);
            zombie.setPersistenceRequired();
            zombie.setTarget(standing.get(i % standing.size()));
            arena.addFreshEntity(zombie);
            ZOMBIES_ALIVE.add(zombie.getUUID());
        }
        if (wave % BOSS_EVERY == 0) {
            spawnBoss(center, standing);
            announce(Component.translatableWithFallback("taczvr.game.wave.boss", "Wave %s: the boss!", wave).withStyle(ChatFormatting.DARK_RED),
                    Component.translatableWithFallback("taczvr.game.wave.count", "%s zombies", count));
            return;
        }
        announce(Component.translatableWithFallback("taczvr.game.wave", "Wave %s", wave),
                Component.translatableWithFallback("taczvr.game.wave.count", "%s zombies", count));
    }

    /**
     * Every fifth wave: a big armoured zombie with a sword, lots of health and a boss bar for everyone.
     */
    private static void spawnBoss(Vec3 center, List<ServerPlayer> standing) {
        double angle = arena.random.nextDouble() * Math.PI * 2.0;
        BlockPos pos = arena.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(center.x + Math.cos(angle) * 18.0, 0, center.z + Math.sin(angle) * 18.0));
        Zombie big = EntityType.ZOMBIE.create(arena);
        if (big == null) {
            return;
        }
        big.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        big.finalizeSpawn(arena, arena.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        big.setBaby(false);
        Component name = Component.translatableWithFallback("taczvr.game.boss", "Giant Zombie").withStyle(ChatFormatting.DARK_RED);
        big.setCustomName(name);
        big.setCustomNameVisible(true);
        big.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        big.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        big.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            big.setDropChance(slot, 0.0F);
        }
        double health = 200.0 + 60.0 * (wave / BOSS_EVERY - 1);
        big.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        big.setHealth((float) health);
        big.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7.0);
        big.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.26);
        big.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        big.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0);
        big.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0, false, false));
        big.addTag(WAVE_TAG);
        big.addTag(BOSS_TAG);
        big.setPersistenceRequired();
        if (!standing.isEmpty()) {
            big.setTarget(standing.get(0));
        }
        arena.addFreshEntity(big);
        ZOMBIES_ALIVE.add(big.getUUID());
        boss = big.getUUID();
        bossBar = new ServerBossEvent(name, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        for (ServerPlayer player : players()) {
            bossBar.addPlayer(player);
        }
    }

    @Nullable
    private static ServerPlayer participant(Object entity) {
        return running() && entity instanceof ServerPlayer player && PLAYERS.containsKey(player.getUUID())
                && !FALLEN.contains(player.getUUID()) ? player : null;
    }

    /**
     * Fighting zombies together: players can't hurt each other or themselves (bullets, grenades, knives), so nobody
     * falls to a friend's stray shot.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (mode == ZOMBIES && participant(event.getEntity()) != null
                && (event.getSource().getEntity() instanceof Player || isDowned(event.getEntity()))) {
            event.setCanceled(true);
        }
    }

    // after armor, so the damage is what the health would really lose
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        ServerPlayer player = participant(event.getEntity());
        if (player == null) {
            return;
        }
        // down on the ground nothing hurts, the clock is ticking instead
        if (countdown > 0 || PROTECTED.containsKey(player.getUUID()) || DOWNED.containsKey(player.getUUID())) {
            event.setCanceled(true);
        } else if (event.getAmount() >= player.getHealth()) {
            event.setCanceled(true);
            fall(player);
        }
    }

    // whatever still gets through (/kill, the void)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        ServerPlayer player = participant(event.getEntity());
        if (player != null) {
            event.setCanceled(true);
            fall(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && participant(player) != null) {
            if (mode == ZOMBIES) {
                // gone, not down: nobody can get them up
                DOWNED.remove(player.getUUID());
                REVIVING.remove(player.getUUID());
                FALLEN.add(player.getUUID());
                if (standing().isEmpty()) {
                    gameOver();
                }
                return;
            }
            LIVES_LEFT.put(player.getUUID(), 0);
            fall(player);
        }
    }

    /**
     * A player's health hit 0. Public for the self-test.
     */
    public static void fall(ServerPlayer loser) {
        if (participant(loser) == null) {
            return;
        }
        loser.setHealth(loser.getMaxHealth());
        if (loser.getY() < loser.level().getMinBuildHeight()) {
            ServerLevel overworld = loser.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            loser.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, loser.getYRot(), loser.getXRot());
        }
        String name = loser.getGameProfile().getName();
        if (mode == FIRST_FALL) {
            List<String> winners = new ArrayList<>();
            for (ServerPlayer player : players()) {
                if (player != loser) {
                    winners.add(player.getGameProfile().getName());
                }
            }
            finish(Component.translatableWithFallback("taczvr.game.fell", "%s fell!", name), winners);
            return;
        }
        if (mode == ZOMBIES) {
            if (DOWNED.containsKey(loser.getUUID())) {
                return;
            }
            // down on the ground if someone is still on their feet to help, otherwise it's over
            if (standing().stream().anyMatch(player -> player != loser)) {
                down(loser);
            } else {
                out(loser);
                gameOver();
            }
            return;
        }
        if (mode == LIVES) {
            int left = LIVES_LEFT.getOrDefault(loser.getUUID(), 1) - 1;
            LIVES_LEFT.put(loser.getUUID(), left);
            if (left > 0) {
                refresh(loser);
                PROTECTED.put(loser.getUUID(), RESPAWN_PROTECTION_TICKS);
                announce(Component.translatableWithFallback("taczvr.game.fell", "%s fell!", name),
                        Component.translatableWithFallback("taczvr.game.lives.left", "%s lives left", left));
                return;
            }
        }
        // out: watches the rest of the round
        FALLEN.add(loser.getUUID());
        MODES_BEFORE.put(loser.getUUID(), loser.gameMode.getGameModeForPlayer());
        loser.setGameMode(GameType.SPECTATOR);
        List<ServerPlayer> standing = standing();
        if (mode == TEAMS) {
            String team = TEAM_OF.get(loser.getUUID());
            boolean teamLeft = standing.stream().anyMatch(player -> team != null && team.equals(TEAM_OF.get(player.getUUID())));
            if (!teamLeft) {
                boolean redWon = !RED.equals(team);
                finishTeams(Component.translatableWithFallback("taczvr.game.fell", "%s fell!", name), redWon);
                return;
            }
        } else if (standing.size() <= 1) {
            List<String> winners = new ArrayList<>();
            for (ServerPlayer player : standing) {
                winners.add(player.getGameProfile().getName());
            }
            finish(Component.translatableWithFallback("taczvr.game.fell", "%s fell!", name), winners);
            return;
        }
        announce(Component.translatableWithFallback("taczvr.game.fell", "%s fell!", name),
                Component.translatableWithFallback("taczvr.game.fell.watch", "watching until the end"));
    }

    private static void finish(Component title, List<String> winners) {
        Component subtitle = winners.isEmpty() ? Component.empty()
                : Component.translatableWithFallback("taczvr.game.winner", "Winner: %s", String.join(", ", winners));
        List<ServerPlayer> everyone = players();
        end();
        for (ServerPlayer player : everyone) {
            title(player, title, subtitle);
            sound(player);
        }
    }

    private static void finishTeams(Component title, boolean redWon) {
        Component subtitle = redWon
                ? Component.translatableWithFallback("taczvr.game.team.red.won", "Red team wins!").withStyle(ChatFormatting.RED)
                : Component.translatableWithFallback("taczvr.game.team.blue.won", "Blue team wins!").withStyle(ChatFormatting.BLUE);
        List<ServerPlayer> everyone = players();
        end();
        for (ServerPlayer player : everyone) {
            title(player, title, subtitle);
            sound(player);
        }
    }

    /**
     * Ends the round: everyone healed and back in their game mode and team, leftover zombies gone.
     */
    private static void end() {
        for (UUID id : new ArrayList<>(DOWNED.keySet())) {
            ServerPlayer downed = online(id);
            if (downed != null) {
                sendDowned(downed, false);
                downed.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                downed.removeEffect(MobEffects.GLOWING);
            }
        }
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }
        if (mode == ZOMBIES) {
            // what was bought with points goes back
            ZombieShop.end(players(), arena);
        }
        for (ServerPlayer player : players()) {
            GameType before = MODES_BEFORE.get(player.getUUID());
            if (before != null) {
                player.setGameMode(before);
            }
            refresh(player);
        }
        if (server != null && !TEAM_OF.isEmpty()) {
            Scoreboard scoreboard = server.getScoreboard();
            for (ServerPlayer player : players()) {
                String name = player.getScoreboardName();
                PlayerTeam current = scoreboard.getPlayersTeam(name);
                if (current != null && (RED.equals(current.getName()) || BLUE.equals(current.getName()))) {
                    scoreboard.removePlayerFromTeam(name, current);
                }
                String before = TEAM_BEFORE.get(player.getUUID());
                PlayerTeam old = before == null ? null : scoreboard.getPlayerTeam(before);
                if (old != null) {
                    scoreboard.addPlayerToTeam(name, old);
                }
            }
            for (String name : new String[]{RED, BLUE}) {
                PlayerTeam team = scoreboard.getPlayerTeam(name);
                if (team != null) {
                    scoreboard.removePlayerTeam(team);
                }
            }
        }
        if (arena != null) {
            for (UUID id : ZOMBIES_ALIVE) {
                Entity zombie = arena.getEntity(id);
                if (zombie != null) {
                    zombie.discard();
                }
            }
        }
        clearState();
        mode = STOP;
    }

    private static void clearState() {
        PLAYERS.clear();
        FALLEN.clear();
        MODES_BEFORE.clear();
        LIVES_LEFT.clear();
        PROTECTED.clear();
        TEAM_OF.clear();
        TEAM_BEFORE.clear();
        ZOMBIES_ALIVE.clear();
        DOWNED.clear();
        REVIVING.clear();
        bossBar = null;
        boss = null;
        countdown = 0;
        wave = 0;
        waveBreak = 0;
    }

    private static List<ServerPlayer> players() {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer player : PLAYERS.values()) {
            // a player who logged out and back in is a new object
            ServerPlayer current = server == null ? null : server.getPlayerList().getPlayer(player.getUUID());
            list.add(current != null ? current : player);
        }
        return list;
    }

    private static void announce(Component title, Component subtitle) {
        for (ServerPlayer player : players()) {
            title(player, title, subtitle);
        }
    }

    private static void sound(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    private static void title(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 10));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
