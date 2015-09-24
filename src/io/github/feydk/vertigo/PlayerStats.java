package io.github.feydk.vertigo;

import java.util.ArrayList;
import java.util.List;

import com.avaje.ebean.SqlQuery;
import com.avaje.ebean.SqlRow;
import com.winthier.minigames.MinigamesPlugin;

public class PlayerStats
{
	private String name;
	private int gamesPlayed;
	private int gamesWon;
	private int splats;
	private int roundsPlayed;
	private int splashes;
	private int superiorWins;
	private int points;
	private int onePointers;
	private int twoPointers;
	private int threePointers;
	private int fourPointers;
	private int fivePointers;
	private int goldenRings;
	private String mapId;
	
	public int getGamesPlayed()
	{
		return gamesPlayed;
	}
	
	public void setGamesPlayed(int games)
	{
		this.gamesPlayed = games;
	}
	
	public int getGamesWon()
	{
		return gamesWon;
	}
	
	public void setGamesWon(int won)
	{
		this.gamesWon = won;
	}

	public int getSplats()
	{
		return splats;
	}
	
	public void setSplats(int splats)
	{
		this.splats = splats;
	}

	public int getRoundsPlayed()
	{
		return roundsPlayed;
	}
	
	public void setRoundsPlayed(int rounds)
	{
		this.roundsPlayed = rounds;
	}

	public int getSplashes()
	{
		return splashes;
	}
	
	public void setSplashes(int splashes)
	{
		this.splashes = splashes;
	}

	public int getSuperiorWins()
	{
		return superiorWins;
	}
	
	public void setSuperiorWins(int wins)
	{
		this.superiorWins = wins;
	}

	public int getPoints()
	{
		return points;
	}
	
	public void setPoints(int points)
	{
		this.points = points;
	}

	public int getOnePointers()
	{
		return onePointers;
	}
	
	public void setOnePointers(int num)
	{
		this.onePointers = num;
	}

	public int getTwoPointers()
	{
		return twoPointers;
	}
	
	public void setTwoPointers(int num)
	{
		this.twoPointers = num;
	}

	public int getThreePointers()
	{
		return threePointers;
	}
	
	public void setThreePointers(int num)
	{
		this.threePointers = num;
	}

	public int getFourPointers()
	{
		return fourPointers;
	}
	
	public void setFourPointers(int num)
	{
		this.fourPointers = num;
	}

	public int getFivePointers()
	{
		return fivePointers;
	}
	
	public void setFivePointers(int num)
	{
		this.fivePointers = num;
	}
	
	public int getGoldenRings()
	{
		return goldenRings;
	}
	
	public void setGoldenRings(int num)
	{
		this.goldenRings = num;
	}
	
	public String getName()
	{
		return name;
	}
	
	public void setName(String name)
	{
		this.name = name;
	}
	
	public String getMapId()
	{
		return mapId;
	}

	public void setMapId(String mapId)
	{
		this.mapId = mapId;
	}

	public void loadOverview(String name)
	{
		String sql = 
			"select count(id) as games, sum(winner) as wins, sum(superior_win) as superior_wins, sum(splats) as splats, sum(rounds_played) as rounds_played, " + 
			"sum(splashes) as splashes, sum(points) as points, sum(one_pointers) as one_pointers, sum(two_pointers) as two_pointers, " +
			"sum(three_pointers) as three_pointers, sum(four_pointers) as four_pointers, sum(five_pointers) as five_pointers, sum(golden_rings) as golden_rings " +
			"from vertigo_playerstats " +
			"where player_name = :name";
		
		SqlQuery query = MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql);
		query.setParameter("name", name);
		
		SqlRow row = query.findUnique();
		
		gamesPlayed = row.getInteger("games");
		
		if(gamesPlayed > 0)
		{
			gamesWon = row.getInteger("wins");
			splats = row.getInteger("splats");
			roundsPlayed = row.getInteger("rounds_played");
			splashes = row.getInteger("splashes");
			superiorWins = row.getInteger("superior_wins");
			points = row.getInteger("points");
			onePointers = row.getInteger("one_pointers");
			twoPointers = row.getInteger("two_pointers");
			threePointers = row.getInteger("three_pointers");
			fourPointers = row.getInteger("four_pointers");
			fivePointers = row.getInteger("five_pointers");
			goldenRings = row.getInteger("golden_rings");
		}
	}
	
	public static List<PlayerStats> loadTopWinners()
	{
		String sql = 
			"select player_name, sum(winner) as wins, sum(superior_win) as superior_wins " +
			"from vertigo_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"having wins > 0 " +
			"order by wins desc, superior_wins desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setGamesWon(row.getInteger("wins"));
			obj.setSuperiorWins(row.getInteger("superior_wins"));
			
			list.add(obj);
		}
		
		return list;
	}
	
	public static List<PlayerStats> loadTopSplashers()
	{
		String sql = 
			"select player_name, sum(splashes) as splashes " +
			"from vertigo_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"having splashes > 0 " +
			"order by splashes desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setSplashes(row.getInteger("splashes"));
			
			list.add(obj);
		}
		
		return list;
	}
	
	public static List<PlayerStats> loadTopSplatters()
	{
		String sql = 
			"select player_name, sum(splats) as splats " +
			"from vertigo_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"having splats > 0 " +
			"order by splats desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setSplats(row.getInteger("splats"));
			
			list.add(obj);
		}
		
		return list;
	}
	
	public static List<PlayerStats> loadTopScorers()
	{
		String sql = 
			"select player_name, sum(points) as points " +
			"from vertigo_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"having points > 0 " +
			"order by points desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setPoints(row.getInteger("points"));
			
			list.add(obj);
		}
		
		return list;
	}
	
	public static List<PlayerStats> loadTopContestants()
	{
		String sql = 
			"select player_name, count(id) as games, sum(rounds_played) as rounds " +
			"from vertigo_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"order by games desc, rounds desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setGamesPlayed(row.getInteger("games"));
			obj.setRoundsPlayed(row.getInteger("rounds"));
			
			list.add(obj);
		}
		
		return list;
	}
	
	public static List<PlayerStats> loadTopRingers()
	{
		String sql = 
			"select player_name, sum(golden_rings) as golden_rings " +
			"from vertigo_playerstats " +
			"where sp_game = 0 " +
			"group by player_name " +
			"having golden_rings > 0 " +
			"order by golden_rings desc limit 0, 3";
		
		List<PlayerStats> list = new ArrayList<PlayerStats>();
		
		for(SqlRow row : MinigamesPlugin.getInstance().getDatabase().createSqlQuery(sql).findList())
		{
			PlayerStats obj = new PlayerStats();
			obj.setName(row.getString("player_name"));
			obj.setGoldenRings(row.getInteger("golden_rings"));
			
			list.add(obj);
		}
		
		return list;
	}
}