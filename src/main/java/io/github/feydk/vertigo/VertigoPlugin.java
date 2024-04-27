package io.github.feydk.vertigo;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.core.font.VanillaItems;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.winthier.creative.BuildWorld;
import com.winthier.creative.vote.MapVote;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
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
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class VertigoPlugin extends JavaPlugin implements Listener {
    private static VertigoPlugin instance;
    protected boolean debug = true;
    protected final Games games = new Games();
    protected State state;
    protected BossBar lobbyBossBar;
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

    public VertigoPlugin() {
        instance = this;
    }

    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        lobbyBossBar = BossBar.bossBar(TITLE, 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        new VertigoAdminCommand(this).enable();
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        loadState();
        computeHighscore();
        games.enable();
    }

    public void onDisable() {
        games.disable();
        saveState();
        games.disable();
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

    public World getLobbyWorld() {
        return Bukkit.getWorlds().get(0);
    }

    private void tick() {
        games.tick();
        final List<Player> online = new ArrayList<>(getLobbyWorld().getPlayers());
        final List<VertigoGame> publicGames = games.getPublicGames();
        if (publicGames.isEmpty() && online.size() < 2) {
            // Nothing is going on.  Reset everything.
            MapVote.stop(MINIGAME_TYPE);
            lobbyBossBar.name(text("Waiting for players", DARK_RED));
            lobbyBossBar.progress(0f);
            lobbyBossBar.color(BossBar.Color.RED);
            serverSidebar(null);
            return;
        }
        // Update the Server Sidebar and start or stop the minigame
        // vote.
        if (publicGames.size() > 1) {
            MapVote.stop(MINIGAME_TYPE);
            serverSidebar(List.of(textOfChildren(WATER_BUCKET, text("/vertigo", YELLOW)),
                                  textOfChildren(WATER_BUCKET, text(publicGames.size() + " groups playing", AQUA))));
        } else if (publicGames.size() == 1) {
            MapVote.stop(MINIGAME_TYPE);
            VertigoGame publicGame = publicGames.get(0);
            if (publicGame.state == VertigoGame.GameState.ENDED && publicGame.stateTicks > publicGame.getEndDuration()) {
                games.discard(publicGame);
            } else {
                switch (publicGame.state) {
                case ENDED:
                    serverSidebar(List.of(textOfChildren(WATER_BUCKET, text("/vertigo", YELLOW)),
                                          textOfChildren(WATER_BUCKET, text("Game Over", AQUA))));
                    break;
                default:
                    serverSidebar(List.of(textOfChildren(WATER_BUCKET, text("/vertigo", YELLOW)),
                                          textOfChildren(WATER_BUCKET, text(publicGame.getPlayerCount() + " playing", AQUA))));
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
                        vote.setCallback(result -> {
                                // This will be cancelled by
                                // setVoteHandler, but we're keeping
                                // it for documentation.
                                VertigoGame newGame = games.startGame(result.getLocalWorldCopy(), result.getBuildWorldWinner(), getLobbyWorld().getPlayers());
                                newGame.setPublicGame(true);
                            });
                        vote.setLobbyWorld(getLobbyWorld());
                        vote.setVoteHandler(v -> {
                                final List<Player> allPlayers = getLobbyWorld().getPlayers();
                                final List<Player> players = new ArrayList<>(allPlayers);
                                Collections.shuffle(players);
                                List<List<Player>> groups = new ArrayList<>();
                                final int desiredGroupSize = 4;
                                if (players.size() < desiredGroupSize) {
                                    groups.add(List.copyOf(players));
                                    players.clear();
                                }
                                while (players.size() >= desiredGroupSize) {
                                    List<Player> currentGroup = new ArrayList<>();
                                    groups.add(currentGroup);
                                    for (int i = 0; i < desiredGroupSize; i += 1) {
                                        currentGroup.add(players.remove(players.size() - 1));
                                    }
                                }
                                for (int i = 0; i < players.size(); i += 1) {
                                    groups.get(i % groups.size()).add(players.get(i));
                                }
                                for (List<Player> group : groups) {
                                    v.findAndLoadWinnersFor(group, result -> {
                                            List<String> names = new ArrayList<>();
                                            for (var p : group) names.add(p.getName());
                                            getLogger().info("Group game: " + result.getLocalWorldCopy()
                                                             + ", " + result.getBuildWorldWinner().getPath()
                                                             + ", " + String.join(" ", names));
                                            VertigoGame newGame = games.startGame(result.getLocalWorldCopy(), result.getBuildWorldWinner(), group);
                                            newGame.setPublicGame(true);
                                            for (var p : allPlayers) newGame.addLateJoinBlacklist(p);
                                        });
                                }
                            });
                    });
            }
        }
    }

    protected void loadAndPlayWorld(BuildWorld buildWorld, Consumer<VertigoGame> callback) {
        MapVote.stop(MINIGAME_TYPE);
        if (buildWorld.getRow().parseMinigame() != MINIGAME_TYPE) {
            throw new IllegalStateException("Not a Vertigo world: " + buildWorld.getName());
        }
        buildWorld.makeLocalCopyAsync(world -> {
                VertigoGame newGame = games.startGame(world, buildWorld, getLobbyWorld().getPlayers());
                callback.accept(newGame);
            });
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent event) {
        games.applyIn(event.getFrom(), game -> game.leave(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        final Player player = event.getPlayer();
        final VertigoGame game = games.at(event.getSpawnLocation());
        if (game != null && game.world != null) {
            event.setSpawnLocation(game.getSpawnLocation());
            return;
        }
        int min = 0;
        VertigoGame publicGame = null;
        for (var it : games.getPublicGames()) {
            if (it.findPlayer(player) == null && it.isLateJoinBlacklisted(player)) continue;
            if (publicGame == null || it.getPlayerCount() < min) {
                publicGame = it;
                min = it.getPlayerCount();
            }
        }
        if (publicGame != null) {
            event.setSpawnLocation(publicGame.getSpawnLocation());
            return;
        } else {
            event.setSpawnLocation(getLobbyWorld().getSpawnLocation());
        }
    }

    /**
     * Late joining if you're not already in a game or were
     * blacklisted during the previous vote.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final VertigoGame game = games.in(player.getWorld());
        if (game != null) {
            if (game.state != VertigoGame.GameState.ENDED && !game.isLateJoinBlacklisted(player)) {
                game.joinPlayer(player, false);
            }
        } else if (player.getWorld().equals(getLobbyWorld())) {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        games.applyEntity(player, game -> game.playerDidLogout(player));
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        games.applyEntity(player, game -> {
                event.setCancelled(true);
                if (game.state == VertigoGame.GameState.RUNNING) {
                    game.playerDamage(player, event);
                }
            });
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        games.applyEntity(player, game -> {
                if (game.state != VertigoGame.GameState.RUNNING) return;
                game.playerMove(player, event);
            });
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        final VertigoGame game = games.in(player.getWorld());
        if (game != null) event.setCancelled(true);
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        games.applyEntity(event.getPlayer(), game -> event.setCancelled(true));
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event) {
        games.applyEntity(event.getEntity(), game -> event.setCancelled(true));
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        games.applyEntity(event.getPlayer(), game -> event.setCancelled(true));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        games.applyEntity(event.getPlayer(), game -> event.setUseInteractedBlock(Event.Result.DENY));
    }

    @EventHandler
    public void onElytra(EntityToggleGlideEvent event) {
        games.applyEntity(event.getEntity(), game -> event.setCancelled(true));
    }

    @EventHandler
    protected void onPlayerHud(PlayerHudEvent event) {
        final Player player = event.getPlayer();
        final List<Component> lines = new ArrayList<>();
        VertigoGame game = games.in(player.getWorld());
        if (game != null && !game.shutdown) {
            lines.add(state.event ? TOURNAMENT_TITLE : TITLE);
            game.makeSidebar(event.getPlayer(), lines);
            event.bossbar(PlayerHudPriority.HIGH, game.bossBar);
        } else if (state.event) {
            lines.add(TOURNAMENT_TITLE);
            lines.addAll(highscoreLines);
            event.bossbar(PlayerHudPriority.HIGH, lobbyBossBar);
        } else if (!MapVote.isActive(MINIGAME_TYPE) || !player.getWorld().equals(getLobbyWorld())) {
            event.bossbar(PlayerHudPriority.HIGH, lobbyBossBar);
        }
        if (!lines.isEmpty()) event.sidebar(PlayerHudPriority.HIGHEST, lines);
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

    public static VertigoPlugin plugin() {
        return instance;
    }
}
