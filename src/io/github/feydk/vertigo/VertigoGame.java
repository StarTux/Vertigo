package io.github.feydk.vertigo;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

import static io.github.feydk.vertigo.VertigoLoader.chatPrefix;

class VertigoGame
{
    VertigoLoader loader;
    World world;
    GameMap map;
    String mapName;
    GameState state = GameState.NONE;
    List<VertigoPlayer> players = new ArrayList<>();
    GameScoreboard scoreboard;

    private BossBar gamebar;

    // Game loop stuff.
    private boolean shutdown;
    private long ticks;
    private long stateTicks;
    private int countdownToStartDuration;
    private int endDuration;

    // Stuff for keeping track of jumpers.
    List<VertigoPlayer> jumpers = new ArrayList<>();
    private int currentJumperIndex = 0;
    private long jumperTicks = 0;
    private boolean currentJumperHasJumped;
    private VertigoPlayer currentJumper;
    private boolean currentJumperPassedRing;
    private boolean moreThanOnePlayed;
    private String winnerName;

    // Stuff for keeping track of rounds.
    //private int roundNumber = 0;
    //private int roundCountdownTicks = -1;

    enum GameState
    {
        NONE,                   // Nothing has happened yet
        INIT,                   // Game has been set up, but not ready
        READY,                  // Game is ready to accept players
        COUNTDOWN_TO_START,     // Countdown to first round has started
        RUNNING,                // Game/a round is running
        ENDED                 // Game is over
    }

    public VertigoGame(VertigoLoader loader)
    {
        this.loader = loader;

        gamebar = loader.getServer().createBossBar(ChatColor.BOLD + "Vertigo", BarColor.BLUE, BarStyle.SOLID);
        gamebar.setProgress(0);
        gamebar.setVisible(false);

        countdownToStartDuration = loader.getConfig().getInt("general.countdownToStartDuration");
        endDuration = loader.getConfig().getInt("general.endDuration");
    }

    void setWorld(World world, String mapName)
    {
        this.world = world;
        this.mapName = mapName;
    }

    boolean setup(Player admin)
    {
        if(world == null)
        {
            admin.sendMessage(ChatColor.RED + "You must load a world first.");
            return false;
        }

        shutdown = false;
        players.clear();
        jumpers.clear();
        updateGamebar(0);

        scoreboard = new GameScoreboard(this);

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
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setStorm(false);

        map = new GameMap(loader.getConfig().getInt("general.chunkRadius"), this);

        boolean ready = map.process(getSpawnLocation().getChunk());

        if(ready)
        {
            if(map.getStartingTime() == -1)
                world.setTime(1000L);
            else
                world.setTime(map.getStartingTime());

            if(map.getLockTime())
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            else
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);

            state = GameState.INIT;
        }
        else
        {
            admin.sendMessage(ChatColor.RED + "The game could not be set up. Check console for hints.");
        }

        return ready;
    }

    void discard()
    {
        setWorld(null, "");
        state = GameState.NONE;

        for(VertigoPlayer vp : players)
        {
            leave(vp.getPlayer());
        }
    }

    void ready(Player admin)
    {
        if(state != GameState.INIT)
        {
            admin.sendMessage(ChatColor.RED + "No can do. Game hasn't been set up yet.");
            return;
        }

        state = GameState.READY;
        updateGamebar(0);

        BukkitRunnable task = new BukkitRunnable()
        {
            @Override
            public void run()
            {
                if(shutdown)
                    this.cancel();

                onTick();
            }
        };

        task.runTaskTimer(loader, 1, 1);
    }

    void join(Player player, boolean spectator)
    {
        VertigoPlayer vp = findPlayer(player);

        if(vp == null)
        {
            vp = new VertigoPlayer(this, player);
            players.add(vp);

            scoreboard.addPlayer(player);
            gamebar.addPlayer(player);

            if(vp.isPlaying)
                scoreboard.setPlayerScore(player, 0);
        }

        vp.isPlaying = !spectator;
        vp.wasPlaying = vp.isPlaying;
        vp.getPlayer().setGameMode(GameMode.SPECTATOR);

        if(vp.isPlaying)
            jumpers.add(vp);

        player.removePotionEffect(PotionEffectType.LEVITATION);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);

        player.teleport(vp.getSpawnLocation());
        player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1, 1);

        player.sendTitle("Welcome to Vertigo", ChatColor.YELLOW + "See /vertigo for game status and info", -1, -1, -1);

        /*BukkitRunnable task = new BukkitRunnable()
        {
            private int count = 0;

            @Override
            public void run()
            {
                if(shutdown)
                    this.cancel();

                player.sendActionBar("Welcome to Vertigo! This map was created by " + ChatColor.AQUA + map.getCredits());
                count++;

                if(count > 3)
                {
                    player.sendActionBar(" ");
                    this.cancel();
                }
            }
        };

        task.runTaskTimer(loader, 0, 40);*/
    }

    void leave(Player player)
    {
        if(player != null)
        {
            if(player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.ADVENTURE)
                player.setGameMode(GameMode.SURVIVAL);

            gamebar.removePlayer(player);
            scoreboard.removePlayer(player);
            players.remove(player);
        }
    }

    void start()
    {
        stateChange(state, GameState.COUNTDOWN_TO_START);
        state = GameState.COUNTDOWN_TO_START;
    }

    void end()
    {
        /*if(this.players.size() > 0)
        {
            for(VertigoPlayer vp : players)
            {
                vp.setSpectator();
            }
        }*/

        findWinner();
        state = GameState.ENDED;
        stateChange(GameState.RUNNING, state);
    }

    void reset()
    {
        state = GameState.READY;
        scoreboard.reset();
        //roundNumber = 0;
        map.reset();

        for(VertigoPlayer vp : players)
        {
            scoreboard.addPlayer(vp.getPlayer());

            if(vp.isPlaying)
                scoreboard.setPlayerScore(vp.getPlayer(), 0);

            if(vp.wasPlaying)
                vp.isPlaying = true;
        }
    }

    void shutdown()
    {
        shutdown = true;
        scoreboard.reset();
    }

    private void onTick()
    {
        ticks++;

        if(shutdown)
            return;

        GameState newState = null;

        // Check for disconnects.
        removeDisconnectedPlayers();

        // Notify spectators very clearly at all times.
        for(VertigoPlayer vp : players)
        {
            if(!vp.isPlaying && !vp.wasPlaying)
                vp.getPlayer().sendActionBar("You're " + ChatColor.YELLOW + "spectating");
        }

        if(state == GameState.RUNNING)
        {
            // Check if only one player is left.
            int aliveCount = 0;

            for(VertigoPlayer vp : players)
            {
                if(vp.isPlaying)
                    aliveCount++;
            }

            if(aliveCount == 0 || (aliveCount == 1 && moreThanOnePlayed) || map.getWaterLeft() <= 0)
            {
                newState = GameState.ENDED;
            }
            else
            {
                // At the start of each "round", check if someone is 10 points ahead. If yes, end the game.
                if(currentJumperIndex == 0 && scoreboard.isSomeoneLeadingBy(10, players))
                {
                    newState = GameState.ENDED;
                }
            }

            if(newState == GameState.ENDED)
                findWinner();
        }

        if(newState == null)
        {
            newState = tickState(state);
        }

        if(newState != null && newState != state)
        {
            stateChange(state, newState);
            state = newState;
        }
    }

    private GameState tickState(GameState state)
    {
        stateTicks++;

        if(state == GameState.READY)
        {
            return tickReady(stateTicks);
        }
        else if(state == GameState.COUNTDOWN_TO_START)
        {
            return tickCountdown(stateTicks);
        }
        else if(state == GameState.RUNNING)
        {
            return tickRunning(stateTicks);
        }
        else if(state == GameState.ENDED)
        {
            return tickEnded(stateTicks);
        }

        return null;
    }

    // Game is ready to accept players.
    private GameState tickReady(long ticks)
    {
        if(ticks % (20) == 0)
        {
            updateGamebar(0);
        }

        return null;
    }

    // Game is starting. Display a countdown to all players in the game world.
    private GameState tickCountdown(long ticks)
    {
        long timeLeft = (countdownToStartDuration * 20) - ticks;
        long seconds = timeLeft / 20;
        double progress = (((double)countdownToStartDuration * 20) - timeLeft) / ((double)countdownToStartDuration * 20);
        updateGamebar(progress);

        if(timeLeft % 20L == 0)
        {
            if(seconds == 0)
            {
                sendTitleToAllPlayers("", "");
                playSoundForAllPlayers(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST);
            }
            else if(seconds <= 5)
            {
                sendTitleToAllPlayers(ChatColor.GREEN + "Get ready!", ChatColor.GREEN + "Game starts in " + seconds + " seconds");
                playNoteForAllPlayers(Instrument.PIANO, new Note((int)seconds));
            }
        }

        if(timeLeft <= 0)
            return GameState.RUNNING;

        return null;
    }

    private GameState tickRunning(long ticks)
    {
        double progress = (double)currentJumperIndex / jumpers.size();
        updateGamebar(progress);

        if(currentJumper != null && !currentJumperHasJumped)
        {
            jumperTicks++;

            // 10 seconds to jump.
            long total = 10 * 20;

            if(jumperTicks >= total + 20) // 1 second of forgiveness
            {
                playerTimedOut(currentJumper);

                // Player didn't jump in time.
            }
            else
            {
                if(jumperTicks % 20 == 0)
                {
                    long ticksLeft = total - jumperTicks;
                    long secsLeft = ticksLeft / 20;

                    String msg = "§f You have §3" + secsLeft + " §fsecond" + (secsLeft > 1 ? "s" : "") + " to jump";

                    if(secsLeft <= 3)
                        msg = "§4 You have §c" + secsLeft + " §4second" + (secsLeft > 1 ? "s" : "") + " to jump";

                    currentJumper.getPlayer().sendActionBar(msg);
                }
            }
        }

        // Let everyone else know when its their turn.
        for(VertigoPlayer vp : jumpers)
        {
            if(currentJumper.equals(vp))
                continue;

            String msg = "You jump as number " + ChatColor.GREEN + vp.order + ". " + ChatColor.WHITE;
            int diff = vp.order - (currentJumperIndex + 1);

            if(diff == 1 || diff <= 0)
            {
                msg += "You're next!";
            }
            else
            {
                msg += " There are " + diff + " players in front of you.";
            }

            vp.getPlayer().sendActionBar(msg);
        }

        /*if(roundCountdownTicks >= 0)
        {
            long ticksTmp = roundCountdownTicks;
            roundCountdownTicks++;
            long total = 3 * 20;

            // Leave two seconds of "silence".
            if(ticksTmp >= 40 && ticksTmp % 20 == 0)
            {
                long secsLeft = (total - ticksTmp) / 20;

                if(secsLeft <= 0)
                {
                    sendTitleToAllPlayers("", "");
                    roundCountdownTicks = -1;
                    startRound();
                }
                else if(secsLeft == 3)
                {
                    sendTitleToAllPlayers("", ChatColor.GOLD + "Round " + (roundNumber + 1));
                }

                playNoteForAllPlayers(Instrument.PIANO, new Note((int)secsLeft));
            }
        }*/

        return null;
    }

    private GameState tickEnded(long ticks)
    {
        updateGamebar(1);

        long timeLeft = (endDuration * 20) - ticks;

        // Every 5 seconds, show/refresh the winner title announcement.
        if(timeLeft > 0 && (timeLeft % (20 * 5) == 0 || ticks == 1))
        {
            if(!winnerName.equals(""))
                sendTitleToAllPlayers(ChatColor.AQUA + winnerName, ChatColor.WHITE + "Wins the Game!");
            else
                sendTitleToAllPlayers(ChatColor.AQUA + "Draw!", ChatColor.WHITE + "Nobody wins");
        }

        return null;
    }

    private void stateChange(GameState oldState, GameState newState)
    {
        stateTicks = 0;

        if(newState == GameState.COUNTDOWN_TO_START)
        {
            //scoreboard.reset();
            int count = 0;

            for(VertigoPlayer vp : players)
            {
                if(vp.isPlaying)
                {
                    scoreboard.addPlayer(vp.getPlayer());
                    scoreboard.setPlayerScore(vp.getPlayer(), 0);

                    count++;
                }
            }

            moreThanOnePlayed = count > 1;

            // Make jump order.
            jumpers.clear();

            for(VertigoPlayer vp : players)
            {
                if(vp.isPlaying)
                {
                    jumpers.add(vp);
                }
            }

            Collections.shuffle(jumpers, new Random(System.currentTimeMillis()));

            int i = 1;
            for(VertigoPlayer vp : jumpers)
            {
                vp.order = i;
                i++;
            }

            currentJumperIndex = 0;
        }
        else if(newState == GameState.RUNNING)
        {
            startRound();
        }
        else if(newState == GameState.ENDED)
        {
            if(!winnerName.equals(""))
                sendMsgToAllPlayers(chatPrefix + "&b" + winnerName + " wins the game!");
            else
                sendMsgToAllPlayers( chatPrefix + "&bDraw! Nobody wins.");

            playSoundForAllPlayers(Sound.UI_TOAST_CHALLENGE_COMPLETE);

            for(VertigoPlayer vp : players)
            {
                vp.getPlayer().setGameMode(GameMode.SPECTATOR);

                /*List<Object> list = new ArrayList<>();
                list.add(" Click here to leave the game: ");
                list.add(button("&c[Quit]", "&cLeave this game", "/quit"));
                Msg.sendRaw(player, list);*/
            }
        }
    }

    /*private void startRoundCountdown()
    {
        sendActionBarToAllPlayers("Round over. Get ready for the next round.");

        roundCountdownTicks = 0;
    }*/

    private void startRound()
    {
        currentJumperPassedRing = false;

        map.removeRing();

        if(map.spawnRing())
        {
            playSoundForAllPlayers(Sound.BLOCK_BEACON_ACTIVATE);
            //sendMsgToAllPlayers(chatPrefix + "§fA §6golden ring §fhas appeared. Jump through it to earn bonus points!");
        }

        currentJumperIndex = 0;

        onPlayerTurn(jumpers.get(currentJumperIndex));
    }

    private void onPlayerTurn(VertigoPlayer vp)
    {
        scoreboard.setPlayerCurrent(vp.getPlayer());

        vp.setJumper();

        //vp.getPlayer().playSound(vp.getPlayer().getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1, 1);
        vp.getPlayer().teleport(map.getJumpSpot());

        Random r = new Random(System.currentTimeMillis());
        String[] strings = { "It's your turn.", "You're up!", "You're next." };
        String[] strings2 = { "Good luck ツ", "Don't break a leg!", "Geronimoooo!" };

        vp.getPlayer().sendTitle("", "§6" + strings[r.nextInt(strings.length)] + " " + strings2[r.nextInt(strings2.length)], -1, -1, -1);
        //vp.getPlayer().playSound(vp.getPlayer().getLocation(), Sound.ENTITY_GHAST_SCREAM, SoundCategory.MASTER, 1, 1);

        /*BukkitRunnable task = new BukkitRunnable()
        {
            int i = 0;

            @Override
            public void run()
            {
                vp.getPlayer().playSound(vp.getPlayer().getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.MASTER, 1, 2);
                i++;

                if(i >= 3)
                    this.cancel();
            }
        };

        task.runTaskTimer(loader, 1, 5);*/

        vp.getPlayer().playSound(vp.getPlayer().getEyeLocation(), Sound.BLOCK_BELL_USE, SoundCategory.MASTER, 1, 1);

        jumperTicks = 0;
        currentJumper = vp;
    }

    void playerDamage(Player player, EntityDamageEvent event)
    {
        // Player is below jump threshold and took fall damage. Ie he didn't land in water.
        if(event.getCause() == EntityDamageEvent.DamageCause.FALL)
        {
            playerLandedBadly(player, player.getLocation());
        }
    }

    void playerMove(Player player, PlayerMoveEvent event)
    {
        // Disregard if player is not in adv mode.
        if(player.getGameMode() != GameMode.ADVENTURE)
            return;

        // Disregard if player Y is above the jump threshold.
        if(player.getLocation().getY() > map.getJumpThresholdY())
            return;

        if(!currentJumperHasJumped)
        {
            currentJumperHasJumped = true;
            player.sendActionBar("");
        }
        if(map.currentRingCenter != null && !currentJumperPassedRing)
        {
            int rx = map.currentRingCenter.getBlockX();
            int ry = map.currentRingCenter.getBlockY();
            int rz = map.currentRingCenter.getBlockZ();
            Location f = event.getFrom();
            Location t = event.getTo();

            if((t.getBlockX() == rx && t.getBlockZ() == rz || f.getBlockX() == rx && f.getBlockZ() == rz) && (t.getBlockY() <= ry && f.getBlockY() >= ry))
            {
                world.spawnParticle(Particle.REDSTONE, map.currentRingCenter, 50, 2f, 2f, 2f, .2f, new Particle.DustOptions(org.bukkit.Color.YELLOW, 10.0f));
                currentJumperPassedRing = true;
            }
        }

        Location l = event.getTo();

        // Check if player landed in water.
        if(l.getBlock().getType() == Material.WATER)
        {
            playerLandedInWater(player, l);
        }
    }

    private void playerLandedBadly(Player player, Location landingLocation)
    {
        player.sendActionBar("");

        currentJumper = null;

        world.spawnParticle(Particle.EXPLOSION_LARGE, landingLocation, 50, .5f, .5f, .5f, .5f);

        VertigoPlayer vp = findPlayer(player);

        if(vp != null)
        {
            vp.setSpectator();
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0);
            player.teleport(vp.getSpawnLocation());
            //player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1, 1);
        }

        String msg = "§cSplat! §3" + player.getName();

        int score = 0;

        if(currentJumperPassedRing)
        {
            score += 1;
            scoreboard.addPoints(player, score);
            map.removeRing();

            msg = "§cSplat! §3" + player.getName() + " §6+" + score + " point";
        }

        //Random r = new Random(System.currentTimeMillis());
        //String[] strings = { "broke their legs.", "heard their bones being shattered.", "is in serious need of medical attention.", "believed they could fly." };
        //String string = strings[r.nextInt(strings.length)];


        //sendActionBarToAllPlayers(msg);
        sendTitleToAllPlayers("", msg);
        playSoundForAllPlayers(Sound.ENTITY_PLAYER_BIG_FALL);

        //player.sendTitle("", "§4Splat!", -1, -1, -1);

        callNextJumper();
    }

    private void playerLandedInWater(Player player, Location landingLocation)
    {
        player.sendActionBar("");

        currentJumper = null;

        VertigoPlayer vp = findPlayer(player);

        if(vp != null)
        {
            vp.setSpectator();
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0);
            player.teleport(vp.getSpawnLocation());
            //player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1, 1);
        }

        int score = 1;
        Location l = landingLocation.clone();

        // Find adjacent blocks to calculate score.
        List<Block> adjacent = new ArrayList<>();

        adjacent.add(world.getBlockAt(l.add(1, 0, 0)));
        l = landingLocation.clone();
        adjacent.add(world.getBlockAt(l.subtract(1, 0, 0)));
        l = landingLocation.clone();
        adjacent.add(world.getBlockAt(l.add(0, 0, 1)));
        l = landingLocation.clone();
        adjacent.add(world.getBlockAt(l.subtract(0, 0, 1)));

        int adjacentBlocks = 0;

        for(Block block : adjacent)
        {
            if(map.isBlock(block))
            {
                adjacentBlocks++;
                score++;
            }
        }

        if(currentJumperPassedRing)
        {
            score += 3;
            map.removeRing();
        }

        String msg = "§9Splash! §3" + player.getName() + " §6+" + score + "§7";
        sendTitleToAllPlayers("", msg);
        //sendActionBarToAllPlayers(msg);

        /*if(adjacentBlocks > 0)
        {
            msg += " (1 + adjacent blocks: " + adjacentBlocks;

            if(currentJumperPassedRing)
                msg += ", golden ring: 3";

            msg += ")";
        }
        else
        {
            if(currentJumperPassedRing)
                msg += " (1 + golden ring: 3)";
        }*/

        //sendMsgToAllPlayers(msg);

        if(currentJumperPassedRing)
            playSoundForAllPlayers(Sound.ENTITY_PLAYER_LEVELUP);
        else
            playSoundForAllPlayers(Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED);
        //  playSoundForAllPlayers(Sound.ENTITY_ARROW_HIT_PLAYER);

        player.sendTitle("", "§9Splash! §6+ " + score + " point" + (score > 1 ? "s" : ""), -1, -1, -1);

        scoreboard.addPoints(player, score);

        world.spawnParticle(Particle.WATER_SPLASH, landingLocation, 50, .5f, 4f, .5f, .1f);

        ItemStack r = map.getRandomBlock();

        landingLocation.getBlock().setType(r.getType(), true);

        Block head = landingLocation.getBlock().getRelative(BlockFace.UP);
        Rotatable skullData = (Rotatable)Material.PLAYER_HEAD.createBlockData();

        int orientation = new Random(System.currentTimeMillis()).nextInt(12);

        if(orientation == 0)
            skullData.setRotation(BlockFace.EAST);
        else if(orientation == 1)
            skullData.setRotation(BlockFace.WEST);
        else if(orientation == 2)
            skullData.setRotation(BlockFace.NORTH);
        else if(orientation == 3)
            skullData.setRotation(BlockFace.SOUTH);
        else if(orientation == 4)
            skullData.setRotation(BlockFace.NORTH_EAST);
        else if(orientation == 5)
            skullData.setRotation(BlockFace.NORTH_NORTH_EAST);
        else if(orientation == 6)
            skullData.setRotation(BlockFace.NORTH_NORTH_WEST);
        else if(orientation == 7)
            skullData.setRotation(BlockFace.NORTH_WEST);
        else if(orientation == 8)
            skullData.setRotation(BlockFace.SOUTH_EAST);
        else if(orientation == 9)
            skullData.setRotation(BlockFace.SOUTH_SOUTH_EAST);
        else if(orientation == 10)
            skullData.setRotation(BlockFace.SOUTH_SOUTH_WEST);
        else if(orientation == 11)
            skullData.setRotation(BlockFace.SOUTH_WEST);

        head.setBlockData(skullData);
        Skull s = (Skull)head.getState();
        //s.setOwningPlayer(player);
        s.setPlayerProfile(player.getPlayerProfile());
        s.update();

        map.addBlock();
        map.addSkull(head);

        //player.teleport(gp.getSpawnLocation());

        callNextJumper();
    }

    private void playerTimedOut(VertigoPlayer vp)
    {
        vp.getPlayer().sendActionBar("");

        /*Random r = new Random(System.currentTimeMillis());
        String[] strings = { "was too afraid to jump.", "chickened out.", "couldn't overcome their acrophobia.", "had a bad case of vertigo." };
        String string = strings[r.nextInt(strings.length)];

        sendMsgToAllPlayers(chatPrefix + "§3" + vp.getPlayer().getName() + " §c" + string);*/

        currentJumper = null;

        // This might be fired after the player has disconnected.
        if(vp.getPlayer().isOnline())
        {
            vp.setSpectator();
            vp.timeouts++;

            // Player has most likely gone afk.
            if(vp.timeouts >= 3)
            {
                vp.isPlaying = false;
                scoreboard.removePlayer(vp.getPlayer());

                sendMsgToAllPlayers(chatPrefix + "§3" + vp.getPlayer().getName() + " §7was disqualified for failing to make 3 jumps in time.");
            }

            vp.getPlayer().teleport(vp.getSpawnLocation());
            vp.getPlayer().playSound(vp.getPlayer().getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1, 1);
        }

        callNextJumper();
    }

    private void callNextJumper()
    {
        scoreboard.resetCurrent();
        currentJumperHasJumped = false;
        currentJumperPassedRing = false;

        boolean advance = false;

        currentJumperIndex++;

        // All players have jumped.
        if(currentJumperIndex >= jumpers.size())
        {
            advance = true;
        }
        else
        {
            VertigoPlayer vp = jumpers.get(currentJumperIndex);

            if(vp.isPlaying)
                onPlayerTurn(vp);
            else
                callNextJumper();
        }

        if(advance)
        {
            //startRoundCountdown();
            startRound();
        }
    }

    private void updateGamebar(double progress)
    {
        if(state == GameState.READY)
        {
            int count = 0;

            for(VertigoPlayer vp : players)
            {
                if(vp.isPlaying)
                    count++;
            }

            gamebar.setTitle(ChatColor.BOLD + "Vertigo" + ChatColor.WHITE + " waiting for players. " + ChatColor.GOLD + count + ChatColor.WHITE + " joined so far.");
        }
        else if(state == GameState.COUNTDOWN_TO_START)
        {
            gamebar.setTitle(ChatColor.BOLD + "Vertigo" + ChatColor.WHITE + " starting..");
        }
        else if(state == GameState.RUNNING)
        {
            String t = ChatColor.BOLD + "Vertigo ";

            if(map.currentRingCenter != null)
                t += ChatColor.GOLD + "" + ChatColor.BOLD + "◯ " + ChatColor.GOLD + "Golden ring ";

            if(currentJumper != null)
                t += "" + ChatColor.WHITE + ChatColor.BOLD + "⇨ " + ChatColor.WHITE + "Current jumper: " + ChatColor.AQUA + currentJumper.getPlayer().getName() + ChatColor.GRAY + " [" + (currentJumperIndex + 1) + "/" + jumpers.size() + "] ";

            gamebar.setTitle(t);
        }
        else if(state == GameState.ENDED)
        {
            String t = ChatColor.BOLD + "Vertigo" + ChatColor.WHITE + " - ";

            if(!winnerName.equals(""))
                t += "Winner: " + ChatColor.AQUA + winnerName;
            else
                t += "It's a draw!";

            gamebar.setTitle(t);
        }

        if(Double.isNaN(progress))
            progress = 0;

        gamebar.setProgress(progress);
        gamebar.setVisible(true);
    }

    private void sendMsgToAllPlayers(String msg)
    {
        for(VertigoPlayer vp : players)
        {
            Msg.send(vp.getPlayer(), msg);
        }
    }

    /*private void sendActionBarToAllPlayers(String msg)
    {
        for(VertigoPlayer vp : players)
        {
            vp.getPlayer().sendActionBar(msg);
        }
    }*/

    private void sendTitleToAllPlayers(String title, String subtitle)
    {
        for(VertigoPlayer vp : players)
        {
            vp.getPlayer().sendTitle(title, subtitle, -1, -1, -1);
        }
    }

    private void playSoundForAllPlayers(Sound sound)
    {
        for(VertigoPlayer vp : players)
        {
            vp.getPlayer().playSound(vp.getPlayer().getEyeLocation(), sound, SoundCategory.MASTER, 1f, 1f);
        }
    }

    private void playNoteForAllPlayers(Instrument instrument, Note note)
    {
        for(VertigoPlayer vp : players)
        {
            vp.getPlayer().playNote(vp.getPlayer().getEyeLocation(), instrument, note);
        }
    }

    private Location getSpawnLocation()
    {
        return new Location(this.world, 255, 60, 255);
    }

    VertigoPlayer findPlayer(Player player)
    {
        for(VertigoPlayer vp : players)
        {
            if(vp.getPlayer().getUniqueId().equals(player.getUniqueId()))
                return vp;
        }

        return null;
    }

    boolean hasPlayerJoined(Player player)
    {
        return findPlayer(player) != null;
    }

    private void findWinner()
    {
        List<VertigoPlayer> winners = scoreboard.getWinners(players);

        winnerName = "";

        if(winners.size() == 1)
        {
            winnerName = winners.get(0).getPlayer().getName();
            scoreboard.setPlayerWinner(winners.get(0).getPlayer());
        }
        else
        {
            String c = "";

            for(int i = 0; i < winners.size(); i++)
            {
                c += winners.get(i).getPlayer().getName();
                scoreboard.setPlayerWinner(winners.get(i).getPlayer());

                int left = winners.size() - (i + 1);

                if(left == 1)
                    c += " and ";
                else if(left > 1)
                    c += ", ";
            }

            winnerName = c;
        }
    }

    private void removeDisconnectedPlayers()
    {
        if(players.size() > 0)
        {
            List<VertigoPlayer> list = new ArrayList<>();

            for(VertigoPlayer vp : players)
            {
                Player p = vp.getPlayer();
                boolean remove = false;

                if(p == null)
                    remove = true;

                if(p != null && !p.isOnline())
                    remove = true;

                if(p != null && !p.getWorld().getName().equals(world.getName()))
                    remove = true;

                if(!remove)
                    list.add(vp);
            }

            players = list;
        }
    }
}