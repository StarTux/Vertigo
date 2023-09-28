package io.github.feydk.vertigo;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.core.font.Unicode;
import com.cavetale.core.font.VanillaItems;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.font.Glyph;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.winthier.creative.BuildWorld;
import com.winthier.creative.file.Files;
import com.winthier.creative.vote.MapVote;
import com.winthier.creative.vote.MapVoteResult;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

public final class VertigoPlugin extends JavaPlugin implements Listener {
    protected boolean debug;
    protected VertigoGame game;
    protected boolean mapLoaded;
    protected State state;
    protected BossBar bossBar;
    protected List<Highscore> highscore = new ArrayList<>();
    protected List<Component> highscoreLines = new ArrayList<>();
    protected static final Component TITLE = join(noSeparators(),
                                                  WATER_BUCKET.component,
                                                  text("Vertigo", AQUA));
    protected static final Component TOURNAMENT_TITLE = join(noSeparators(),
                                                             TITLE,
                                                             VanillaItems.TROPICAL_FISH_BUCKET.component,
                                                             text("Tournament", AQUA));
    protected static final MinigameMatchType MINIGAME_TYPE = MinigameMatchType.VERTIGO;

    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        bossBar = BossBar.bossBar(TITLE, 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        this.debug = true;
        this.game = new VertigoGame(this);
        new VertigoAdminCommand(this).enable();
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        loadState();
        computeHighscore();
    }

    public void onDisable() {
        saveState();
        discardGame();
        serverSidebar(null);
        MapVote.stop(MINIGAME_TYPE);
    }

    protected void loadState() {
        File file = new File(getDataFolder(), "state.json");
        state = Json.load(file, State.class, State::new);
    }

    protected void saveState() {
        File file = new File(getDataFolder(), "state.json");
        Json.save(file, state);
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        if (game.world == null) {
            return;
        }
        if (event.getFrom().getName().equals(game.world.getName())) {
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
        if (game != null && game.world != null) {
            player.setGameMode(GameMode.SPECTATOR);
        } else {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (game == null || game.world == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getWorld().getName().equals(game.world.getName())) {
            game.leave(player);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (game == null || game.world == null || game.state != VertigoGame.GameState.RUNNING) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (player.getWorld().getName().equals(game.world.getName())) {
            event.setCancelled(true);

            // Pass it on to game.
            game.playerDamage(player, event);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (game == null || game.world == null || game.state != VertigoGame.GameState.RUNNING) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getWorld().getName().equals(game.world.getName())) {
            game.playerMove(player, event);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (game == null || game.world == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (player.getWorld().getName().equals(game.world.getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (game == null || game.world == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getWorld().getName().equals(game.world.getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        if (game == null || game.world == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (player.getWorld().getName().equals(game.world.getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        if (game == null || game.world == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getWorld().getName().equals(game.world.getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (game == null || game.world == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getWorld().getName().equals(game.world.getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onElytra(EntityToggleGlideEvent event) {
        if (game == null || game.world == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (player.getWorld().getName().equals(game.world.getName())) {
            event.setCancelled(true);
        }
    }

    private void tick() {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if ((!mapLoaded || !game.isTesting()) && online.size() < 2) {
            MapVote.stop(MINIGAME_TYPE);
            if (mapLoaded) {
                getLogger().info("Discarding world because of online players");
                discardGame();
            }
            bossBar.name(text("Waiting for players", DARK_RED));
            bossBar.progress(0f);
            bossBar.color(BossBar.Color.RED);
            serverSidebar(null);
            return;
        }
        if (mapLoaded) {
            if (game.state == VertigoGame.GameState.ENDED && game.stateTicks > 20L * 30L) {
                discardGame();
            } else {
                game.onTick();
                if (game.isTesting()) {
                    serverSidebar(null);
                } else if (game.state == VertigoGame.GameState.ENDED) {
                    serverSidebar(List.of(textOfChildren(WATER_BUCKET, text("/vertigo", YELLOW)),
                                          textOfChildren(WATER_BUCKET, text("Game Over", AQUA))));
                } else {
                    serverSidebar(List.of(textOfChildren(WATER_BUCKET, text("/vertigo", YELLOW)),
                                          textOfChildren(WATER_BUCKET, text(game.getPlayerCount() + " playing", AQUA))));
                }
            }
        } else {
            if (state.pause) {
                serverSidebar(null);
                MapVote.stop(MINIGAME_TYPE);
            } else if (MapVote.isActive(MINIGAME_TYPE)) {
                serverSidebar(List.of(textOfChildren(WATER_BUCKET, text("/vertigo", YELLOW)),
                                      textOfChildren(WATER_BUCKET, text(online.size() + " waiting", GRAY))));
            } else {
                MapVote.start(MINIGAME_TYPE, vote -> {
                        vote.setTitle(TITLE);
                        vote.setCallback(this::onMapLoaded);
                        vote.setLobbyWorld(Bukkit.getWorlds().get(0));
                    });
            }
        }
    }

    protected void loadAndPlayWorld(BuildWorld buildWorld, boolean testing) {
        MapVote.stop(MINIGAME_TYPE);
        if (buildWorld.getRow().parseMinigame() != MINIGAME_TYPE) {
            throw new IllegalStateException("Not a Vertigo world: " + buildWorld.getName());
        }
        buildWorld.makeLocalCopyAsync(world -> {
                VertigoGame newGame = onWorldLoaded(world, buildWorld);
                newGame.setTesting(testing);
            });
    }

    protected VertigoGame onMapLoaded(MapVoteResult result) {
        return onWorldLoaded(result.getLocalWorldCopy(), result.getBuildWorldWinner());
    }

    private VertigoGame onWorldLoaded(World world, BuildWorld buildWorld) {
        discardGame();
        game = new VertigoGame(this);
        game.setWorld(world, buildWorld);
        if (game.setup(Bukkit.getConsoleSender())) {
            game.ready(Bukkit.getConsoleSender());
            for (Player player : Bukkit.getOnlinePlayers()) {
                game.joinPlayer(player, false); // Player, isSpectator
            }
            game.start();
        } else {
            game.discard();
        }
        mapLoaded = true;
        buildWorld.announceMap(world);
        return game;
    }

    protected boolean discardGame() {
        if (!mapLoaded) return false;
        if (!discardWorld(game)) return false;
        mapLoaded = false;
        game = null;
        return true;
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
        Files.deleteWorld(theGame.world);
        theGame.discard();
        if (theGame == game) {
            game = null;
            mapLoaded = false;
        }
        return true;
    }

    @EventHandler
    protected void onPlayerHud(PlayerHudEvent event) {
        if (!MapVote.isActive(MINIGAME_TYPE)) {
            event.bossbar(PlayerHudPriority.HIGH, bossBar);
        }
        List<Component> lines = new ArrayList<>();
        if (mapLoaded && game != null && !game.shutdown) {
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
