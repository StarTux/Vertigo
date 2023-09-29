package io.github.feydk.vertigo;

import com.cavetale.core.event.minigame.MinigameFlag;
import com.cavetale.core.event.minigame.MinigameMatchCompleteEvent;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.font.Glyph;
import com.winthier.creative.BuildWorld;
import com.winthier.creative.file.Files;
import com.winthier.creative.review.MapReview;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Rotatable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import static com.cavetale.core.font.Unicode.subscript;
import static com.cavetale.core.font.Unicode.superscript;
import static com.cavetale.core.font.Unicode.tiny;
import static com.cavetale.core.font.VanillaItems.WATER_BUCKET;
import static java.util.Comparator.comparingInt;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;
import static net.kyori.adventure.title.Title.Times.times;
import static net.kyori.adventure.title.Title.title;

@Getter
public final class VertigoGame {
    protected final VertigoPlugin plugin;
    protected final World world;
    protected final String path;
    protected final BuildWorld buildWorld;
    protected GameMap map;
    protected final String mapName;
    protected GameState state = GameState.NONE;
    protected List<VertigoPlayer> players = new ArrayList<>();
    @Setter protected boolean publicGame = false;
    @Setter private boolean testing = false;

    // Game loop stuff.
    protected boolean shutdown;
    private long ticks;
    protected long stateTicks;
    private int countdownToStartDuration;
    private int endDuration = 20 * 60;
    private int currentRound;
    private int maxRounds = 10;
    protected BossBar bossBar;

    // Stuff for keeping track of jumpers.
    List<VertigoPlayer> jumpers = new ArrayList<>();
    private int currentJumperIndex = 0;
    private long jumperTicks = 0;
    private boolean currentJumperHasJumped;
    protected VertigoPlayer currentJumper;
    private boolean currentJumperPassedRing;
    private boolean moreThanOnePlayed;
    private String winnerName;
    private List<UUID> winnerUuids = new ArrayList<>();

    // Stuff for keeping track of rounds.
    //private int roundNumber = 0;
    //private int roundCountdownTicks = -1;

    enum GameState {
        NONE,                   // Nothing has happened yet
        INIT,                   // Game has been set up, but not ready
        READY,                  // Game is ready to accept players
        COUNTDOWN_TO_START,     // Countdown to first round has started
        RUNNING,                // Game/a round is running
        ENDED                 // Game is over
    }

    protected void log(String msg) {
        plugin.getLogger().info("[" + mapName + "] " + msg);
    }

    protected void warn(String msg) {
        plugin.getLogger().warning("[" + mapName + "] " + msg);
    }

    public VertigoGame(final VertigoPlugin plugin, final World theWorld, final BuildWorld theBuildWorld) {
        this.plugin = plugin;
        this.path = theWorld.getName();
        this.world = theWorld;
        this.buildWorld = theBuildWorld;
        this.mapName = theBuildWorld.getPath();
        this.bossBar = BossBar.bossBar(plugin.TITLE, 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        countdownToStartDuration = plugin.getConfig().getInt("general.countdownToStartDuration");
    }

    protected boolean setup(CommandSender admin) {
        shutdown = false;
        players.clear();
        jumpers.clear();
        updateBossBar(0);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setPVP(false);
        world.setGameRule(GameRule.DO_TILE_DROPS, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false);
        world.setGameRule(GameRule.DISABLE_RAIDS, true);
        world.setGameRule(GameRule.DO_INSOMNIA, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.FIRE_DAMAGE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.FALL_DAMAGE, true);
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setStorm(false);
        if (world.getWorldBorder().getSize() < 2048) {
            world.getWorldBorder().setSize(2048);
        }
        map = new GameMap(plugin.getConfig().getInt("general.chunkRadius"), this);
        boolean ready = map.process(getSpawnLocation().getChunk());
        if (ready) {
            if (map.getStartingTime() == -1) {
                world.setTime(1000L);
            } else {
                world.setTime(map.getStartingTime());
            }
            if (map.getLockTime()) {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            } else {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
            }
            state = GameState.INIT;
        } else {
            admin.sendMessage(text("The game could not be set up. Check console for hints.", RED));
        }
        return ready;
    }

    /**
     * Clean up the game and its world.  Called by Games, which will
     * then remove the instance from its cache.
     */
    protected void discard() {
        log("Discarding world");
        shutdown();
        for (Player player : world.getPlayers()) {
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(plugin.getLobbyWorld().getSpawnLocation());
        }
        Files.deleteWorld(world);
        state = GameState.NONE;
        players.clear();
        jumpers.clear();
    }

    protected void ready(CommandSender admin) {
        if (state != GameState.INIT) {
            admin.sendMessage(text("No can do. Game hasn't been set up yet.", RED));
            return;
        }
        state = GameState.READY;
        updateBossBar(0);
    }

    protected void joinPlayer(Player player, boolean spectator) {
        VertigoPlayer vp = findPlayer(player);
        if (vp == null) {
            vp = new VertigoPlayer(this, player);
            players.add(vp);
        }
        vp.isPlaying = !spectator;
        vp.wasPlaying = vp.isPlaying;
        player.setGameMode(GameMode.SPECTATOR);
        if (vp.isPlaying) {
            jumpers.add(vp);
        }
        player.removePotionEffect(PotionEffectType.LEVITATION);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(vp.getSpawnLocation());
        player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1, 1);
        player.showTitle(title(text("Welcome to Vertigo", GREEN), empty()));
        if (plugin.state.event && !testing) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
        }
    }

    protected void leave(Player player) {
        VertigoPlayer vp = findPlayer(player);
        if (vp != null) {
            players.remove(vp);
            jumpers.remove(vp);
        }
    }

    protected void start() {
        stateChange(state, GameState.COUNTDOWN_TO_START);
        state = GameState.COUNTDOWN_TO_START;
    }

    protected void end() {
        findWinner();
        callMatchEvent();
        state = GameState.ENDED;
        stateChange(GameState.RUNNING, state);
    }

    protected void reset() {
        state = GameState.READY;
        //roundNumber = 0;
        map.reset();
        for (VertigoPlayer vp : players) {
            if (vp.wasPlaying) {
                vp.isPlaying = true;
            }
        }
    }

    protected void shutdown() {
        shutdown = true;
    }

    protected void onTick() {
        ticks++;
        if (shutdown) {
            return;
        }
        GameState newState = null;
        // Check for disconnects.
        removeDisconnectedPlayers();
        if (state == GameState.RUNNING) {
            // Check if only one player is left.
            int aliveCount = 0;
            for (VertigoPlayer vp : players) {
                if (vp.isPlaying) {
                    aliveCount++;
                }
            }
            if (aliveCount == 0 || (aliveCount == 1 && moreThanOnePlayed && !testing)) {
                log("Stopping game because of alive count");
                newState = GameState.ENDED;
            } else if (map.getWaterLeft() <= 0) {
                log("Stopping game because there is no water left");
                newState = GameState.ENDED;
            } else if (currentJumperIndex == 0 && isSomeoneLeadingBy(10)) {
                log("Stopping game because someone is leading by 10");
                newState = GameState.ENDED;
            }
            if (newState == GameState.ENDED) {
                findWinner();
                callMatchEvent();
            }
        }
        if (newState == null) {
            newState = tickState(state);
        }
        if (newState != null && newState != state) {
            stateChange(state, newState);
            state = newState;
        }
    }

    private boolean isSomeoneLeadingBy(int amount) {
        List<Integer> list = new ArrayList<>(players.size());
        for (VertigoPlayer vp : players) {
            if (!vp.isPlaying) continue;
            list.add(vp.score);
        }
        if (list.size() < 2) return false;
        Collections.sort(list);
        return list.get(list.size() - 1) - list.get(list.size() - 2) >= amount;
    }

    private GameState tickState(GameState theState) {
        stateTicks++;
        if (theState == GameState.READY) {
            return tickReady(stateTicks);
        } else if (theState == GameState.COUNTDOWN_TO_START) {
            return tickCountdown(stateTicks);
        } else if (theState == GameState.RUNNING) {
            return tickRunning(stateTicks);
        } else if (theState == GameState.ENDED) {
            return tickEnded(stateTicks);
        }
        return null;
    }

    // Game is ready to accept players.
    private GameState tickReady(long theTicks) {
        if (theTicks % (20) == 0) {
            updateBossBar(0);
        }
        return null;
    }

    // Game is starting. Display a countdown to all players in the game world.
    private GameState tickCountdown(long theTicks) {
        long timeLeft = (countdownToStartDuration * 20) - theTicks;
        long seconds = timeLeft / 20;
        double progress = (((double) countdownToStartDuration * 20) - timeLeft) / ((double) countdownToStartDuration * 20);
        updateBossBar(progress);
        if (timeLeft % 20L == 0) {
            if (seconds == 0) {
                sendTitleToAllPlayers(empty(), empty());
                playSoundForAllPlayers(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST);
            } else if (seconds <= 5) {
                sendTitleToAllPlayers(text("Get ready!", GREEN), text("Game starts in " + seconds + " seconds", GREEN));
                playNoteForAllPlayers(Instrument.PIANO, new Note((int) seconds));
            }
        }
        if (timeLeft <= 0) {
            return GameState.RUNNING;
        }
        return null;
    }

    private GameState tickRunning(long theTicks) {
        double progress = (double) currentJumperIndex / jumpers.size();
        updateBossBar(progress);
        if (currentJumper != null && !currentJumperHasJumped) {
            jumperTicks++;
            // 20 seconds to jump.
            long total = 20 * 20;
            if (jumperTicks >= total + 20) {
                playerTimedOut(currentJumper);
                // Player didn't jump in time.
            } else {
                if (jumperTicks % 20 == 0) {
                    long ticksLeft = total - jumperTicks;
                    long secsLeft = ticksLeft / 20;
                    Component msg = textOfChildren(text(" You have ", WHITE),
                                                   text(secsLeft, DARK_AQUA),
                                                   text(" second" + (secsLeft > 1 ? "s" : "") + " to jump", WHITE));
                    if (secsLeft <= 3) {
                        msg = textOfChildren(text(" You have ", DARK_RED),
                                             text(secsLeft, RED),
                                             text(" second" + (secsLeft > 1 ? "s" : "") + " to jump", DARK_RED));
                    }
                    Player player = currentJumper.getPlayer();
                    if (player != null) player.sendActionBar(msg);
                }
            }
        }
        return null;
    }

    private GameState tickEnded(long theTicks) {
        updateBossBar(1);
        long timeLeft = (endDuration * 20) - theTicks;
        // Every 5 seconds, show/refresh the winner title announcement.
        if (timeLeft > 0 && (timeLeft % (20 * 5) == 0 || theTicks == 1)) {
            if (!winnerName.equals("")) {
                sendTitleToAllPlayers(text(winnerName, AQUA), text("Wins the Game!", WHITE));
            } else {
                sendTitleToAllPlayers(text("Draw!", AQUA), text("Nobody wins", WHITE));
            }
        }
        if (!MapReview.isActive(world)) {
            MapReview.start(world, buildWorld);
        }
        MapReview.in(world).remindAllOnce();
        return null;
    }

    private void stateChange(GameState oldState, GameState newState) {
        log("State " + oldState + " => " + newState);
        stateTicks = 0;
        if (newState == GameState.COUNTDOWN_TO_START) {
            int count = 0;
            for (VertigoPlayer vp : players) {
                if (vp.isPlaying) {
                    count++;
                }
            }
            moreThanOnePlayed = count > 1;
            // Make jump order.
            jumpers.clear();
            for (VertigoPlayer vp : players) {
                if (vp.isPlaying) {
                    jumpers.add(vp);
                }
            }
            currentJumperIndex = 0;
        } else if (newState == GameState.RUNNING) {
            startRound();
        } else if (newState == GameState.ENDED) {
            if (!winnerName.equals("")) {
                sendMsgToAllPlayers(textOfChildren(plugin.TITLE, text(winnerName + " wins the game!", BLUE)));
                if (plugin.state.event && !testing) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + winnerName + " Splash Jumper WaterBucket");
                }
            } else {
                sendMsgToAllPlayers(textOfChildren(plugin.TITLE, text("Draw! Nobody wins.", AQUA)));
            }
            playSoundForAllPlayers(Sound.UI_TOAST_CHALLENGE_COMPLETE);
            for (VertigoPlayer vp : players) {
                Player player = vp.getPlayer();
                if (player != null) player.setGameMode(GameMode.SPECTATOR);
            }
        }
    }

    private void startRound() {
        currentRound += 1;
        int order = 1;
        Collections.shuffle(jumpers);
        Collections.sort(jumpers, comparingInt(VertigoPlayer::getScore));
        for (VertigoPlayer vp : jumpers) {
            vp.order = order++;
        }
        currentJumperPassedRing = false;
        map.removeRing();
        if (map.spawnRing()) {
            playSoundForAllPlayers(Sound.BLOCK_BEACON_ACTIVATE);
            sendMsgToAllPlayers(textOfChildren(plugin.TITLE,
                                               text(" A ", WHITE),
                                               text("golden ring ", GOLD),
                                               text("has appeared. Jump through it to earn bonus points!", WHITE)));
        }
        currentJumperIndex = 0;
        onPlayerTurn(jumpers.get(currentJumperIndex));
    }

    private void onPlayerTurn(VertigoPlayer vp) {
        vp.setJumper();
        Player player = vp.getPlayer();
        if (player != null) player.teleport(map.getJumpSpot());
        Random r = new Random(System.currentTimeMillis());
        String[] strings = {"It's your turn.", "You're up!", "You're next."};
        String[] strings2 = {"Good luck ツ", "Don't break a leg!", "Geronimoooo!"};
        if (player != null) {
            Component msg = text(strings[r.nextInt(strings.length)] + " " + strings2[r.nextInt(strings2.length)], GOLD);
            player.sendMessage(msg);
            player.showTitle(title(empty(), msg,
                                   times(Duration.ofSeconds(0),
                                         Duration.ofSeconds(1),
                                         Duration.ofSeconds(0))));
            player.playSound(player.getEyeLocation(), Sound.BLOCK_BELL_USE, SoundCategory.MASTER, 1, 1);
        }
        jumperTicks = 0;
        currentJumper = vp;
    }

    protected void playerDamage(Player player, EntityDamageEvent event) {
        // Player is below jump threshold and took fall damage. Ie he didn't land in water.
        if (event.getCause() == DamageCause.FALL || event.getCause() == DamageCause.VOID) {
            playerLandedBadly(player, player.getLocation());
        }
    }

    protected void playerMove(Player player, PlayerMoveEvent event) {
        // Disregard if player is not in adv mode.
        if (player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }
        // Disregard if player Y is above the jump threshold.
        if (player.getLocation().getY() > map.getJumpThresholdY()) {
            return;
        }
        if (!currentJumperHasJumped) {
            currentJumperHasJumped = true;
            player.sendActionBar(empty());
        }
        if (map.currentRingCenter != null && !currentJumperPassedRing) {
            int rx = map.currentRingCenter.getBlockX();
            int ry = map.currentRingCenter.getBlockY();
            int rz = map.currentRingCenter.getBlockZ();
            Location f = event.getFrom();
            Location t = event.getTo();
            if ((t.getBlockX() == rx && t.getBlockZ() == rz || f.getBlockX() == rx && f.getBlockZ() == rz) && (t.getBlockY() <= ry && f.getBlockY() >= ry)) {
                world.spawnParticle(Particle.REDSTONE, map.currentRingCenter, 50, 2f, 2f, 2f, .2f, new Particle.DustOptions(org.bukkit.Color.YELLOW, 10.0f));
                currentJumperPassedRing = true;
            }
        }
        Location l = event.getTo();
        // Check if player landed in water.
        final Block block = l.getBlock();
        if (block.getType() == Material.WATER && block.getBlockData() instanceof Levelled level && level.getLevel() == 0) {
            playerLandedInWater(player, l);
        }
    }

    private void playerLandedBadly(Player player, Location landingLocation) {
        VertigoPlayer vp = findPlayer(player);
        if (vp == null) return;
        player.sendActionBar(empty());
        world.spawnParticle(Particle.EXPLOSION_LARGE, landingLocation, 50, .5f, .5f, .5f, .5f);
        vp.setSpectator();
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0);
        player.teleport(vp.getSpawnLocation());
        //player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1, 1);
        Component msg = textOfChildren(text("Splat! ", RED), text(player.getName(), DARK_AQUA));
        int score = 0;
        if (currentJumperPassedRing) {
            score += 1;
            vp.score += score;
            if (plugin.state.event) {
                plugin.state.addScore(vp.uuid, score);
                plugin.computeHighscore();
                plugin.saveState();
            }
            map.removeRing();
            msg = textOfChildren(text("Splat! ", RED), text(player.getName() + " ", DARK_AQUA), text(score + " point", GOLD));
        }
        sendTitleToAllPlayers(empty(), msg);
        sendMsgToAllPlayers(msg);
        playSoundForAllPlayers(Sound.ENTITY_PLAYER_BIG_FALL);
        callNextJumper();
    }

    private void playerLandedInWater(Player player, Location landingLocation) {
        VertigoPlayer vp = findPlayer(player);
        if (vp == null) return;
        player.sendActionBar(empty());
        vp.setSpectator();
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0);
        player.teleport(vp.getSpawnLocation());
        int score = 1;
        Block block = landingLocation.getBlock();
        // Find adjacent blocks to calculate score.
        int adjacentBlocks = 0;
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            if (map.isBlock(block.getRelative(face))) {
                adjacentBlocks += 1;
            }
        }
        int diagonalBlocks = 0;
        for (BlockFace face : List.of(BlockFace.NORTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST, BlockFace.NORTH_WEST)) {
            if (map.isBlock(block.getRelative(face))) {
                diagonalBlocks += 1;
            }
        }
        int diagonalScore = diagonalBlocks / 2;
        score += adjacentBlocks + diagonalScore;
        if (currentJumperPassedRing) {
            score += 3;
            map.removeRing();
        }
        Component msg = join(noSeparators(),
                             text(tiny("Splash! "), BLUE),
                             text(player.getName(), WHITE),
                             (adjacentBlocks == 0 ? empty() : text(tiny(" adjacent") + adjacentBlocks, GRAY)),
                             (diagonalScore == 0 ? empty() : text(tiny(" diagonal") + diagonalScore, GRAY)),
                             (!currentJumperPassedRing ? empty() : text(tiny(" ring") + 3, GRAY)),
                             text(tiny(" total"), GRAY),
                             text(score, GOLD));
        sendTitleToAllPlayers(empty(), msg);
        sendMsgToAllPlayers(msg);
        if (currentJumperPassedRing) {
            playSoundForAllPlayers(Sound.ENTITY_PLAYER_LEVELUP);
        } else {
            playSoundForAllPlayers(Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED);
        }
        player.showTitle(title(empty(), textOfChildren(text("Splash! ", BLUE), text(score + " point" + (score > 1 ? "s" : ""), GOLD))));
        vp.score += score;
        if (plugin.state.event) {
            plugin.state.addScore(vp.uuid, score);
            plugin.computeHighscore();
            plugin.saveState();
        }
        world.spawnParticle(Particle.WATER_SPLASH, landingLocation, 50, .5f, 4f, .5f, .1f);
        ItemStack r = map.getRandomBlock();
        landingLocation.getBlock().setType(r.getType(), true);
        Block head = landingLocation.getBlock().getRelative(BlockFace.UP);
        Rotatable skullData = (Rotatable) Material.PLAYER_HEAD.createBlockData();
        int orientation = new Random(System.currentTimeMillis()).nextInt(12);
        if (orientation == 0) {
            skullData.setRotation(BlockFace.EAST);
        } else if (orientation == 1) {
            skullData.setRotation(BlockFace.WEST);
        } else if (orientation == 2) {
            skullData.setRotation(BlockFace.NORTH);
        } else if (orientation == 3) {
            skullData.setRotation(BlockFace.SOUTH);
        } else if (orientation == 4) {
            skullData.setRotation(BlockFace.NORTH_EAST);
        } else if (orientation == 5) {
            skullData.setRotation(BlockFace.NORTH_NORTH_EAST);
        } else if (orientation == 6) {
            skullData.setRotation(BlockFace.NORTH_NORTH_WEST);
        } else if (orientation == 7) {
            skullData.setRotation(BlockFace.NORTH_WEST);
        } else if (orientation == 8) {
            skullData.setRotation(BlockFace.SOUTH_EAST);
        } else if (orientation == 9) {
            skullData.setRotation(BlockFace.SOUTH_SOUTH_EAST);
        } else if (orientation == 10) {
            skullData.setRotation(BlockFace.SOUTH_SOUTH_WEST);
        } else if (orientation == 11) {
            skullData.setRotation(BlockFace.SOUTH_WEST);
        }
        head.setBlockData(skullData);
        Skull s = (Skull) head.getState();
        s.setPlayerProfile(player.getPlayerProfile());
        s.update();
        map.addBlock();
        map.addSkull(head);
        callNextJumper();
    }

    private void playerTimedOut(VertigoPlayer vp) {
        Player player = vp.getPlayer();
        if (player != null) player.sendActionBar(empty());
        Random r = new Random(System.currentTimeMillis());
        String[] strings = {
            "was too afraid to jump.",
            "chickened out.",
            "couldn't overcome their acrophobia.",
            "had a bad case of vertigo."
        };
        String string = strings[r.nextInt(strings.length)];
        sendMsgToAllPlayers(textOfChildren(plugin.TITLE, text(vp.name + " ", DARK_AQUA), text(string, RED)));
        // This might be fired after the player has disconnected.
        vp.setSpectator();
        vp.timeouts++;
        // Player has most likely gone afk.
        if (vp.timeouts >= 3) {
            vp.isPlaying = false;
            sendMsgToAllPlayers(textOfChildren(plugin.TITLE, text(vp.name + " ", RED), text("was disqualified for failing to make 3 jumps in time.", GRAY)));
        }
        if (player != null) {
            player.teleport(vp.getSpawnLocation());
            player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1, 1);
        }
        callNextJumper();
    }

    private void callNextJumper() {
        currentJumperHasJumped = false;
        currentJumperPassedRing = false;
        boolean advance = false;
        if (currentJumper != null) currentJumperIndex = jumpers.indexOf(currentJumper);
        currentJumperIndex++;
        // All players have jumped.
        if (currentJumperIndex >= jumpers.size()) {
            advance = true;
        } else {
            VertigoPlayer vp = jumpers.get(currentJumperIndex);
            if (vp.isPlaying) {
                onPlayerTurn(vp);
            } else {
                currentJumper = null;
                callNextJumper();
            }
        }
        if (advance) {
            //startRoundCountdown();
            if (currentRound >= maxRounds) {
                log("Stopping game because max rounds exceeded");
                end();
            } else {
                startRound();
            }
        }
    }

    private void updateBossBar(double progress) {
        if (state == GameState.READY) {
            int count = 0;
            for (VertigoPlayer vp : players) {
                if (vp.isPlaying) {
                    count++;
                }
            }
            bossBar.name(textOfChildren(plugin.TITLE,
                                               text(" waiting for players. ", WHITE),
                                               text(count, GOLD),
                                               text(" joined so far.", WHITE)));
        } else if (state == GameState.COUNTDOWN_TO_START) {
            bossBar.name(textOfChildren(plugin.TITLE, text(" starting...", WHITE)));
        } else if (state == GameState.RUNNING) {
            List<Component> t = new ArrayList<>();
            if (map.currentRingCenter != null) {
                t.add(textOfChildren(Mytems.GOLDEN_HOOP, text("Golden ring", GOLD)));
            }
            t.add(textOfChildren(text(tiny("round"), GRAY),
                                 text(superscript(currentRound), WHITE),
                                 text("/", GRAY), text(subscript(maxRounds), WHITE)));
            if (currentJumper != null) {
                t.add(textOfChildren(text(tiny("jumper"), GRAY),
                                     text(currentJumper.name, AQUA),
                                     text(superscript(currentJumperIndex + 1), WHITE),
                                     text("/", GRAY),
                                     text(subscript(jumpers.size()), WHITE)));
            }
            bossBar.name(join(separator(space()), t));
        } else if (state == GameState.ENDED) {
            bossBar.name(!winnerName.equals("")
                                ? textOfChildren(text("Winner: ", WHITE), text(winnerName, AQUA))
                                : text("It's a draw!", GRAY));
        }
        if (Double.isNaN(progress)) {
            progress = 0;
        }
        bossBar.progress((float) progress);
    }

    private void sendMsgToAllPlayers(Component msg) {
        for (Player player : world.getPlayers()) {
            player.sendMessage(msg);
        }
    }

    private void sendTitleToAllPlayers(Component title, Component subtitle) {
        for (Player player : world.getPlayers()) {
            player.showTitle(title(title, subtitle));
        }
    }

    private void playSoundForAllPlayers(Sound sound) {
        for (VertigoPlayer vp : players) {
            Player player = vp.getPlayer();
            if (player != null) {
                player.playSound(player.getEyeLocation(), sound, SoundCategory.MASTER, 1f, 1f);
            }
        }
    }

    private void playNoteForAllPlayers(Instrument instrument, Note note) {
        for (VertigoPlayer vp : players) {
            Player player = vp.getPlayer();
            if (player != null) {
                player.playNote(player.getEyeLocation(), instrument, note);
            }
        }
    }

    public Location getSpawnLocation() {
        return world.getSpawnLocation();
    }

    protected VertigoPlayer findPlayer(Player player) {
        for (VertigoPlayer vp : players) {
            if (vp.uuid.equals(player.getUniqueId())) {
                return vp;
            }
        }
        return null;
    }

    protected boolean hasPlayerJoined(Player player) {
        return findPlayer(player) != null;
    }

    private List<VertigoPlayer> getWinners() {
        int max = 0;
        for (VertigoPlayer vp : players) {
            if (vp.isPlaying && vp.score > max) {
                max = vp.score;
            }
        }
        List<VertigoPlayer> winners = new ArrayList<>(players.size());
        for (VertigoPlayer vp : players) {
            if (vp.isPlaying && vp.score >= max) {
                winners.add(vp);
            }
        }
        return winners;
    }

    private void findWinner() {
        List<VertigoPlayer> winners = getWinners();
        for (var it : winners) winnerUuids.add(it.uuid);
        winnerName = "";
        if (winners.size() == 1) {
            winnerName = winners.get(0).name;
        } else {
            String c = "";
            for (int i = 0; i < winners.size(); i++) {
                c += winners.get(i).name;
                int left = winners.size() - (i + 1);
                if (left == 1) {
                    c += " and ";
                } else if (left > 1) {
                    c += ", ";
                }
            }
            winnerName = c;
        }
    }

    private void removeDisconnectedPlayers() {
        if (players.size() > 0) {
            List<VertigoPlayer> list = new ArrayList<>();
            for (VertigoPlayer vp : players) {
                Player p = vp.getPlayer();
                boolean remove = false;
                if (p == null) {
                    remove = true;
                }
                if (p != null && !p.isOnline()) {
                    remove = true;
                }
                if (p != null && !p.getWorld().getName().equals(world.getName())) {
                    remove = true;
                }
                if (!remove) {
                    list.add(vp);
                }
            }
            players = list;
        }
    }

    private void callMatchEvent() {
        if (testing) return;
        MinigameMatchCompleteEvent event = new MinigameMatchCompleteEvent(MinigameMatchType.VERTIGO);
        if (plugin.state.event) event.addFlags(MinigameFlag.EVENT);
        for (VertigoPlayer vp : players) event.addPlayerUuid(vp.uuid);
        event.addWinnerUuids(winnerUuids);
        event.callEvent();
    }

    public int getPlayerCount() {
        int result = 0;
        for (VertigoPlayer it : players) {
            if (it.isPlaying) result += 1;
        }
        return result;
    }

    protected void makeSidebar(Player player, List<Component> lines) {
        lines.add(textOfChildren(text(tiny("round "), GRAY),
                                 text(superscript(currentRound), WHITE),
                                 text("/", GRAY), text(subscript(maxRounds), WHITE)));
        List<VertigoPlayer> activePlayers = new ArrayList<>();
        for (VertigoPlayer vp : players) {
            if (!vp.isPlaying) continue;
            activePlayers.add(vp);
        }
        Collections.sort(activePlayers, comparingInt(VertigoPlayer::getScore).reversed());
        // Notify spectators very clearly at all times.
        VertigoPlayer vertigoPlayer = findPlayer(player);
        if (vertigoPlayer != null) {
            if (!vertigoPlayer.isPlaying && !vertigoPlayer.wasPlaying) {
                lines.add(text("You're spectating!", YELLOW));
            }
            if (vertigoPlayer.isPlaying && vertigoPlayer.order > 0) {
                lines.add(textOfChildren(text(tiny("your jump "), GRAY),
                                         text(superscript(vertigoPlayer.order), WHITE),
                                         text("/", GRAY),
                                         text(subscript(jumpers.size()), WHITE)));
                lines.add(textOfChildren(text(tiny("score "), GRAY),
                                         text(vertigoPlayer.score, WHITE)));
            }
        }
        int placement = 0;
        int lastScore = -1;
        for (VertigoPlayer vp : activePlayers) {
            if (lastScore != vp.score) {
                lastScore = vp.score;
                placement += 1;
            }
            boolean jumping = currentJumper == vp;
            lines.add(join(noSeparators(),
                           (jumping ? WATER_BUCKET.component : empty()),
                           Glyph.toComponent("" + placement),
                           text(subscript(vp.score), AQUA),
                           space(),
                           text(vp.name),
                           text(superscript(vp.order), DARK_GRAY)));
        }
    }
}
