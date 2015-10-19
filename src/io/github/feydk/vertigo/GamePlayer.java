package io.github.feydk.vertigo;

import java.util.Date;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import com.avaje.ebean.SqlUpdate;
import com.winthier.minigames.MinigamesPlugin;

import com.winthier.reward.RewardBuilder;

public class GamePlayer
{
	private final VertigoGame game;
	private final UUID uuid;
	private PlayerType type;
	private String name;
	private boolean isReady;
	private long disconnectedTics;
	private Location spawnLocation;
	private boolean statsRecorded;
	private boolean didPlay = false;
	private boolean joinedAsSpectator = false;
	private int chickenStreak = 0;
	
	// Player stats and highscore stuff.
	private boolean winner = false;
	private Date startTime;
	private Date endTime;
	private int roundsPlayed = 0;
	private int splats = 0;
	private int splashes = 0;
	private int chickens = 0;
	private boolean superior = false;
	private int onePointers = 0;
	private int twoPointers = 0;
	private int threePointers = 0;
	private int fourPointers = 0;
	private int fivePointers = 0;
	private int totalPoints = 0;
	private int goldenRings = 0;

        boolean rewarded = false;
	
	static enum PlayerType
	{
		JUMPER,
		SPECTATOR
	}
	
	public GamePlayer(VertigoGame game, UUID uuid)
	{
		this.game = game;
		this.uuid = uuid;
	}
	
	public boolean joinedAsSpectator()
	{
		return this.joinedAsSpectator;
	}
	
	public void setJoinedAsSpectator(boolean didhe)
	{
		joinedAsSpectator = didhe;
	}
	
	public boolean isJumper()
	{
		return type == PlayerType.JUMPER;
	}
	
	public boolean isSpectator()
	{
		return type == PlayerType.SPECTATOR;
	}
	
	// Set player as participant.
	public void setJumper()
	{
		type = PlayerType.JUMPER;
		
		if(game.getPlayer(uuid).isOnline())
		{
			game.getPlayer(uuid).getPlayer().setGameMode(GameMode.ADVENTURE);
			game.getPlayer(uuid).getPlayer().setAllowFlight(false);
			game.getPlayer(uuid).getPlayer().setFlying(false);
			didPlay = true;
		}
	}
	
	// Set player as spectator.
	public void setSpectator()
	{
		type = PlayerType.SPECTATOR;
		
		if(game.getPlayer(uuid).isOnline())
		{
			game.getPlayer(uuid).getPlayer().setGameMode(GameMode.SPECTATOR);
			game.getPlayer(uuid).getPlayer().setAllowFlight(true);
			game.getPlayer(uuid).getPlayer().setFlying(true);
		}
	}
	
	public void setName(String name)
	{
		this.name = name;
	}
	
	public String getName()
	{
		return name;
	}
	
	public boolean isReady()
	{
		return isReady;
	}
	
	public void setReady(boolean ready)
	{
		isReady = ready;
	}
	
	public Location getSpawnLocation()
	{
		if(spawnLocation == null)
			spawnLocation = game.getMap().dealSpawnLocation();
		
		return spawnLocation;
	}
	
	public long getDisconnectedTicks()
	{
		return disconnectedTics;
	}
	
	public void setDisconnectedTicks(long ticks)
	{
		disconnectedTics = ticks;
	}
	
	public void addRound()
	{
		roundsPlayed++;
	}
	
	public void addSplat()
	{
		splats++;
		chickenStreak = 0;
	}
	
	public void addSplash()
	{
		splashes++;
		chickenStreak = 0;
	}
	
	public void addChicken()
	{
		chickens++;
		chickenStreak++;
	}
	
	public void addGoldenRing()
	{
		goldenRings++;
	}
	
	public void addPoint(int points)
	{
		if(points == 1)
			onePointers++;
		else if(points == 2)
			twoPointers++;
		else if(points == 3)
			threePointers++;
		else if(points == 4)
			fourPointers++;
		else
			fivePointers++;
		
		totalPoints += points;
	}
	
	public void setWinner()
	{
		winner = true;
	}
	
	public void setEndTime(Date end)
	{
		endTime = end;
	}
	
	public void setStartTime(Date start)
	{
		startTime = start;
	}
	
	public int getChickenStreak()
	{
		return chickenStreak;
	}
	
	public void updateStatsName()
	{
		String sql = "update `vertigo_playerstats` set `player_name` = :name where `player_uuid` = :uuid";
		
		try
		{
			SqlUpdate update = MinigamesPlugin.getInstance().getDatabase().createSqlUpdate(sql);
			update.setParameter("name", getName());
			update.setParameter("uuid", this.uuid);
			update.execute();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("incomplete-switch")
	public void recordStats(boolean moreThanOnePlayed, String mapId)
    {
        switch(game.state)
        {
        	case INIT:
        	case WAIT_FOR_PLAYERS:
        		return;
        }
        
        if(!didPlay)
        	return;
        
        if(statsRecorded)
        	return;
        
        if(endTime == null)
			endTime = new Date();
        
        superior = splats == 0;
		
		final String sql =
			"insert into `vertigo_playerstats` (" +
			" `game_uuid`, `player_uuid`, `player_name`, `start_time`, `end_time`, `rounds_played`, `splats`, `splashes`, `chickens`, `superior_win`, `points`, `one_pointers`, `two_pointers`, `three_pointers`, `four_pointers`, `five_pointers`, `golden_rings`, `winner`, `sp_game`, `map_id`" +
			") values (" +
			" :gameUuid, :playerUuid, :playerName, :startTime, :endTime, :roundsPlayed, :splats, :splashes, :chickens, :superiorWin, :totalPoints, :onePointers, :twoPointers, :threePointers, :fourPointers, :fivePointers, :goldenRings, :winner, :spGame, :mapId" +
			")";
		
		try
		{
			SqlUpdate update = MinigamesPlugin.getInstance().getDatabase().createSqlUpdate(sql);
			update.setParameter("gameUuid", game.gameUuid);
			update.setParameter("playerUuid", uuid);
			update.setParameter("playerName", name);
			update.setParameter("startTime", startTime);
			update.setParameter("endTime", endTime);
			update.setParameter("roundsPlayed", roundsPlayed);
			update.setParameter("splats", splats);
			update.setParameter("splashes", splashes);
			update.setParameter("chickens", chickens);
			update.setParameter("superiorWin", superior);
			update.setParameter("totalPoints", totalPoints);
			update.setParameter("onePointers", onePointers);
			update.setParameter("twoPointers", twoPointers);
			update.setParameter("threePointers", threePointers);
			update.setParameter("fourPointers", fourPointers);
			update.setParameter("fivePointers", fivePointers);
			update.setParameter("goldenRings", goldenRings);
			update.setParameter("winner", winner);
			update.setParameter("spGame", !moreThanOnePlayed);
			update.setParameter("mapId", mapId);
			update.execute();
			
			game.getLogger().info("Stored player stats of " + name);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
        
        statsRecorded = true;

        if (moreThanOnePlayed && !rewarded) {
                rewarded = true;
                RewardBuilder reward = RewardBuilder.create().uuid(uuid).name(name);
                ConfigurationSection config = game.getConfigFile("rewards");
                reward.comment(String.format("Game of Vertigo %s with %d rounds, %d splashes, %d golden rings and %d total points.", (winner ? (superior ? "won" : "won superior") : "played"), roundsPlayed, splashes, goldenRings, totalPoints));
                for (int i = 0; i < splashes; ++i) reward.config(config.getConfigurationSection("splash"));
                for (int i = 0; i < onePointers; ++i) reward.config(config.getConfigurationSection("splash1point"));
                for (int i = 0; i < twoPointers; ++i) reward.config(config.getConfigurationSection("splash2points"));
                for (int i = 0; i < threePointers; ++i) reward.config(config.getConfigurationSection("splash3points"));
                for (int i = 0; i < fourPointers; ++i) reward.config(config.getConfigurationSection("splash4points"));
                for (int i = 0; i < fivePointers; ++i) reward.config(config.getConfigurationSection("splash5points"));
                for (int i = 0; i < goldenRings; ++i) reward.config(config.getConfigurationSection("golden_ring"));
                if (splashes >= 5) {
                        if (winner) reward.config(config.getConfigurationSection("win"));
                        if (superior) reward.config(config.getConfigurationSection("superior"));
                }
                reward.store();
        }
    }
}
