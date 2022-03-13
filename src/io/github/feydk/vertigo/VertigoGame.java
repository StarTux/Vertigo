package io.github.feydk.vertigo;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
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

    // Game loop stuff.
    protected boolean shutdown;
    private long ticks;
    protected long stateTicks;
    private int countdownToStartDuration;
    private int endDuration;

    // Stuff for keeping track of jumpers.
    List<VertigoPlayer> jumpers = new ArrayList<>();
    private int currentJumperIndex = 0;
    private long jumperTicks = 0;
    private boolean currentJumperHasJumped;
    protected VertigoPlayer currentJumper;
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

        countdownToStartDuration = loader.getConfig().getInt("general.countdownToStartDuration");
        endDuration = loader.getConfig().getInt("general.endDuration");
    }

    void setWorld(World world, String mapName)
    {
        this.world = world;
        this.mapName = mapName;
    }

    boolean setup(CommandSender admin)
    {
        if (world == null)
            {
                admin.sendMessage(ChatColor.RED + "You must load a world first.");
                return false;
            }

        shutdown = false;
        players.clear();
        jumpers.clear();
        updateGamebar(0);

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
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.FALL_DAMAGE, true);
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setStorm(false);
        if (world.getWorldBorder().getSize() < 2048) {
            world.getWorldBorder().setSize(2048);
        }

        map = new GameMap(loader.getConfig().getInt("general.chunkRadius"), this);

        boolean ready = map.process(getSpawnLocation().getChunk());

        if (ready)
            {
                if (map.getStartingTime() == -1)
                    world.setTime(1000L);
                else
                    world.setTime(map.getStartingTime());

                if (map.getLockTime())
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

    void ready(CommandSender admin)
    {
        if (state != GameState.INIT)
            {
                admin.sendMessage(ChatColor.RED + "No can do. Game hasn't been set up yet.");
                return;
            }

        state = GameState.READY;
        updateGamebar(0);
    }

    void join(Player player, boolean spectator)
    {
        VertigoPlayer vp = findPlayer(player);

        if (vp == null)
            {
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

        player.sendTitle("Welcome to Vertigo", "", -1, -1, -1);
        if (loader.state.event) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
        }

        /*BukkitRunnable task = new BukkitRunnable()
          {
          private int count = 0;

          @Override
          public void run()
          {
          if (shutdown)
          this.cancel();

          player.sendActionBar("Welcome to Vertigo! This map was created by " + ChatColor.AQUA + map.getCredits());
          count++;

          if (count > 3)
          {
          player.sendActionBar(" ");
          this.cancel();
          }
          }
          };

          task.runTaskTimer(loader, 0, 40);*/
    }

    void leave(Player player) {
        VertigoPlayer vp = findPlayer(player);
        if (vp != null) {
            players.remove(vp);
            jumpers.remove(vp);
        }
    }

    void start() {
        stateChange(state, GameState.COUNTDOWN_TO_START);
        state = GameState.COUNTDOWN_TO_START;
    }

    void end()
    {
        /*if (this.players.size() > 0)
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
        //roundNumber = 0;
        map.reset();
        for(VertigoPlayer vp : players) {
            if (vp.wasPlaying) {
                vp.isPlaying = true;
            }
        }
    }

    void shutdown() {
        shutdown = true;
    }

    protected void onTick()
    {
        ticks++;

        if (shutdown)
            return;

        GameState newState = null;

        // Check for disconnects.
        removeDisconnectedPlayers();

        if (state == GameState.RUNNING)
            {
                // Check if only one player is left.
                int aliveCount = 0;

                for(VertigoPlayer vp : players)
                    {
                        if (vp.isPlaying)
                            aliveCount++;
                    }

                if (aliveCount == 0 || (aliveCount == 1 && moreThanOnePlayed) || map.getWaterLeft() <= 0)
                    {
                        newState = GameState.ENDED;
                    }
                else
                    {
                        // At the start of each "round", check if someone is 10 points ahead. If yes, end the game.
                        if (currentJumperIndex == 0 && isSomeoneLeadingBy(10)) {
                            newState = GameState.ENDED;
                        }
                    }

                if (newState == GameState.ENDED)
                    findWinner();
            }

        if (newState == null)
            {
                newState = tickState(state);
            }

        if (newState != null && newState != state)
            {
                stateChange(state, newState);
                state = newState;
            }
    }

    private boolean isSomeoneLeadingBy(int amount) {
        if (players.size() < 2) return false;
        List<Integer> list = new ArrayList<>(players.size());
        for (VertigoPlayer vp : players) {
            if (!vp.isPlaying) continue;
            list.add(vp.score);
        }
        Collections.sort(list);
        return list.get(list.size() - 1) - list.get(list.size() - 2) >= amount;
    }

    private GameState tickState(GameState state)
    {
        stateTicks++;

        if (state == GameState.READY)
            {
                return tickReady(stateTicks);
            }
        else if (state == GameState.COUNTDOWN_TO_START)
            {
                return tickCountdown(stateTicks);
            }
        else if (state == GameState.RUNNING)
            {
                return tickRunning(stateTicks);
            }
        else if (state == GameState.ENDED)
            {
                return tickEnded(stateTicks);
            }

        return null;
    }

    // Game is ready to accept players.
    private GameState tickReady(long ticks)
    {
        if (ticks % (20) == 0)
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

        if (timeLeft % 20L == 0)
            {
                if (seconds == 0)
                    {
                        sendTitleToAllPlayers("", "");
                        playSoundForAllPlayers(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST);
                    }
                else if (seconds <= 5)
                    {
                        sendTitleToAllPlayers(ChatColor.GREEN + "Get ready!", ChatColor.GREEN + "Game starts in " + seconds + " seconds");
                        playNoteForAllPlayers(Instrument.PIANO, new Note((int)seconds));
                    }
            }

        if (timeLeft <= 0)
            return GameState.RUNNING;

        return null;
    }

    private GameState tickRunning(long ticks)
    {
        double progress = (double)currentJumperIndex / jumpers.size();
        updateGamebar(progress);

        if (currentJumper != null && !currentJumperHasJumped) {
            jumperTicks++;

            // 10 seconds to jump.
            long total = 10 * 20;

            if (jumperTicks >= total + 20) {
                playerTimedOut(currentJumper);
                // Player didn't jump in time.
            } else {
                if (jumperTicks % 20 == 0)
                    {
                        long ticksLeft = total - jumperTicks;
                        long secsLeft = ticksLeft / 20;

                        String msg = "§f You have §3" + secsLeft + " §fsecond" + (secsLeft > 1 ? "s" : "") + " to jump";

                        if (secsLeft <= 3)
                            msg = "§4 You have §c" + secsLeft + " §4second" + (secsLeft > 1 ? "s" : "") + " to jump";

                        Player player = currentJumper.getPlayer();
                        if (player != null) player.sendActionBar(msg);
                    }
            }
        }
        /*if (roundCountdownTicks >= 0)
          {
          long ticksTmp = roundCountdownTicks;
          roundCountdownTicks++;
          long total = 3 * 20;

          // Leave two seconds of "silence".
          if (ticksTmp >= 40 && ticksTmp % 20 == 0)
          {
          long secsLeft = (total - ticksTmp) / 20;

          if (secsLeft <= 0)
          {
          sendTitleToAllPlayers("", "");
          roundCountdownTicks = -1;
          startRound();
          }
          else if (secsLeft == 3)
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
        if (timeLeft > 0 && (timeLeft % (20 * 5) == 0 || ticks == 1))
            {
                if (!winnerName.equals(""))
                    sendTitleToAllPlayers(ChatColor.AQUA + winnerName, ChatColor.WHITE + "Wins the Game!");
                else
                    sendTitleToAllPlayers(ChatColor.AQUA + "Draw!", ChatColor.WHITE + "Nobody wins");
            }

        return null;
    }

    private void stateChange(GameState oldState, GameState newState)
    {
        stateTicks = 0;

        if (newState == GameState.COUNTDOWN_TO_START)
            {
                int count = 0;

                for(VertigoPlayer vp : players)
                    {
                        if (vp.isPlaying) {
                            count++;
                        }
                    }

                moreThanOnePlayed = count > 1;

                // Make jump order.
                jumpers.clear();

                for(VertigoPlayer vp : players)
                    {
                        if (vp.isPlaying)
                            {
                                jumpers.add(vp);
                            }
                    }

                Collections.shuffle(jumpers, new Random(System.currentTimeMillis()));

                currentJumperIndex = 0;
            }
        else if (newState == GameState.RUNNING)
            {
                startRound();
            }
        else if (newState == GameState.ENDED)
            {
                if (!winnerName.equals("")) {
                    sendMsgToAllPlayers(chatPrefix + "&b" + winnerName + " wins the game!");
                    if (loader.state.event) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + winnerName + " Splash Jumper WaterBucket");
                    }
                } else {
                    sendMsgToAllPlayers( chatPrefix + "&bDraw! Nobody wins.");
                }

                playSoundForAllPlayers(Sound.UI_TOAST_CHALLENGE_COMPLETE);

                for(VertigoPlayer vp : players)
                    {
                        Player player = vp.getPlayer();
                        if (player != null) player.setGameMode(GameMode.SPECTATOR);

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

    private void startRound() {
        int order = 1;
        for(VertigoPlayer vp : jumpers) {
            vp.order = order++;
        }
        currentJumperPassedRing = false;
        map.removeRing();
        if (map.spawnRing())
            {
                playSoundForAllPlayers(Sound.BLOCK_BEACON_ACTIVATE);
                sendMsgToAllPlayers(chatPrefix + "§fA §6golden ring §fhas appeared. Jump through it to earn bonus points!");
            }
        currentJumperIndex = 0;
        onPlayerTurn(jumpers.get(currentJumperIndex));
    }

    private void onPlayerTurn(VertigoPlayer vp) {
        vp.setJumper();
        Player player = vp.getPlayer();
        if (player != null) player.teleport(map.getJumpSpot());
        Random r = new Random(System.currentTimeMillis());
        String[] strings = { "It's your turn.", "You're up!", "You're next." };
        String[] strings2 = { "Good luck ツ", "Don't break a leg!", "Geronimoooo!" };
        if (player != null) {
            player.sendTitle("", "§6" + strings[r.nextInt(strings.length)] + " " + strings2[r.nextInt(strings2.length)], -1, -1, -1);
            player.playSound(player.getEyeLocation(), Sound.BLOCK_BELL_USE, SoundCategory.MASTER, 1, 1);
        }
        jumperTicks = 0;
        currentJumper = vp;
    }

    void playerDamage(Player player, EntityDamageEvent event) {
        // Player is below jump threshold and took fall damage. Ie he didn't land in water.
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL)
            {
                playerLandedBadly(player, player.getLocation());
            }
    }

    void playerMove(Player player, PlayerMoveEvent event) {
        // Disregard if player is not in adv mode.
        if (player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }

        // Disregard if player Y is above the jump threshold.
        if (player.getLocation().getY() > map.getJumpThresholdY()) {
            return;
        }

        if (!currentJumperHasJumped)
            {
                currentJumperHasJumped = true;
                player.sendActionBar("");
            }
        if (map.currentRingCenter != null && !currentJumperPassedRing)
            {
                int rx = map.currentRingCenter.getBlockX();
                int ry = map.currentRingCenter.getBlockY();
                int rz = map.currentRingCenter.getBlockZ();
                Location f = event.getFrom();
                Location t = event.getTo();

                if ((t.getBlockX() == rx && t.getBlockZ() == rz || f.getBlockX() == rx && f.getBlockZ() == rz) && (t.getBlockY() <= ry && f.getBlockY() >= ry))
                    {
                        world.spawnParticle(Particle.REDSTONE, map.currentRingCenter, 50, 2f, 2f, 2f, .2f, new Particle.DustOptions(org.bukkit.Color.YELLOW, 10.0f));
                        currentJumperPassedRing = true;
                    }
            }

        Location l = event.getTo();

        // Check if player landed in water.
        if (l.getBlock().getType() == Material.WATER)
            {
                playerLandedInWater(player, l);
            }
    }

    private void playerLandedBadly(Player player, Location landingLocation) {
        VertigoPlayer vp = findPlayer(player);
        if (vp == null) return;

        player.sendActionBar("");

        world.spawnParticle(Particle.EXPLOSION_LARGE, landingLocation, 50, .5f, .5f, .5f, .5f);
        vp.setSpectator();
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0);
        player.teleport(vp.getSpawnLocation());
        //player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1, 1);
        String msg = "§cSplat! §3" + player.getName();
        int score = 0;
        if (currentJumperPassedRing) {
            score += 1;
            vp.score += score;
            map.removeRing();
            msg = "§cSplat! §3" + player.getName() + " §6+" + score + " point";
        }
        //Random r = new Random(System.currentTimeMillis());
        //String[] strings = { "broke their legs.", "heard their bones being shattered.", "is in serious need of medical attention.", "believed they could fly." };
        //String string = strings[r.nextInt(strings.length)];
        //sendActionBarToAllPlayers(msg);
        sendTitleToAllPlayers("", msg);
        sendMsgToAllPlayers(msg);
        playSoundForAllPlayers(Sound.ENTITY_PLAYER_BIG_FALL);
        //player.sendTitle("", "§4Splat!", -1, -1, -1);
        callNextJumper();
    }

    private void playerLandedInWater(Player player, Location landingLocation) {
        VertigoPlayer vp = findPlayer(player);
        if (vp == null) return;
        player.sendActionBar("");
        vp.setSpectator();
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0);
        player.teleport(vp.getSpawnLocation());
        //player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.MASTER, 1, 1);
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
        for(Block block : adjacent) {
            if (map.isBlock(block)) {
                adjacentBlocks++;
                score++;
            }
        }
        if (currentJumperPassedRing) {
            score += 3;
            map.removeRing();
        }
        String msg = "§9Splash! §3" + player.getName() + " §6+" + score + "§7";
        sendTitleToAllPlayers("", msg);
        //sendActionBarToAllPlayers(msg);
        if (adjacentBlocks > 0) {
            msg += " (1 + adjacent blocks: " + adjacentBlocks;
            if (currentJumperPassedRing) {
                msg += ", golden ring: 3";
            }
            msg += ")";
        } else {
            if (currentJumperPassedRing) {
                msg += " (1 + golden ring: 3)";
            }
        }
        sendMsgToAllPlayers(msg);
        if (currentJumperPassedRing) {
            playSoundForAllPlayers(Sound.ENTITY_PLAYER_LEVELUP);
        } else {
            playSoundForAllPlayers(Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED);
        }
        player.sendTitle("", "§9Splash! §6+ " + score + " point" + (score > 1 ? "s" : ""), -1, -1, -1);
        vp.score += score;
        world.spawnParticle(Particle.WATER_SPLASH, landingLocation, 50, .5f, 4f, .5f, .1f);
        ItemStack r = map.getRandomBlock();
        landingLocation.getBlock().setType(r.getType(), true);
        Block head = landingLocation.getBlock().getRelative(BlockFace.UP);
        Rotatable skullData = (Rotatable)Material.PLAYER_HEAD.createBlockData();
        int orientation = new Random(System.currentTimeMillis()).nextInt(12);
        if (orientation == 0)
            skullData.setRotation(BlockFace.EAST);
        else if (orientation == 1)
            skullData.setRotation(BlockFace.WEST);
        else if (orientation == 2)
            skullData.setRotation(BlockFace.NORTH);
        else if (orientation == 3)
            skullData.setRotation(BlockFace.SOUTH);
        else if (orientation == 4)
            skullData.setRotation(BlockFace.NORTH_EAST);
        else if (orientation == 5)
            skullData.setRotation(BlockFace.NORTH_NORTH_EAST);
        else if (orientation == 6)
            skullData.setRotation(BlockFace.NORTH_NORTH_WEST);
        else if (orientation == 7)
            skullData.setRotation(BlockFace.NORTH_WEST);
        else if (orientation == 8)
            skullData.setRotation(BlockFace.SOUTH_EAST);
        else if (orientation == 9)
            skullData.setRotation(BlockFace.SOUTH_SOUTH_EAST);
        else if (orientation == 10)
            skullData.setRotation(BlockFace.SOUTH_SOUTH_WEST);
        else if (orientation == 11)
            skullData.setRotation(BlockFace.SOUTH_WEST);
        head.setBlockData(skullData);
        Skull s = (Skull)head.getState();
        s.setPlayerProfile(player.getPlayerProfile());
        s.update();
        map.addBlock();
        map.addSkull(head);
        callNextJumper();
    }

    private void playerTimedOut(VertigoPlayer vp) {
        Player player = vp.getPlayer();
        if (player != null) player.sendActionBar("");
        Random r = new Random(System.currentTimeMillis());
        String[] strings = {
            "was too afraid to jump.",
            "chickened out.",
            "couldn't overcome their acrophobia.",
            "had a bad case of vertigo."
        };
        String string = strings[r.nextInt(strings.length)];
        sendMsgToAllPlayers(chatPrefix + "§3" + vp.name + " §c" + string);
        // This might be fired after the player has disconnected.
        vp.setSpectator();
        vp.timeouts++;
        // Player has most likely gone afk.
        if (vp.timeouts >= 3)
            {
                vp.isPlaying = false;

                sendMsgToAllPlayers(chatPrefix + "§3" + vp.name + " §7was disqualified for failing to make 3 jumps in time.");
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
            startRound();
        }
    }

    private void updateGamebar(double progress)
    {
        if (state == GameState.READY)
            {
                int count = 0;

                for(VertigoPlayer vp : players)
                    {
                        if (vp.isPlaying)
                            count++;
                    }

                loader.gamebar.setTitle(ChatColor.BOLD + "Vertigo" + ChatColor.WHITE + " waiting for players. " + ChatColor.GOLD + count + ChatColor.WHITE + " joined so far.");
            }
        else if (state == GameState.COUNTDOWN_TO_START)
            {
                loader.gamebar.setTitle(ChatColor.BOLD + "Vertigo" + ChatColor.WHITE + " starting...");
            }
        else if (state == GameState.RUNNING)
            {
                String t = "";

                if (map.currentRingCenter != null)
                    t += ChatColor.GOLD + "" + ChatColor.BOLD + "◯" + ChatColor.GOLD + "Golden ring ";

                if (currentJumper != null)
                    t += "" + ChatColor.WHITE + "Jumping: " + ChatColor.AQUA + currentJumper.name + ChatColor.GRAY + " [" + (currentJumperIndex + 1) + "/" + jumpers.size() + "] ";

                loader.gamebar.setTitle(t);
            }
        else if (state == GameState.ENDED)
            {
                String t = "";

                if (!winnerName.equals(""))
                    t += "Winner: " + ChatColor.AQUA + winnerName;
                else
                    t += "It's a draw!";

                loader.gamebar.setTitle(t);
            }

        if (Double.isNaN(progress))
            progress = 0;

        loader.gamebar.setProgress(progress);
    }

    private void sendMsgToAllPlayers(String msg)
    {
        for(VertigoPlayer vp : players) {
            Player player = vp.getPlayer();
            if (player != null) {
                Msg.send(player, msg);
            }
        }
    }

    private void sendTitleToAllPlayers(String title, String subtitle) {
        for(VertigoPlayer vp : players) {
            Player player = vp.getPlayer();
            if (player != null) {
                player.sendTitle(title, subtitle, -1, -1, -1);
            }
        }
    }

    private void playSoundForAllPlayers(Sound sound) {
        for(VertigoPlayer vp : players) {
            Player player = vp.getPlayer();
            if (player != null) {
                player.playSound(player.getEyeLocation(), sound, SoundCategory.MASTER, 1f, 1f);
            }
        }
    }

    private void playNoteForAllPlayers(Instrument instrument, Note note) {
        for(VertigoPlayer vp : players) {
            Player player = vp.getPlayer();
            if (player != null) {
                player.playNote(player.getEyeLocation(), instrument, note);
            }
        }
    }

    public Location getSpawnLocation() {
        return world.getSpawnLocation();
    }

    VertigoPlayer findPlayer(Player player) {
        for(VertigoPlayer vp : players) {
            if (vp.uuid.equals(player.getUniqueId())) {
                return vp;
            }
        }
        return null;
    }

    boolean hasPlayerJoined(Player player) {
        return findPlayer(player) != null;
    }

    List<VertigoPlayer> getWinners() {
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
        winnerName = "";
        if (winners.size() == 1) {
            winnerName = winners.get(0).name;
        } else {
            String c = "";
            for(int i = 0; i < winners.size(); i++) {
                c += winners.get(i).name;
                int left = winners.size() - (i + 1);
                if (left == 1)
                    c += " and ";
                else if (left > 1)
                    c += ", ";
            }
            winnerName = c;
        }
    }

    private void removeDisconnectedPlayers() {
        if (players.size() > 0) {
            List<VertigoPlayer> list = new ArrayList<>();
            for(VertigoPlayer vp : players) {
                Player p = vp.getPlayer();
                boolean remove = false;

                if (p == null)
                    remove = true;

                if (p != null && !p.isOnline())
                    remove = true;

                if (p != null && !p.getWorld().getName().equals(world.getName()))
                    remove = true;

                if (!remove)
                    list.add(vp);
            }

            players = list;
        }
    }
}
