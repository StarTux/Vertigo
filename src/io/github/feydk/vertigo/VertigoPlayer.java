package io.github.feydk.vertigo;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

class VertigoPlayer
{
    private VertigoGame game;
    private Player player;
    private Location spawnLocation;

    boolean isPlaying;
    boolean wasPlaying;
    int timeouts;

    Player getPlayer()
    {
        return player;
    }

    VertigoPlayer(VertigoGame game, Player player)
    {
        this.game = game;
        this.player = player;
    }

    // Set player as participant.
    void setJumper()
    {
        player.setGameMode(GameMode.ADVENTURE);
    }

    // Set player as spectator.
    void setSpectator()
    {
        player.setGameMode(GameMode.SPECTATOR);
        //player.setAllowFlight(true);
        //player.setFlying(true);
    }

    Location getSpawnLocation()
    {
        if(spawnLocation == null)
            spawnLocation = game.map.dealSpawnLocation();

        return spawnLocation;
    }
}