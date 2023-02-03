package io.github.feydk.vertigo;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.font.Unicode;
import com.cavetale.core.font.VanillaItems;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.font.Glyph;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import static com.cavetale.core.font.VanillaItems.WATER_BUCKET;
import static com.cavetale.server.ServerPlugin.serverSidebar;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class VertigoLoader extends JavaPlugin implements Listener {
    protected boolean debug;
    protected VertigoGame game;
    protected static String chatPrefix = "§7[§bVertigo§7] ";
    protected boolean map_loaded;
    protected State state;
    protected int ticksWaited;
    protected BossBar gamebar;
    protected File mapFolder;
    protected List<Highscore> highscore = new ArrayList<>();
    protected List<Component> highscoreLines = new ArrayList<>();
    protected static final Component TITLE = join(noSeparators(),
                                                  WATER_BUCKET.component,
                                                  text("Vertigo", AQUA));
    protected static final Component TOURNAMENT_TITLE = join(noSeparators(),
                                                             TITLE,
                                                             VanillaItems.TROPICAL_FISH_BUCKET.component,
                                                             text("Tournament", AQUA));

    public void onEnable() {
        mapFolder = new File(getDataFolder(), "maps");
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        gamebar = getServer().createBossBar(ChatColor.BOLD + "Vertigo", BarColor.BLUE, BarStyle.SOLID);
        gamebar.setProgress(0);
        gamebar.setVisible(true);
        this.debug = true;
        this.game = new VertigoGame(this);
        getCommand("vertigo").setExecutor((a, b, c, d) -> onGameCommand(a, d));
        new VertigoAdminCommand(this).enable();
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        loadState();
        for (Player player : Bukkit.getOnlinePlayers()) {
            gamebar.addPlayer(player);
        }
        computeHighscore();
    }

    public void onDisable() {
        saveState();
        gamebar.removeAll();
        if (map_loaded) {
            discardWorld(game);
            map_loaded = false;
        }
        for(World w : getServer().getWorlds()) {
            if (w.getWorldFolder().getName().startsWith("Vertigo_temp_")) {
                boolean unloaded = getServer().unloadWorld(w, false);
                if (unloaded) {
                    deleteFiles(w.getWorldFolder());
                }
            }
        }
        serverSidebar(null);
    }

    void loadState() {
        File file = new File(getDataFolder(), "state.json");
        state = Json.load(file, State.class, State::new);
    }

    void saveState() {
        File file = new File(getDataFolder(), "state.json");
        Json.save(file, state);
    }        

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event)
    {
        if (game.world == null)
            return;

        if (event.getFrom().getName().equals(game.world.getName()))
        {
            Player player = event.getPlayer();
            game.leave(player);
        }
    }

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (game != null && game.world != null) {
            event.setSpawnLocation(game.getSpawnLocation());
        } else {
            event.setSpawnLocation(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        gamebar.addPlayer(player);
        if (game != null && game.world != null) {
            player.setGameMode(GameMode.SPECTATOR);
        } else {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        if (game.world == null)
            return;

        Player player = event.getPlayer();

        if (player.getWorld().getName().equals(game.world.getName()))
        {
            game.leave(player);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event)
    {
        if (game.world == null || game.state != VertigoGame.GameState.RUNNING)
            return;

        if (!(event.getEntity() instanceof Player))
            return;

        Player player = (Player)event.getEntity();

        if (player.getWorld().getName().equals(game.world.getName()))
        {
            event.setCancelled(true);

            // Pass it on to game.
            game.playerDamage(player, event);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event)
    {
        if (game.world == null || game.state != VertigoGame.GameState.RUNNING)
            return;

        Player player = event.getPlayer();

        if (player.getWorld().getName().equals(game.world.getName()))
        {
            game.playerMove(player, event);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event)
    {
        if (game.world == null)
            return;

        if (!(event.getEntity() instanceof Player))
            return;

        Player player = (Player)event.getEntity();

        if (player.getWorld().getName().equals(game.world.getName()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event)
    {
        if (game.world == null)
            return;

        Player player = event.getPlayer();

        if (player.getWorld().getName().equals(game.world.getName()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event)
    {
        if (game.world == null)
            return;

        if (!(event.getEntity() instanceof Player))
            return;

        Player player = (Player)event.getEntity();

        if (player.getWorld().getName().equals(game.world.getName()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event)
    {
        if (game.world == null)
            return;

        Player player = event.getPlayer();

        if (player.getWorld().getName().equals(game.world.getName()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        if (game.world == null)
            return;

        Player player = event.getPlayer();

        if (player.getWorld().getName().equals(game.world.getName()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onElytra(EntityToggleGlideEvent event)
    {
        if (game.world == null)
            return;

        if (!(event.getEntity() instanceof Player))
            return;

        Player player = (Player)event.getEntity();

        if (player.getWorld().getName().equals(game.world.getName()))
            event.setCancelled(true);
    }

    private boolean onGameCommand(CommandSender sender, String[] args)
    {
        if (args.length != 0) return false;
        if (!(sender instanceof Player)) return false;

        Player player = (Player) sender;

        if (game.state == VertigoGame.GameState.INIT)
        {
            player.sendMessage("There is no Vertigo game going on right now.");
        }
        else if (game.state == VertigoGame.GameState.READY || game.state == VertigoGame.GameState.COUNTDOWN_TO_START || game.state == VertigoGame.GameState.RUNNING || game.state == VertigoGame.GameState.ENDED)
        {
            if (!game.hasPlayerJoined(player))
            {
                game.joinPlayer(player, (game.state == VertigoGame.GameState.COUNTDOWN_TO_START || game.state == VertigoGame.GameState.RUNNING || game.state == VertigoGame.GameState.ENDED));
            }
            else
            {
                List<Object> list = new ArrayList<>();

                list.add(ChatColor.GOLD + "=== Vertigo (");
                list.add(Msg.button("" + ChatColor.YELLOW + ChatColor.UNDERLINE + "Map info" + ChatColor.GOLD + ")", ChatColor.GREEN + game.mapName + "\n" + ChatColor.WHITE + "Created by " + game.map.getCredits(), ""));
                list.add(ChatColor.GOLD + " ===\n" + ChatColor.WHITE);

                VertigoPlayer vp = game.findPlayer(player);

                if (game.state == VertigoGame.GameState.READY)
                {
                    list.add("We're waiting for players to join.\n");

                    if (vp != null && !vp.isPlaying && !vp.wasPlaying)
                        list.add("You're " + ChatColor.YELLOW + "spectating.\n" + ChatColor.WHITE);
                    else
                        list.add("You've joined the game.\n");
                }
                else if (game.state == VertigoGame.GameState.COUNTDOWN_TO_START)
                {
                    list.add("The game is starting. Get ready!\n");

                    if (vp != null && !vp.isPlaying && !vp.wasPlaying)
                        list.add("You're " + ChatColor.YELLOW + "spectating. " + ChatColor.WHITE);

                    if (vp != null && vp.isPlaying)
                        list.add("You will jump as number " + ChatColor.GREEN + vp.order + ChatColor.WHITE + " of " + ChatColor.DARK_GREEN + game.jumpers.size() + ChatColor.WHITE + " players.\n");
                    else
                        list.add("" + ChatColor.DARK_GREEN + game.jumpers.size() + ChatColor.WHITE + " players are playing.\n");
                }
                else if (game.state == VertigoGame.GameState.RUNNING)
                {
                    if (vp != null && !vp.isPlaying && !vp.wasPlaying)
                        list.add("You're " + ChatColor.YELLOW + "spectating. " + ChatColor.WHITE);
                    else
                        list.add("You've joined the game. ");

                    if (vp != null && vp.isPlaying)
                        list.add("You jump as number " + ChatColor.GREEN + vp.order + ChatColor.WHITE + " of " + ChatColor.DARK_GREEN + game.jumpers.size() + ChatColor.WHITE + " players.\n");
                    else
                        list.add("" + ChatColor.DARK_GREEN + game.jumpers.size() + ChatColor.WHITE + " players are playing.\n");
                }
                else if (game.state == VertigoGame.GameState.ENDED)
                {
                    list.add("The game is over! Final scores:\n");
                }

                for(VertigoPlayer vp_ : game.jumpers)
                {
                    if (vp_.getPlayer().getUniqueId().equals(player.getUniqueId()))
                        list.add("" + ChatColor.AQUA + vp_.getPlayer().getName() + " (" + vp_.score + ") ");
                    else
                        list.add(ChatColor.DARK_AQUA + vp_.getPlayer().getName() + " (" + vp_.score + ") ");
                }

                list.add("\n");

                for(VertigoPlayer vp_ : game.players)
                {
                    if (!vp_.isPlaying && !vp_.wasPlaying)
                        list.add(Msg.button("" + ChatColor.GRAY + "(" + vp_.getPlayer().getName() + ") ", "Spectating", ""));
                }

                Msg.sendRaw(player, list);
            }
        }

        return true;
    }

    private World loadWorld(String worldname, YamlConfiguration config) {
        WorldCreator wc = new WorldCreator(worldname);
        wc.environment(World.Environment.valueOf(config.getString("world.Environment")));
        wc.generateStructures(config.getBoolean("world.GenerateStructures"));
        wc.generator(config.getString("world.Generator"));
        wc.type(WorldType.valueOf(config.getString("world.WorldType")));
        getServer().createWorld(wc);

        World world = getServer().getWorld(worldname);
        world.setAutoSave(false);

        return world;
    }

    private void copyFileStructure(File source, File target) {
        try {
            ArrayList<String> ignore = new ArrayList<>(Arrays.asList("uid.dat", "session.lock"));
            if (!ignore.contains(source.getName())) {
                if (source.isDirectory()) {
                    if (!target.exists()) {
                        if (!target.mkdirs())
                            throw new IOException("Couldn't create world directory!");
                    }
                    String[] files = source.list();
                    for(String file : files) {
                        File srcFile = new File(source, file);
                        File destFile = new File(target, file);
                        copyFileStructure(srcFile, destFile);
                    }
                } else {
                    InputStream in = new FileInputStream(source);
                    OutputStream out = new FileOutputStream(target);
                    byte[] buffer = new byte[1024];
                    int length;
                    while((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                    in.close();
                    out.close();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteFiles(File path) {
        if (path.exists()) {
            for(File file : path.listFiles()) {
                if (file.isDirectory())
                    deleteFiles(file);
                else
                    file.delete();
            }
            path.delete();
        }
    }

    private void tick() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if ((!map_loaded || !game.isTesting()) && online.size() < 2) {
            if (map_loaded) {
                getLogger().info("Discarding world because of online players");
                discardWorld(game);
                map_loaded = false;
            }
            ticksWaited = 0;
            gamebar.setTitle(ChatColor.DARK_RED + "Waiting for players");
            gamebar.setProgress(0);
            gamebar.setColor(BarColor.RED);
            serverSidebar(null);
            return;
        }
        if (map_loaded) {
            if (game.state == VertigoGame.GameState.ENDED && game.stateTicks > 20L * 30L) {
                if (state.event || game.isTesting()) {
                    discardWorld(game);
                } else {
                    nextWorld();
                }
            } else {
                game.onTick();
                if (game.isTesting()) {
                    serverSidebar(null);
                } else if (game.state == VertigoGame.GameState.ENDED) {
                    serverSidebar(List.of(textOfChildren(WATER_BUCKET, text("/vertigo", YELLOW)),
                                          textOfChildren(WATER_BUCKET, text("Game Over", AQUA))));
                } else {
                    serverSidebar(List.of(textOfChildren(WATER_BUCKET, text("/vertigo")),
                                          textOfChildren(WATER_BUCKET, text(game.getPlayerCount() + " playing", AQUA))));
                }
            }
        } else {
            if (!state.event) {
                ticksWaited += 1;
            } else {
                ticksWaited = 0;
            }
            int ticksToWait = 20 * 30;
            if (ticksWaited >= ticksToWait) {
                nextWorld();
            } else {
                double progress = (double) ticksWaited / (double) ticksToWait;
                gamebar.setProgress(Math.max(0, Math.min(1, progress)));
                gamebar.setColor(BarColor.BLUE);
                gamebar.setTitle("Get ready...");
                serverSidebar(List.of(textOfChildren(WATER_BUCKET, text("/vertigo")),
                                      textOfChildren(WATER_BUCKET, text(game.getPlayerCount() + " waiting", AQUA))));
            }
        }
    }

    protected void nextWorld() {
        if (state.worlds.isEmpty()) {
            state.worlds = new ArrayList<>();
            state.worlds.addAll(getConfig().getStringList("maps"));
            Collections.shuffle(state.worlds);
        }
        String worldName = state.worlds.remove(state.worlds.size() - 1);
        saveState();
        loadAndPlayWorld(worldName);
    }

    protected void loadAndPlayWorld(String worldName) {
        VertigoGame oldGame = game;
        game = loadWorld(worldName);
        map_loaded = true;
        discardWorld(oldGame);
    }

    protected VertigoGame loadWorld(String name) {
        VertigoGame newGame = new VertigoGame(this);
        String worldName = "Vertigo_temp_";
        Random random = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i += 1) {
            worldName += "" + random.nextInt(10);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(mapFolder, name + "/config.yml"));

        File source = new File(mapFolder, name);
        File target = new File(getServer().getWorldContainer() + "/" + worldName);

        copyFileStructure(source, target);

        World gameWorld = loadWorld(worldName, config);

        newGame.setWorld(gameWorld, name);

        if (newGame.setup(Bukkit.getConsoleSender())) {
            newGame.ready(Bukkit.getConsoleSender());
            for (Player player : Bukkit.getOnlinePlayers()) {
                newGame.joinPlayer(player, false); // Player, isSpectator
            }
            newGame.start();
        } else {
            newGame.discard();
        }
        return newGame;
    }

    protected boolean discardWorld(VertigoGame theGame) {
        getLogger().info("Discarding world " + theGame.mapName);
        theGame.shutdown();
        if (theGame.world == null) return false;
        for (Player p : theGame.world.getPlayers()) {
            theGame.leave(p);
            p.teleport(getServer().getWorlds().get(0).getSpawnLocation());
            if (p.getGameMode() != GameMode.ADVENTURE) {
                p.setGameMode(GameMode.ADVENTURE);
            }
        }
        File dir = theGame.world.getWorldFolder();
        boolean unloaded = getServer().unloadWorld(theGame.world, false);
        if (unloaded) {
            deleteFiles(dir);
            theGame.discard();
            return true;
        } else {
            getLogger().warning("The game world could not be unloaded: " + theGame.mapName);
            return false;
        }
    }

    @EventHandler
    protected void onPlayerHud(PlayerHudEvent event) {
        List<Component> lines = new ArrayList<>();
        if (map_loaded && game != null && !game.shutdown) {
            lines.add(state.event ? TOURNAMENT_TITLE : TITLE);
            List<VertigoPlayer> players = new ArrayList<>();
            for (VertigoPlayer vp : game.players) {
                if (!vp.isPlaying) continue;
                players.add(vp);
            }
            Collections.sort(players, (a, b) -> Integer.compare(b.score, a.score));
            // Notify spectators very clearly at all times.
            VertigoPlayer vertigoPlayer = game.findPlayer(event.getPlayer());
            if (vertigoPlayer != null) {
                if (!vertigoPlayer.isPlaying && !vertigoPlayer.wasPlaying) {
                    lines.add(Component.text("You're spectating!", NamedTextColor.YELLOW));
                }
                if (vertigoPlayer.isPlaying && vertigoPlayer.order > 0) {
                    lines.add(Component.text("You jump as #" + vertigoPlayer.order, NamedTextColor.GRAY));
                }
            }
            int placement = 0;
            int lastScore = -1;
            for (VertigoPlayer vp : players) {
                if (lastScore != vp.score) {
                    lastScore = vp.score;
                    placement += 1;
                }
                Player player = vp.getPlayer();
                Component name = player != null ? player.displayName() : Component.text(vp.name);
                boolean jumping = game.currentJumper == vp;
                lines.add(join(noSeparators(),
                               (jumping ? WATER_BUCKET.component : empty()),
                               Glyph.toComponent("" + placement),
                               Component.text(Unicode.subscript(vp.score), NamedTextColor.AQUA),
                               space(),
                               name,
                               Component.text(Unicode.superscript(vp.order), NamedTextColor.DARK_GRAY)));
            }
        } else if (state.event) {
            lines.add(TOURNAMENT_TITLE);
            lines.addAll(highscoreLines);
        }
        if (lines.isEmpty()) return;
        event.sidebar(PlayerHudPriority.HIGHEST, lines);
    }

    protected void computeHighscore() {
        highscore = Highscore.of(state.scores);
        highscoreLines = Highscore.sidebar(highscore, TrophyCategory.VERTIGO);
    }

    protected int rewardHighscore() {
        return Highscore.reward(state.scores,
                                "vertigo_event",
                                TrophyCategory.VERTIGO,
                                TOURNAMENT_TITLE,
                                hi -> "You collected " + hi.score + " point" + (hi.score == 1 ? "" : "s")
                                + " at Vertigo!");
    }
}
