package io.github.feydk.vertigo;

import java.util.UUID;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Getter
public final class VertigoPlayer {
    private VertigoGame game;
    protected final UUID uuid;
    protected final String name;
    private Location spawnLocation;

    protected boolean isPlaying;
    protected boolean wasPlaying;
    protected int timeouts;
    protected int order;
    protected int score;

    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public boolean isOnline() {
        return getPlayer() != null;
    }

    public VertigoPlayer(final VertigoGame game, final Player player) {
        this.game = game;
        this.uuid = player.getUniqueId();
        this.name = player.getName();
    }

    // Set player as participant.
    public void setJumper() {
        Player player = getPlayer();
        if (player != null) player.setGameMode(GameMode.ADVENTURE);
    }

    // Set player as spectator.
    public void setSpectator() {
        Player player = getPlayer();
        if (player != null) player.setGameMode(GameMode.SPECTATOR);
        //player.setAllowFlight(true);
        //player.setFlying(true);
    }

    public Location getSpawnLocation() {
        if (spawnLocation == null) {
            spawnLocation = game.map.dealSpawnLocation();
        }
        return spawnLocation;
    }
}
