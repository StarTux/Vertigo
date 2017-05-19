package io.github.feydk.vertigo;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

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
		String sql = "update `vertigo_playerstats` set `player_name` = ? where `player_uuid` = ?";
		
		try (PreparedStatement update = MinigamesPlugin.getInstance().getDb().getConnection().prepareStatement(sql))
		{
			update.setString(1, getName());
			update.setString(2, this.uuid.toString());
			update.executeUpdate();
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
			" ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?" +
			")";
		
		try (PreparedStatement update = MinigamesPlugin.getInstance().getDb().getConnection().prepareStatement(sql))
		{
			update.setString(1, game.gameUuid.toString());
			update.setString(2, uuid.toString());
			update.setString(3, name);
			update.setTimestamp(4, new java.sql.Timestamp(startTime.getTime()));
			update.setTimestamp(5, new java.sql.Timestamp(endTime.getTime()));
			update.setInt(6, roundsPlayed);
			update.setInt(7, splats);
			update.setInt(8, splashes);
			update.setInt(9, chickens);
			update.setBoolean(10, superior);
			update.setInt(11, totalPoints);
			update.setInt(12, onePointers);
			update.setInt(13, twoPointers);
			update.setInt(14, threePointers);
			update.setInt(15, fourPointers);
			update.setInt(16, fivePointers);
			update.setInt(17, goldenRings);
			update.setBoolean(18, winner);
			update.setBoolean(19, !moreThanOnePlayed);
			update.setString(20, mapId);
			update.executeUpdate();
			
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
		reward.comment(String.format("Game of Vertigo %s with %d rounds, %d splashes, %d golden rings and %d total points.", (winner ? (superior ? "won superior" : "won") : "played"), roundsPlayed, splashes, goldenRings, totalPoints));
		for (int i = 0; i < splashes; ++i) reward.config(config.getConfigurationSection("splash"));
		for (int i = 0; i < onePointers; ++i) reward.config(config.getConfigurationSection("splash1point"));
		for (int i = 0; i < twoPointers; ++i) reward.config(config.getConfigurationSection("splash2points"));
		for (int i = 0; i < threePointers; ++i) reward.config(config.getConfigurationSection("splash3points"));
		for (int i = 0; i < fourPointers; ++i) reward.config(config.getConfigurationSection("splash4points"));
		for (int i = 0; i < fivePointers; ++i) reward.config(config.getConfigurationSection("splash5points"));
		for (int i = 0; i < goldenRings; ++i) reward.config(config.getConfigurationSection("golden_ring"));
		for (int i = 0; i < splashes; ++i) reward.config(config.getConfigurationSection("splashed" + i + "times"));
		if (splashes >= 5) {
			if (winner) reward.config(config.getConfigurationSection("win"));
			if (superior) reward.config(config.getConfigurationSection("superior"));
		}
		reward.store();
	}
    }
}
