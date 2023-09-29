package io.github.feydk.vertigo;

import com.winthier.creative.BuildWorld;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import static io.github.feydk.vertigo.VertigoPlugin.plugin;

/**
 * Manager for all games.  Creation and destruction happens here.
 */
public final class Games {
    @Getter private final Map<String, VertigoGame> worldGameMap = new HashMap<>();

    protected void enable() { }

    protected void disable() {
        for (VertigoGame it : worldGameMap.values()) {
            it.discard();
        }
        worldGameMap.clear();
    }

    public VertigoGame getPublicGame() {
        for (VertigoGame it : worldGameMap.values()) {
            if (it.isPublicGame()) return it;
        }
        return null;
    }

    public VertigoGame getFirstGame() {
        if (worldGameMap.isEmpty()) return null;
        return worldGameMap.values().iterator().next();
    }

    public boolean hasPublicGame() {
        return getPublicGame() != null;
    }

    public void discardPublicGame() {
        VertigoGame game = getPublicGame();
        if (game != null) discard(game);
    }

    public VertigoGame in(World world) {
        return worldGameMap.get(world.getName());
    }

    public VertigoGame at(Location location) {
        return worldGameMap.get(location.getWorld().getName());
    }

    public VertigoGame of(Entity entity) {
        return worldGameMap.get(entity.getWorld().getName());
    }

    public void applyIn(World world, Consumer<VertigoGame> callback) {
        final VertigoGame game = in(world);
        if (game != null) callback.accept(game);
    }

    public void applyAt(Location location, Consumer<VertigoGame> callback) {
        final VertigoGame game = at(location);
        if (game != null) callback.accept(game);
    }

    public void applyEntity(Entity entity, Consumer<VertigoGame> callback) {
        final VertigoGame game = of(entity);
        if (game != null) callback.accept(game);
    }

    public VertigoGame startGame(World world, BuildWorld buildWorld) {
        VertigoGame game = new VertigoGame(plugin(), world, buildWorld);
        if (game.setup(Bukkit.getConsoleSender())) {
            game.ready(Bukkit.getConsoleSender());
            for (Player player : plugin().getLobbyWorld().getPlayers()) {
                game.joinPlayer(player, false); // Player, isSpectator
            }
            game.start();
        } else {
            game.discard();
        }
        worldGameMap.put(game.getPath(), game);
        buildWorld.announceMap(world);
        return game;
    }

    public void discard(VertigoGame game) {
        game.discard();
        worldGameMap.remove(game.getPath());
    }

    /**
     * Called by plugin.
     */
    protected void tick() {
        for (VertigoGame game : List.copyOf(worldGameMap.values())) {
            if (game.state == VertigoGame.GameState.ENDED && game.stateTicks > 20L * 30L) {
                discard(game);
            } else {
                game.onTick();
            }
        }
    }

    public static Games games() {
        return plugin().games;
    }
}
