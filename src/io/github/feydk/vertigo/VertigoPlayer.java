package io.github.feydk.vertigo;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

class VertigoPlayer {
    private VertigoGame game;
    protected final UUID uuid;
    protected final String name;
    private Location spawnLocation;

    protected boolean isPlaying;
    protected boolean wasPlaying;
    protected int timeouts;
    protected int order;
    protected int score;

    Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    VertigoPlayer(final VertigoGame game, final Player player) {
        this.game = game;
        this.uuid = player.getUniqueId();
        this.name = player.getName();
    }

    // Set player as participant.
    void setJumper() {
        Player player = getPlayer();
        if (player != null) player.setGameMode(GameMode.ADVENTURE);
    }

    // Set player as spectator.
    void setSpectator() {
        Player player = getPlayer();
        if (player != null) player.setGameMode(GameMode.SPECTATOR);
        //player.setAllowFlight(true);
        //player.setFlying(true);
    }

    Location getSpawnLocation() {
        if(spawnLocation == null) {
            spawnLocation = game.map.dealSpawnLocation();
        }
        return spawnLocation;
    }
}
