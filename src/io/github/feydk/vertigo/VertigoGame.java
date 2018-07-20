package io.github.feydk.vertigo;

import com.winthier.connect.Connect;
import com.winthier.connect.Message;
import com.winthier.connect.bukkit.event.ConnectMessageEvent;
import com.winthier.sql.SQLDatabase;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONValue;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public final class VertigoGame extends JavaPlugin implements Listener
{
    World world;
    //static String nmsver;

    // Stuff for keeping track of the game loop and ticks.
    GameState state;
    BukkitRunnable task;
    long ticks;
    long emptyTicks;
    long stateTicks;

    private GameScoreboard scoreboard;
    private GameMap map;

    static String chatPrefix = " §7[§bVertigo§7] ";

    // Config stuff.
    private int disconnectLimit;
    private int minPlayersToStart;
    private int waitForPlayersDuration;
    private int countdownToStartDuration;
    private int endDuration;
    private double ringChance;

    // Level config, sent from the framework.
    private String mapID = "Classic";
    boolean debug = false;

    // Debug stuff.
    List<String> debugStrings = new ArrayList<String>();
    boolean denyStart = false;

    private boolean didSomeoneJoin;
    private boolean moreThanOnePlayed;
    private String winnerName;

    // Stuff for keeping track of jumpers.
    private List<Player> roundJumpers = new ArrayList<Player>();
    private int currentJumperIndex = 0;
    private long jumperTicks = 0;
    private boolean currentJumperHasJumped = false;
    private Player currentJumper;
    private boolean currentJumperPassedRing = false;

    // Stuff for keeping track of rounds.
    private int currentRound = 1;
    private int roundCountdownTicks = -1;
    private Location currentRingCenter = null;
    private List<Block> currentRing = new ArrayList<Block>();

    // Highscore stuff.
    UUID gameUuid;
    String joinRandomStat;

    SQLDatabase db;
    Map<UUID, GamePlayer> gamePlayers = new HashMap<>();

    public VertigoGame()
    {
        state = GameState.INIT;
    }

    public static enum GameState
    {
        INIT,
        WAIT_FOR_PLAYERS,
        COUNTDOWN_TO_START,
        STARTED,
        END
    }

    public GameScoreboard getScoreboard()
    {
        return scoreboard;
    }

    public GameMap getMap()
    {
        return map;
    }

    @Override @SuppressWarnings("unchecked")
    public void onEnable()
    {
        db = new SQLDatabase(this);

        // Load game and world configs, then the world.  Copy and paste worthy.
        ConfigurationSection worldConfig;
        ConfigurationSection gameConfig;
        try {
            worldConfig = YamlConfiguration.loadConfiguration(new FileReader("GameWorld/config.yml"));
            gameConfig = new YamlConfiguration().createSection("tmp", (Map<String, Object>)JSONValue.parse(new FileReader("game_config.json")));
        } catch (Throwable t) {
            t.printStackTrace();
            getServer().shutdown();
            return;
        }
        mapID = gameConfig.getString("map_id", mapID);
        debug = gameConfig.getBoolean("debug", debug);
        gameUuid = UUID.fromString(gameConfig.getString("unique_id"));

        WorldCreator wc = WorldCreator.name("GameWorld");
        wc.generator("VoidGenerator");
        wc.type(WorldType.FLAT);
        try {
            wc.environment(World.Environment.valueOf(worldConfig.getString("world.Environment")));
        } catch (Throwable t) {
            wc.environment(World.Environment.NORMAL);
        }
        world = wc.createWorld();

        map = new GameMap(getConfig().getInt("general.chunkRadius"), this);

        disconnectLimit = getConfig().getInt("general.disconnectLimit");
        waitForPlayersDuration = getConfig().getInt("general.waitForPlayersDuration");
        countdownToStartDuration = getConfig().getInt("general.countdownToStartDuration");
        endDuration = getConfig().getInt("general.endDuration");

        ringChance = getConfig().getDouble("general.ringChance");

        minPlayersToStart = 1;

        System.out.println("Setting up Vertigo player stats");

        final String sql =
            "CREATE TABLE IF NOT EXISTS `vertigo_playerstats` (" +
            " `id` INT(11) NOT NULL AUTO_INCREMENT," +
            " `game_uuid` VARCHAR(40) NOT NULL," +
            " `player_uuid` VARCHAR(40) NOT NULL," +
            " `player_name` VARCHAR(16) NOT NULL," +
            " `start_time` DATETIME NOT NULL," +
            " `end_time` DATETIME NOT NULL," +
            " `rounds_played` INT(11) NOT NULL," +
            " `splats` INT(11) NOT NULL," +
            " `splashes` INT(11) NOT NULL," +
            " `chickens` INT(11) NOT NULL," +
            " `superior_win` INT(11) NOT NULL," +
            " `points` INT(11) NOT NULL," +
            " `one_pointers` INT(11) NOT NULL," +
            " `two_pointers` INT(11) NOT NULL," +
            " `three_pointers` INT(11) NOT NULL," +
            " `four_pointers` INT(11) NOT NULL," +
            " `five_pointers` INT(11) NOT NULL," +
            " `golden_rings` INT(11) NOT NULL," +
            " `winner` INT(11) NOT NULL," +
            " `sp_game` BOOLEAN NOT NULL," +
            " `map_id` VARCHAR(40) NULL, " +
            " PRIMARY KEY (`id`)" +
            ")";

        try
            {
                db.executeUpdate(sql);
            }
        catch(Exception e)
            {
                e.printStackTrace();
            }

        System.out.println("Done setting up Vertigo player stats");

        //nmsver = getServer().getClass().getPackage().getName();
        //nmsver = nmsver.substring(nmsver.lastIndexOf(".") + 1);

        world.setDifficulty(Difficulty.HARD);
        world.setPVP(false);
        world.setGameRuleValue("doTileDrops", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setWeatherDuration(Integer.MAX_VALUE);
        world.setStorm(false);

        map.process(getSpawnLocation().getChunk());

        if(map.getStartingTime() == -1)
            world.setTime(1000L);
        else
            world.setTime(map.getStartingTime());

        if(map.getLockTime())
            world.setGameRuleValue("doDaylightCycle", "false");
        else
            world.setGameRuleValue("doDaylightCycle", "true");

        task = new BukkitRunnable()
            {
                @Override public void run()
                {
                    onTick();
                }
            };

        task.runTaskTimer(this, 1, 1);
        getServer().getPluginManager().registerEvents(this, this);

        scoreboard = new GameScoreboard(this);

        //highscore.init();
        for (String ids: gameConfig.getStringList("members")) {
            UUID playerId = UUID.fromString(ids);
            getGamePlayer(playerId);
        }
        for (String ids: gameConfig.getStringList("spectators")) {
            UUID playerId = UUID.fromString(ids);
            getGamePlayer(playerId).setSpectator();
            getGamePlayer(playerId).setJoinedAsSpectator(true);
        }
    }

    @SuppressWarnings("static-access")
    private void onTick()
    {
        ticks++;

        // All players left, shut it down.
        if(gamePlayers.isEmpty())
            {
                getServer().shutdown();
                return;
            }

        if(getServer().getOnlinePlayers().isEmpty() && state == GameState.END)
            {
                getServer().shutdown();
                return;
            }

        // Check if everyone logged off during the game state.
        if(state != GameState.INIT && state != GameState.WAIT_FOR_PLAYERS)
            {
                if(getServer().getOnlinePlayers().isEmpty())
                    {
                        final long emptyTicks = this.emptyTicks++;

                        // If no one was online for 20 seconds, shut it down.
                        if(emptyTicks >= 20 * 20)
                            {
                                getServer().shutdown();
                                return;
                            }
                    }
                else
                    {
                        emptyTicks = 0L;
                    }
            }

        GameState newState = null;

        // Check for disconnects.
        for(GamePlayer gp : gamePlayers.values())
            {
                Player player = getServer().getPlayer(gp.getUuid());
                if(player == null && !gp.joinedAsSpectator())
                    {
                        // Kick players who disconnect too long.
                        long discTicks = gp.getDisconnectedTicks();

                        if(discTicks > disconnectLimit * 20)
                            {
                                scoreboard.updatePlayers();
                                getLogger().info("Kicking " + gp.getName() + " because they were disconnected too long");
                                daemonRemovePlayer(gp.getUuid());
                            }

                        gp.setDisconnectedTicks(discTicks + 1);
                    }
            }

        if(state != GameState.INIT && state != GameState.WAIT_FOR_PLAYERS && state != GameState.END)
            {
                // Check if only one player is left.
                int aliveCount = 0;

                for(Player player : getServer().getOnlinePlayers())
                    {
                        GamePlayer gp = getGamePlayer(player);

                        if(!gp.joinedAsSpectator())
                            {
                                aliveCount++;
                            }
                    }

                if(aliveCount == 0 || (aliveCount == 1 && moreThanOnePlayed))
                    {
                        newState = GameState.END;
                    }
                else
                    {
                        // At the end of each round, check if someone is 10 points ahead. If yes, end the game.
                        if(currentJumperIndex >= roundJumpers.size() && scoreboard.isSomeoneLeadingBy(10, new ArrayList<>(getServer().getOnlinePlayers())))
                            {
                                newState = GameState.END;
                            }
                    }
            }

        if(newState == null)
            newState = tickState(state);

        if(newState != null && state != newState)
            {
                onStateChange(state, newState);
                state = newState;
            }
    }

    @SuppressWarnings("incomplete-switch")
    void onStateChange(GameState oldState, GameState newState)
    {
        stateTicks = 0;

        switch(newState)
            {
            case WAIT_FOR_PLAYERS:
                scoreboard.setTitle(ChatColor.GREEN + "Waiting");
                break;
            case COUNTDOWN_TO_START:
                daemonGameConfig("players_may_join", false);
                scoreboard.setTitle(ChatColor.GREEN + "Get ready..");

                // Once the countdown starts, remove everyone who disconnected.
                for(GamePlayer gp : gamePlayers.values())
                    {
                        Player player = getServer().getPlayer(gp.getUuid());
                        if(player == null)
                            {
                                daemonRemovePlayer(gp.getUuid());
                            }

                        //GamePlayer gp = getGamePlayer(info.getUuid());
                    }

                break;
            case STARTED:
                int count = 0;

                for(Player player : getServer().getOnlinePlayers())
                    {
                        GamePlayer gp = getGamePlayer(player);

                        if(!gp.joinedAsSpectator())
                            {
                                scoreboard.setPlayerScore(player, 0);
                                //player.playSound(player.getEyeLocation(), Sound.WITHER_SPAWN, SoundCategory.MASTER, 1f, 1f);
                                count++;
                            }
                    }

                if(count > 1)
                    {
                        moreThanOnePlayed = true;
                    }
                else
                    {
                        // If it's a single player game not in debug mode, do something to make it easier to test?
                        if(!debug)
                            {
                                for(Player player : getServer().getOnlinePlayers())
                                    {
                                        GamePlayer gp = getGamePlayer(player);

                                        if(!gp.joinedAsSpectator())
                                            {
                                                // do it here
                                            }
                                    }
                            }
                    }

                scoreboard.updatePlayers();

                startRound();

                break;
            case END:
                daemonGameEnd();
                for(Player player : getServer().getOnlinePlayers())
                    {
                        getGamePlayer(player).setSpectator();

                        player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.MASTER, 1f, 1f);
                    }

                scoreboard.setTitle("Game over");
            }
    }

    private GameState tickState(GameState state)
    {
        long ticks = this.stateTicks++;

        switch(state)
            {
            case INIT:
                return tickInit(ticks);
            case WAIT_FOR_PLAYERS:
                return tickWaitForPlayers(ticks);
            case COUNTDOWN_TO_START:
                return tickCountdownToStart(ticks);
            case STARTED:
                return tickStarted(ticks);
            case END:
                return tickEnd(ticks);
            }

        return null;
    }

    GameState tickInit(long ticks)
    {
        if(!didSomeoneJoin)
            return null;

        return GameState.WAIT_FOR_PLAYERS;
    }

    GameState tickWaitForPlayers(long ticks)
    {
        // Every 5 seconds, ask players to ready (or leave).
        if(ticks % (20 * 5) == 0)
            {
                for(Player player : getServer().getOnlinePlayers())
                    {
                        GamePlayer gp = getGamePlayer(player);

                        if(!gp.isReady() && !gp.joinedAsSpectator())
                            {
                                List<Object> list = new ArrayList<>();
                                list.add(Msg.format(" &fClick here when ready: "));
                                list.add(button("&3[Ready]", "&3Mark yourself as ready", "/ready"));
                                list.add(Msg.format("&f or "));
                                list.add(button("&c[Quit]", "&cLeave this game", "/quit"));

                                Msg.sendRaw(player, list);
                            }
                    }
            }

        long timeLeft = (waitForPlayersDuration * 20) - ticks;

        // Every second, update the sidebar timer.
        if(timeLeft % 20 == 0)
            {
                scoreboard.refreshTitle(timeLeft);

                // Check if all players are ready.
                boolean allReady = true;
                int playerCount = 0;

                for(Player player : getServer().getOnlinePlayers())
                    {
                        playerCount++;
                        GamePlayer gp = getGamePlayer(player);

                        if(!gp.isReady() && !gp.joinedAsSpectator())
                            {
                                allReady = false;
                                break;
                            }
                    }

                // If they are, start the countdown (to start the game).
                if(allReady && playerCount >= minPlayersToStart)
                    return GameState.COUNTDOWN_TO_START;
            }

        // Time ran out, so we force everyone ready.
        if(timeLeft <= 0)
            {
                if(getServer().getOnlinePlayers().size() >= minPlayersToStart) {
                    return GameState.COUNTDOWN_TO_START;
                } else {
                    getServer().shutdown();
                    return null;
                }
            }

        return null;
    }

    GameState tickCountdownToStart(long ticks)
    {
        long timeLeft = (countdownToStartDuration * 20) - ticks;

        // Every second..
        if(timeLeft % 20L == 0)
            {
                long seconds = timeLeft / 20;

                scoreboard.refreshTitle(timeLeft);

                for(Player player : getServer().getOnlinePlayers())
                    {
                        if(seconds == 0)
                            {
                                Msg.sendTitle(player, "", "");
                                player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.MASTER, 1f, 1f);
                            }
                        else if(seconds == countdownToStartDuration)
                            {
                                Msg.sendTitle(player, ChatColor.GREEN + "Get ready!", ChatColor.GREEN + "Game starts in " + countdownToStartDuration + " seconds");
                                Msg.send(player, ChatColor.AQUA + " Game starts in %d seconds", seconds);
                            }
                        else
                            {
                                Msg.sendTitle(player, ChatColor.GREEN + "Get ready!", "" + ChatColor.GREEN + seconds);
                                player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int)seconds));
                            }
                    }
            }

        if(timeLeft <= 0)
            return GameState.STARTED;

        return null;
    }

    GameState tickStarted(long ticks)
    {
        if(denyStart)
            {
                for(Player player : getServer().getOnlinePlayers())
                    {
                        Msg.send(player, ChatColor.RED + " Not starting game, due to missing configuration.");
                    }

                return GameState.END;
            }

        // Check if there are still water blocks left. If not, end the game.
        if(map.getWaterLeft() <= 0)
            {
                return GameState.END;
            }

        if(currentJumper != null && !currentJumperHasJumped)
            {
                jumperTicks++;
                long total = 10 * 20;

                if(jumperTicks >= total + 20) // 1 second of forgiveness
                    {
                        onAfraidOfHeights(currentJumper);
                    }
                else
                    {
                        if(jumperTicks % 20 == 0)
                            {
                                long ticksLeft = total - jumperTicks;
                                long secsLeft = ticksLeft / 20;

                                String msg = "§3»» §f You have §3" + secsLeft + " §fsecond" + (secsLeft > 1 ? "s" : "") + " to jump §3««";

                                if(secsLeft <= 3)
                                    msg = "§3»» §4 You have §c" + secsLeft + " §4second" + (secsLeft > 1 ? "s" : "") + " to jump §3««";

                                Msg.sendActionBar(currentJumper, msg);
                            }
                    }
            }

        if(roundCountdownTicks >= 0)
            {
                long ticksTmp = roundCountdownTicks;
                roundCountdownTicks++;
                long total = 3 * 20;

                // Leave two seconds of "silence".
                if(ticksTmp >= 40 && ticksTmp % 20 == 0)
                    {
                        long secsLeft = (total - ticksTmp) / 20;

                        for(Player player : getServer().getOnlinePlayers())
                            {
                                if(secsLeft <= 0)
                                    {
                                        Msg.sendTitle(player, "", "");
                                        //player.playSound(player.getEyeLocation(), Sound.FIREWORK_LARGE_BLAST, SoundCategory.MASTER, 1f, 1f);
                                    }
                                else
                                    {
                                        Msg.sendTitle(player, "", "§aRound " + currentRound);
                                        player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int)secsLeft));
                                    }
                            }

                        if(secsLeft <= 0)
                            {
                                roundCountdownTicks = -1;
                                startRound();
                            }
                    }
            }

        return null;
    }

    GameState tickEnd(long ticks)
    {
        if(ticks == 0)
            {
                List<Player> winners = scoreboard.getWinners(new ArrayList<>(getServer().getOnlinePlayers()));

                for(Player p : winners)
                    {
                        if(p.isOnline())
                            {
                                GamePlayer gp = getGamePlayer(p.getUniqueId());

                                if(!gp.joinedAsSpectator())
                                    gp.setWinner();
                            }
                    }

                winnerName = "";

                if(winners.size() == 1)
                    {
                        winnerName = winners.get(0).getName();
                    }
                else
                    {
                        String c = "";

                        for(int i = 0; i < winners.size(); i++)
                            {
                                c += winners.get(i).getName();

                                int left = winners.size() - (i + 1);

                                if(left == 1)
                                    c += " and ";
                                else if(left > 1)
                                    c += ", ";
                            }

                        winnerName = c;
                    }

                for(Player player : getServer().getOnlinePlayers())
                    {
                        if(!debug)
                            {
                                GamePlayer gp = getGamePlayer(player);
                                gp.recordStats(moreThanOnePlayed, mapID);
                            }

                        player.setGameMode(GameMode.SPECTATOR);

                        if(winnerName != "")
                            {
                                Msg.send(player, chatPrefix + "&b%s wins the game!", winnerName);
                            }
                        else
                            {
                                Msg.send(player, chatPrefix + "&bDraw! Nobody wins.");
                            }

                        List<Object> list = new ArrayList<>();
                        list.add(" Click here to leave the game: ");
                        list.add(button("&c[Quit]", "&cLeave this game", "/quit"));
                        Msg.sendRaw(player, list);
                    }
            }

        long timeLeft = (endDuration * 20) - ticks;

        // Every second, update the sidebar timer.
        if(timeLeft % 20L == 0)
            {
                scoreboard.refreshTitle(timeLeft);
            }

        // Every 5 seconds, show/refresh the winner title announcement.
        if(timeLeft % (20 * 5) == 0)
            {
                for(Player player : getServer().getOnlinePlayers())
                    {
                        if(winnerName != "")
                            {
                                Msg.sendTitle(player, "&a" + winnerName, "&aWins the Game!");
                            }
                        else
                            {
                                Msg.sendTitle(player, "&cDraw!", "&cNobody wins");
                            }
                    }
            }

        if(timeLeft <= 0) {
            getServer().shutdown();
        }

        return null;
    }

    // Called once, when the player joins for the first time.
    public void onPlayerFirstJoin(final Player player, GamePlayer gp)
    {
        didSomeoneJoin = true;

        gp.setName(player.getName());
        gp.setStartTime(new Date());
        gp.setSpectator();
        gp.updateStatsName();

        scoreboard.addPlayer(player);

        if(debug)
            {
                if(debugStrings.size() > 0)
                    {
                        Msg.send(player, ChatColor.DARK_RED + " === DEBUG INFO ===");

                        for(String s : debugStrings)
                            {
                                Msg.send(player, " " + ChatColor.RED + s);
                            }
                    }
            }

        if(gp.joinedAsSpectator())
            return;

        switch(state)
            {
            case INIT:
            case WAIT_FOR_PLAYERS:
            case COUNTDOWN_TO_START:
                scoreboard.setPlayerScore(player, 0);
                break;
            default:
                if(gp.isJumper())
                    {
                        scoreboard.setPlayerScore(player, 0);
                    }
            }

        if(joinRandomStat == null)
            {
                joinRandomStat = getStatsJson(new Random(System.currentTimeMillis()).nextInt(5));
            }

        new BukkitRunnable()
        {
            @Override public void run()
            {
                Msg.send(player, " ");

                String credits = map.getCredits();

                if(!credits.isEmpty())
                    Msg.send(player, ChatColor.GOLD + " Welcome to Vertigo!" + ChatColor.WHITE + " Map name: " + ChatColor.AQUA + mapID + ChatColor.WHITE + " - Made by: " + ChatColor.AQUA + credits);

                Msg.send(player, " ");

                sendJsonMessage(player, joinRandomStat);
            }
        }.runTaskLater(this, 20 * 3);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    Object button(String chat, String tooltip, String command)
    {
        Map<String, Object> map = new HashMap<>();
        map.put("text", Msg.format(chat));

        Map<String, Object> map2 = new HashMap<>();
        map.put("clickEvent", map2);

        map2.put("action", "run_command");
        map2.put("value", command);

        map2 = new HashMap();
        map.put("hoverEvent", map2);
        map2.put("action", "show_text");
        map2.put("value", Msg.format(tooltip));

        return map;
    }

    // Called whenever a player joins. This could be after a player disconnect during a game, for instance.
    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        GamePlayer gp = getGamePlayer(player);

        if (!gp.hasJoinedBefore) {
            gp.hasJoinedBefore = true;
            onPlayerFirstJoin(player, gp);
        }

        gp.setDisconnectedTicks(0);

        if(gp.joinedAsSpectator())
            {
                gp.setSpectator();
                return;
            }

        scoreboard.addPlayer(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        if (state == GameState.END) {
            onPlayerLeave(event.getPlayer());
        }
    }

    public void onPlayerLeave(Player player)
    {
        GamePlayer gp = getGamePlayer(player);

        if(!gp.joinedAsSpectator())
            {
                gp.setEndTime(new Date());

                if(!debug)
                    gp.recordStats(moreThanOnePlayed, mapID);
            }
        gamePlayers.remove(gp.getUuid());
    }

    public boolean onCommand(CommandSender sender, Command bcommand, String command, String[] args)
    {
        if (!(sender instanceof Player)) return true;
        final Player player = (Player)sender;

        if(command.equalsIgnoreCase("ready") && state == GameState.WAIT_FOR_PLAYERS)
            {
                getGamePlayer(player).setReady(true);
                scoreboard.setPlayerScore(player, 1);
                Msg.send(player, ChatColor.GREEN + " Marked as ready");
            }
        else if(command.equalsIgnoreCase("tp") && getGamePlayer(player).isSpectator())
            {
                if(args.length != 1)
                    {
                        Msg.send(player, " &cUsage: /tp <player>");
                        return true;
                    }

                String arg = args[0];

                for(Player target : getServer().getOnlinePlayers())
                    {
                        if(arg.equalsIgnoreCase(target.getName()))
                            {
                                player.teleport(target);
                                Msg.send(player, " &bTeleported to %s", target.getName());
                                return true;
                            }
                    }

                Msg.send(player, " &cPlayer not found: %s", arg);
                return true;
            }
        else if(command.equalsIgnoreCase("highscore") || command.equalsIgnoreCase("hi"))
            {
                int type = 0;

                if(args.length == 1)
                    {
                        try
                            {
                                type = Integer.parseInt(args[0]);
                            }
                        catch(NumberFormatException e)
                            {}
                    }

                String json = getStatsJson(type);

                sendJsonMessage(player, json);
            }
        else if(command.equalsIgnoreCase("stats"))
            {
                showStats(player, (args.length == 0 ? player.getName() : args[0]));
            }
        else if(command.equals("jump"))
            {
                onPlayerTurn(player);
            }
        else if(command.equals("quit"))
            {
                daemonRemovePlayer(player.getUniqueId());
            }
        else
            {
                return false;
            }

        return true;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event)
    {
        if(!(event.getEntity() instanceof Player))
            return;

        Player player = (Player)event.getEntity();

        // Disregard if player is not in adv mode.
        if(player.getGameMode() != GameMode.ADVENTURE)
            {
                event.setCancelled(true);
                return;
            }

        // Disregard if player Y is above the jump threshold.
        if(player.getLocation().getY() > map.getJumpThresholdY())
            {
                event.setCancelled(true);
                //debug("Player Y above jump threshold");
                return;
            }

        // Player is below jump threshold and took fall damage. Ie he didn't land in water.
        if(event.getCause() == DamageCause.FALL)
            {
                onBrokenLegs(player, player.getLocation());
            }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event)
    {
        Player player = event.getPlayer();

        // Disregard if player is not in adv mode.
        if(player.getGameMode() != GameMode.ADVENTURE)
            return;

        // Disregard if player Y is above the jump threshold.
        if(player.getLocation().getY() > map.getJumpThresholdY())
            return;

        if(!currentJumperHasJumped)
            {
                currentJumperHasJumped = true;
                Msg.sendActionBar(player, "");
            }
        if(currentRingCenter != null && !currentJumperPassedRing)
            {
                int rx = currentRingCenter.getBlockX();
                int ry = currentRingCenter.getBlockY();
                int rz = currentRingCenter.getBlockZ();
                Location f = event.getFrom();
                Location t = event.getTo();

                if((t.getBlockX() == rx && t.getBlockZ() == rz || f.getBlockX() == rx && f.getBlockZ() == rz) && (t.getBlockY() <= ry && f.getBlockY() >= ry))
                    {
                        // world.spigot().playEffect(currentRingCenter, Effect.COLOURED_DUST, 0, 0, 2f, 2f, 2f, .2f, 2000, 50);
                        world.spawnParticle(Particle.REDSTONE, currentRingCenter, 50, 2f, 2f, 2f, .2f, new Particle.DustOptions(org.bukkit.Color.YELLOW, 10.0f));
                        currentJumperPassedRing = true;
                    }
            }

        Location l = event.getTo();

        // Check if player landed in water.
        if(l.getBlock().getType() == Material.WATER)
            {
                onSplash(player, l);
            }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event)
    {
        event.setCancelled(true);
    }

    private void callNextJumper()
    {
        scoreboard.resetCurrent();
        currentJumperHasJumped = false;
        currentJumperPassedRing = false;

        boolean advance = false;

        currentJumperIndex++;

        // All players already jumped this round.
        if(currentJumperIndex >= roundJumpers.size())
            {
                advance = true;
            }
        else
            {
                // Extra check. Players could have disconnected since the round started.
                if(roundJumpers.get(currentJumperIndex) == null || !roundJumpers.get(currentJumperIndex).isOnline())
                    {
                        callNextJumper();
                    }
                else
                    {
                        GamePlayer gp = getGamePlayer(roundJumpers.get(currentJumperIndex));

                        if(gp.joinedAsSpectator())
                            callNextJumper();
                        else
                            onPlayerTurn(roundJumpers.get(currentJumperIndex));
                    }
            }

        if(advance)
            {
                currentRound++;
                startRoundCountdown();
            }
    }

    private void startRoundCountdown()
    {
        for(Player player : getServer().getOnlinePlayers())
            {
                Msg.send(player, chatPrefix + "§fRound over. Get ready for the next round..");
            }

        roundCountdownTicks = 0;
    }

    private void removeRing()
    {
        if(currentRingCenter != null)
            {
                for(Block b : currentRing)
                    world.getBlockAt(b.getLocation()).setType(Material.AIR);

                world.getBlockAt(currentRingCenter).setType(Material.AIR);

                currentRing.clear();

                currentRingCenter = null;
            }
    }

    private void startRound()
    {
        currentJumperPassedRing = false;

        removeRing();

        // Spawn a ring?
        currentRingCenter = map.getRingLocation(ringChance);

        if(currentRingCenter != null)
            {
                currentRing.add(world.getBlockAt(currentRingCenter.clone().add(1, 0, 0)));
                currentRing.add(world.getBlockAt(currentRingCenter.clone().subtract(1, 0, 0)));
                currentRing.add(world.getBlockAt(currentRingCenter.clone().add(0, 0, 1)));
                currentRing.add(world.getBlockAt(currentRingCenter.clone().subtract(0, 0, 1)));

                for(Block b : currentRing)
                    world.getBlockAt(b.getLocation()).setType(Material.GOLD_BLOCK);

                // world.getBlockAt(currentRingCenter).setType(Material.ENDER_PORTAL);

                // new BukkitRunnable()
                // {
                //              @Override public void run()
                //      {
                //              for(final Player player : getServer().getOnlinePlayers())
                //      {
                //                      player.sendBlockChange(currentRingCenter, Material.AIR, (byte)0);
                //      }
                //      }
                // }.runTaskLater(MinigamesPlugin.getInstance(), 1);

                for(final Player player : getServer().getOnlinePlayers())
                    {
                        Msg.send(player, chatPrefix + "§fA §6golden ring §fhas appeared. Jump through it and land in water to earn an extra 3 points!");
                    }
            }

        // Distribute players randomly to mix every round up a little.
        roundJumpers.clear();

        for(Player player : getServer().getOnlinePlayers())
            {
                GamePlayer gp = getGamePlayer(player);

                if(!gp.joinedAsSpectator())
                    {
                        roundJumpers.add(player);
                        gp.addRound();
                    }
            }

        Collections.shuffle(roundJumpers, new Random(System.currentTimeMillis()));

        currentJumperIndex = 0;

        scoreboard.setTitle("Round " + currentRound);

        onPlayerTurn(roundJumpers.get(currentJumperIndex));
    }

    private void onPlayerTurn(final Player player)
    {
        scoreboard.setPlayerCurrent(player);

        GamePlayer gp = getGamePlayer(player);

        player.teleport(map.getJumpSpot());
        gp.setJumper();

        Random r = new Random(System.currentTimeMillis());
        String[] strings = { "It's your turn.", "You're up!", "You're next." };
        String[] strings2 = { "Good luck ツ", "Don't break a leg ;-)", "Geronimo!" };

        Msg.sendTitle(player, "", "§6" + strings[r.nextInt(strings.length)] + " " + strings2[r.nextInt(strings2.length)]);

        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, SoundCategory.MASTER, 1, 1);

        new BukkitRunnable()
        {
            @Override public void run()
            {
                for(Player p : getServer().getOnlinePlayers())
                    {
                        if(p != player)
                            {
                                Msg.sendTitle(p, "", "§aNext jumper: §f" + player.getName());
                            }
                    }
            }
        }.runTaskLater(this, 20);

        jumperTicks = 0;
        currentJumper = player;
    }

    private void onAfraidOfHeights(Player player)
    {
        Msg.sendActionBar(player, "");

        Random r = new Random(System.currentTimeMillis());
        String[] strings = { "was too afraid to jump.", "chickened out.", "couldn't overcome their acrophobia.", "had a bad case of vertigo." };
        String string = strings[r.nextInt(strings.length)];

        for(Player p : getServer().getOnlinePlayers())
            {
                Msg.send(p, chatPrefix + "§3" + player.getName() + " §c" + string);
            }

        currentJumper = null;

        // This might be fired after the player has disconnected.
        if(player.isOnline())
            {
                GamePlayer gp = getGamePlayer(player);
                gp.setSpectator();
                gp.addChicken();

                // Player has most likely gone afk.
                if(gp.getChickenStreak() >= 3)
                    {
                        gp.setJoinedAsSpectator(true);
                        scoreboard.removePlayer(player);

                        for(Player p : getServer().getOnlinePlayers())
                            {
                                Msg.send(p, chatPrefix + "§3" + player.getName() + " §7was disqualified because they didn't jump 3 times in a row.");
                            }
                    }

                player.teleport(gp.getSpawnLocation());
            }

        callNextJumper();
    }

    private void onBrokenLegs(Player player, Location landingLocation)
    {
        Msg.sendActionBar(player, "");

        currentJumper = null;

        // world.spigot().playEffect(landingLocation, Effect.EXPLOSION, 0, 0, .5f, .5f, .5f, .5f, 1000, 50);
        world.spawnParticle(Particle.EXPLOSION_LARGE, landingLocation, 50, .5f, .5f, .5f, .5f);

        if(player.isOnline())
            {
                GamePlayer gp = getGamePlayer(player);
                gp.setSpectator();
                gp.addSplat();

                player.teleport(gp.getSpawnLocation());
            }

        Random r = new Random(System.currentTimeMillis());
        String[] strings = { "broke their legs.", "heard their bones being shattered.", "is in serious need of medical attention.", "believed they could fly." };
        String string = strings[r.nextInt(strings.length)];

        for(Player p : getServer().getOnlinePlayers())
            {
                Msg.send(p, chatPrefix + "§cSplat! §3" + player.getName() + " §c" + string);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BIG_FALL, SoundCategory.MASTER, 1, 1);
            }

        //summonRocket(landingLocation.subtract(0, 1, 0), Color.RED);

        Msg.sendTitle(player, "", "§4Splat!");

        callNextJumper();
    }

    private void onSplash(Player player, Location landingLocation)
    {
        Msg.sendActionBar(player, "");

        currentJumper = null;

        GamePlayer gp = getGamePlayer(player);
        gp.setSpectator();
        gp.addSplash();

        int score = 1;
        Location l = landingLocation.clone();

        // Find adjacent blocks to calculate score.
        List<Block> adjacent = new ArrayList<Block>();

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
                if(map.isBlock(block.getType()))
                    {
                        adjacentBlocks++;
                        score++;
                    }
            }

        if(currentJumperPassedRing)
            {
                gp.addGoldenRing();
                score += 3;

                removeRing();
            }

        String msg = chatPrefix + "§9Splash! §3" + player.getName() + " §6+" + score + "§7";

        if(adjacentBlocks > 0)
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
            }

        for(Player p : getServer().getOnlinePlayers())
            {
                //if(currentJumperPassedRing)
                //      Msg.send(p, chatPrefix + "§9Splash! §3" + player.getName() + " §fjumped through the golden ring!");

                Msg.send(p, msg);

                if(currentJumperPassedRing)
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1, 1);
                else
                    p.playSound(p.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.MASTER, 1, 1);
            }

        Msg.sendTitle(player, "", "§9Splash! §6+ " + score + " point" + (score > 1 ? "s" : ""));

        scoreboard.addPoints(player, score);
        gp.addPoint(score);

        // Animation
        // world.spigot().playEffect(landingLocation, Effect.SPLASH, 0, 0, .5f, 4f, .5f, .1f, 5000, 50);
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
        s.setOwningPlayer(player);
        s.update();

        map.addBlock();

        player.teleport(gp.getSpawnLocation());

        callNextJumper();
    }

    GamePlayer getGamePlayer(UUID playerId) {
        GamePlayer result = gamePlayers.get(playerId);
        if (result == null) {
            result = new GamePlayer(this, playerId);
            gamePlayers.put(playerId, result);
        }
        return result;
    }

    GamePlayer getGamePlayer(Player player) {
        GamePlayer result = getGamePlayer(player.getUniqueId());
        result.setName(player.getName());
        return result;
    }

    public Location getSpawnLocation()
    {
        return new Location(world, 255, 60, 255);
    }

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (getGamePlayer(event.getPlayer().getUniqueId()).hasJoinedBefore) return;
        event.setSpawnLocation(getSpawnLocation(event.getPlayer()));
    }

    public Location getSpawnLocation(Player player)
    {
        switch (state)
            {
            case INIT:
            case WAIT_FOR_PLAYERS:
            case COUNTDOWN_TO_START:
                return getGamePlayer(player).getSpawnLocation();
            default:
                return world.getSpawnLocation();
            }
    }

    String getStatsJson(int type)
    {
        // 0: Top dog (wins, superior wins)
        // 1: Top splashers
        // 2: Top splatters
        // 3: Top scorers
        // 4: Top contestants
        // 5: Top ringers

        String json = "[";

        if(type == 0)
            {
                List<PlayerStats> list = PlayerStats.loadTopWinners(this);

                json += "{\"text\": \" §6»» §bVertigo top dogs §6««\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fwith §b" + obj.getGamesWon() + " §fwin" + (obj.getGamesWon() == 1 ? "" : "s") + "\"}, ";

                        if(obj.getSuperiorWins() > 0)
                            json += "{\"text\": \"§f, of which §b" + obj.getSuperiorWins() + " §f" + (obj.getSuperiorWins() == 1 ? "is a" : "are") + " §asuperior win" + (obj.getSuperiorWins() == 1 ? "" : "s") + "\"}, ";

                        json += "{\"text\": \"§f.\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\n\"}, ";
                    }
            }
        else if(type == 1)
            {
                List<PlayerStats> list = PlayerStats.loadTopSplashers(this);

                json += "{\"text\": \" §6»» §bVertigo top splashers §6««\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fwith §b" + obj.getSplashes() + " §fsplash" + (obj.getSplashes() == 1 ? "" : "es") + ".\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\n\"}, ";
                    }
            }
        else if(type == 2)
            {
                List<PlayerStats> list = PlayerStats.loadTopSplatters(this);

                json += "{\"text\": \" §6»» §bVertigo top broken legs §6««\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fbroke their legs §b" + obj.getSplats() + " §ftime" + (obj.getSplats() == 1 ? "" : "s") + ".\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\n\"}, ";
                    }
            }
        else if(type == 3)
            {
                List<PlayerStats> list = PlayerStats.loadTopScorers(this);

                json += "{\"text\": \" §6»» §bVertigo top scorers §6««\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fhas acquired §b" + obj.getPoints() + " §fpoint" + (obj.getPoints() == 1 ? "" : "s") + ".\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\n\"}, ";
                    }
            }
        else if(type == 4)
            {
                List<PlayerStats> list = PlayerStats.loadTopContestants(this);

                json += "{\"text\": \" §6»» §bVertigo top contestants §6««\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fhas played §b" + obj.getGamesPlayed() + " §fgame" + (obj.getGamesPlayed() == 1 ? "" : "s") + "\"}, ";

                        if(obj.getRoundsPlayed() > 0)
                            json += "{\"text\": \"§f, and a total of §b" + obj.getRoundsPlayed() + " §fround" + (obj.getRoundsPlayed() == 1 ? "" : "s") + "\"}, ";

                        json += "{\"text\": \"§f.\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\n\"}, ";
                    }
            }
        else if(type == 5)
            {
                List<PlayerStats> list = PlayerStats.loadTopRingers(this);

                json += "{\"text\": \" §6»» §bVertigo top ring jumpers §6««\n\"}, ";

                int i = 0;
                for(PlayerStats obj : list)
                    {
                        if(i == 0)
                            json += "{\"text\": \" §3Current leader: \"}, ";
                        else
                            json += "{\"text\": \" §3Runner-up: \"}, ";

                        json += "{\"text\": \"§b" + obj.getName() + " §fhas jumped §b" + obj.getGoldenRings() + " §fgolden ring" + (obj.getGoldenRings() == 1 ? "" : "s") + ".\n\"}, ";

                        i++;
                    }

                if(i == 0)
                    {
                        json += "{\"text\": \" §fNothing in this category yet. You can be the first ?\n\"}, ";
                    }
            }

        json += "{\"text\": \"\n §eOther stats:\n\"}, ";

        if(type != 0)
            json += "{\"text\": \" §f[§bWins§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 0\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who won the most games.\"}}, ";

        if(type != 3)
            json += "{\"text\": \" §f[§bScorers§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 3\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who acquired most points.\"}}, ";

        if(type != 4)
            json += "{\"text\": \" §f[§bContestants§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 4\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who played the most.\"}}, ";

        json += "{\"text\": \" \n\"}, ";

        if(type != 1)
            json += "{\"text\": \" §f[§bSplashers§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 1\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who splashed the most.\"}}, ";

        if(type != 2)
            json += "{\"text\": \" §f[§bSplatters§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 2\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who splatted the most.\"}}, ";

        if(type != 5)
            json += "{\"text\": \" §f[§bRing jumpers§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/hi 5\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee who jumped the most golden rings.\"}}, ";

        json += "{\"text\": \"\n §f[§6See your own stats§f]\", \"clickEvent\": {\"action\": \"run_command\", \"value\": \"/stats\" }, \"hoverEvent\": {\"action\": \"show_text\", \"value\": \"§fSee your personal stats.\"}}, ";

        json += "{\"text\": \" \n\"} ";
        json += "] ";

        return json;
    }

    void showStats(Player player, String name)
    {
        PlayerStats stats = new PlayerStats();
        stats.loadOverview(name, this);

        if(stats.getGamesPlayed() > 0)
            {
                String json = "[";
                json += "{\"text\": \" §3§l§m   §3 Stats for §b" + name + " §3§l§m   \n\"}, ";

                String who = player.getName().equalsIgnoreCase(name) ? "You have" : name + " has";

                json += "{\"text\": \" §f" + who + " played §b" + stats.getGamesPlayed() + " §fgame" + (stats.getGamesPlayed() == 1 ? "" : "s") + " of Vertigo.\n\"}, ";
                json += "{\"text\": \" §6Notable stats:\n\"}, ";
                json += "{\"text\": \" §b" + stats.getRoundsPlayed() + " §fround" + (stats.getRoundsPlayed() == 1 ? "" : "s") + " played §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getGamesWon() + " §fwin" + (stats.getGamesWon() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getSuperiorWins() + " §fsuperior win" + (stats.getSuperiorWins() == 1 ? "" : "s") + "\n\"}, ";
                json += "{\"text\": \" §b" + stats.getSplashes() + " §fsplash" + (stats.getSplashes() == 1 ? "" : "es") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getSplats() + " §fsplat" + (stats.getSplats() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getGoldenRings() + " §fgolden ring" + (stats.getGoldenRings() == 1 ? "" : "s") + "\n\"}, ";

                json += "{\"text\": \" §6Points acquired:\n\"}, ";
                json += "{\"text\": \" §b" + stats.getPoints() + " §ftotal point" + (stats.getPoints() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getOnePointers() + " §fone pointer" + (stats.getOnePointers() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getTwoPointers() + " §ftwo pointer" + (stats.getTwoPointers() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getThreePointers() + " §fthree pointer" + (stats.getThreePointers() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getFourPointers() + " §ffour pointer" + (stats.getFourPointers() == 1 ? "" : "s") + " §7/\"}, ";
                json += "{\"text\": \" §b" + stats.getFivePointers() + " §ffive pointer" + (stats.getFivePointers() == 1 ? "" : "s") + " \n\"}, ";

                json += "{\"text\": \" \n\"} ";
                json += "] ";

                sendJsonMessage(player, json);
            }
        else
            {
                Msg.send(player, " No stats recorded for " + name);
            }
    }

    private boolean sendJsonMessage(Player player, String json)
    {
        if(player == null)
            return false;

        final CommandSender console = getServer().getConsoleSender();
        final String command = "minecraft:tellraw " + player.getName() + " " + json;

        getServer().dispatchCommand(console, command);

        return true;
    }

    @SuppressWarnings("unused")
    private void debug(Object o)
    {
        System.out.println(o);
    }

    // Some Daemon related functions. Copy and paste worthy.

    // Request from a player to join this game.  It gets sent to us by
    // the daemon when the player enters the appropriate remote
    // command.  Tell the daemon that that the request has been
    // accepted, then wait for the daemon to send the player here.
    @EventHandler @SuppressWarnings("unchecked")
    public void onConnectMessage(ConnectMessageEvent event) {
        final Message message = event.getMessage();
        if (message.getFrom().equals("daemon") && message.getChannel().equals("minigames")) {
            Map<String, Object> payload = (Map<String, Object>)message.getPayload();
            if (payload == null) return;
            boolean join = false;
            boolean leave = false;
            boolean spectate = false;
            switch ((String)payload.get("action")) {
            case "player_join_game":
                join = true;
                spectate = false;
                break;
            case "player_spectate_game":
                join = true;
                spectate = true;
                break;
            case "player_leave_game":
                leave = true;
                break;
            default:
                return;
            }
            if (join) {
                final UUID gameId = UUID.fromString((String)payload.get("game"));
                if (!gameId.equals(gameUuid)) return;
                final UUID player = UUID.fromString((String)payload.get("player"));
                if (spectate) {
                    if (gamePlayers.containsKey(player)) return;
                    getGamePlayer(player).setSpectator();
                    getGamePlayer(player).setJoinedAsSpectator(true);
                    daemonAddSpectator(player);
                } else {
                    if (state != GameState.WAIT_FOR_PLAYERS) return;
                    if (gamePlayers.containsKey(player)) return;
                    daemonAddPlayer(player);
                }
            } else if (leave) {
                final UUID playerId = UUID.fromString((String)payload.get("player"));
                Player player = getServer().getPlayer(playerId);
                if (player != null) {
                    onPlayerLeave(player);
                    player.kickPlayer("Leaving game");
                }
            }
        }
    }

    void daemonRemovePlayer(UUID uuid) {
        gamePlayers.remove(uuid);
        Map<String, Object> map = new HashMap<>();
        map.put("action", "player_leave_game");
        map.put("player", uuid.toString());
        map.put("game", gameUuid.toString());
        Connect.getInstance().send("daemon", "minigames", map);
        Player player = getServer().getPlayer(uuid);
        if (player != null) {
            onPlayerLeave(player);
            player.kickPlayer("Leaving Game");
        }
    }

    void daemonAddPlayer(UUID uuid) {
        Map<String, Object> map = new HashMap<>();
        map.put("action", "game_add_player");
        map.put("player", uuid.toString());
        map.put("game", gameUuid.toString());
        Connect.getInstance().send("daemon", "minigames", map);
    }

    void daemonAddSpectator(UUID uuid) {
        Map<String, Object> map = new HashMap<>();
        map.put("action", "game_add_spectator");
        map.put("player", uuid.toString());
        map.put("game", gameUuid.toString());
        Connect.getInstance().send("daemon", "minigames", map);
    }

    void daemonGameEnd() {
        Map<String, Object> map = new HashMap<>();
        map.put("action", "game_end");
        map.put("game", gameUuid.toString());
        Connect.getInstance().send("daemon", "minigames", map);
    }

    void daemonGameConfig(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put("action", "game_config");
        map.put("game", gameUuid.toString());
        map.put("key", key);
        map.put("value", value);
        Connect.getInstance().send("daemon", "minigames", map);
    }

    // End of daemon stuff
}
