package io.github.feydk.vertigo;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.ArrayList;
import java.util.List;

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

    public void loadOverview(String name, VertigoGame game)
    {
        String sql =
            "select count(id) as games, sum(winner) as wins, sum(superior_win) as superior_wins, sum(splats) as splats, sum(rounds_played) as rounds_played, " +
            "sum(splashes) as splashes, sum(points) as points, sum(one_pointers) as one_pointers, sum(two_pointers) as two_pointers, " +
            "sum(three_pointers) as three_pointers, sum(four_pointers) as four_pointers, sum(five_pointers) as five_pointers, sum(golden_rings) as golden_rings " +
            "from vertigo_playerstats " +
            "where player_name = ?";

        try(PreparedStatement query = game.db.getConnection().prepareStatement(sql))
            {
                query.setString(1, name);

                ResultSet row = query.executeQuery();
                row.next();

                gamesPlayed = row.getInt("games");

                if(gamesPlayed > 0)
                    {
                        gamesWon = row.getInt("wins");
                        splats = row.getInt("splats");
                        roundsPlayed = row.getInt("rounds_played");
                        splashes = row.getInt("splashes");
                        superiorWins = row.getInt("superior_wins");
                        points = row.getInt("points");
                        onePointers = row.getInt("one_pointers");
                        twoPointers = row.getInt("two_pointers");
                        threePointers = row.getInt("three_pointers");
                        fourPointers = row.getInt("four_pointers");
                        fivePointers = row.getInt("five_pointers");
                        goldenRings = row.getInt("golden_rings");
                    }
            }
        catch (Exception e)
            {
                e.printStackTrace();
            }
    }

    public static List<PlayerStats> loadTopWinners(VertigoGame game)
    {
        String sql =
            "select player_name, sum(winner) as wins, sum(superior_win) as superior_wins " +
            "from vertigo_playerstats " +
            "where sp_game = 0 " +
            "group by player_name " +
            "having wins > 0 " +
            "order by wins desc, superior_wins desc limit 0, 3";

        List<PlayerStats> list = new ArrayList<PlayerStats>();

        try(ResultSet row = game.db.executeQuery(sql))
            {
                while(row.next())
                    {
                        PlayerStats obj = new PlayerStats();
                        obj.setName(row.getString("player_name"));
                        obj.setGamesWon(row.getInt("wins"));
                        obj.setSuperiorWins(row.getInt("superior_wins"));

                        list.add(obj);
                    }
            }
        catch (Exception e)
            {
                e.printStackTrace();
            }

        return list;
    }

    public static List<PlayerStats> loadTopSplashers(VertigoGame game)
    {
        String sql =
            "select player_name, sum(splashes) as splashes " +
            "from vertigo_playerstats " +
            "where sp_game = 0 " +
            "group by player_name " +
            "having splashes > 0 " +
            "order by splashes desc limit 0, 3";

        List<PlayerStats> list = new ArrayList<PlayerStats>();

        try(ResultSet row = game.db.executeQuery(sql))
            {
                while(row.next())
                    {
                        PlayerStats obj = new PlayerStats();
                        obj.setName(row.getString("player_name"));
                        obj.setSplashes(row.getInt("splashes"));

                        list.add(obj);
                    }
            }
        catch (Exception e)
            {
                e.printStackTrace();
            }

        return list;
    }

    public static List<PlayerStats> loadTopSplatters(VertigoGame game)
    {
        String sql =
            "select player_name, sum(splats) as splats " +
            "from vertigo_playerstats " +
            "where sp_game = 0 " +
            "group by player_name " +
            "having splats > 0 " +
            "order by splats desc limit 0, 3";

        List<PlayerStats> list = new ArrayList<PlayerStats>();

        try(ResultSet row = game.db.executeQuery(sql))
            {
                while(row.next())
                    {
                        PlayerStats obj = new PlayerStats();
                        obj.setName(row.getString("player_name"));
                        obj.setSplats(row.getInt("splats"));

                        list.add(obj);
                    }
            }
        catch (Exception e)
            {
                e.printStackTrace();
            }

        return list;
    }

    public static List<PlayerStats> loadTopScorers(VertigoGame game)
    {
        String sql =
            "select player_name, sum(points) as points " +
            "from vertigo_playerstats " +
            "where sp_game = 0 " +
            "group by player_name " +
            "having points > 0 " +
            "order by points desc limit 0, 3";

        List<PlayerStats> list = new ArrayList<PlayerStats>();

        try(ResultSet row = game.db.executeQuery(sql))
            {
                while(row.next())
                    {
                        PlayerStats obj = new PlayerStats();
                        obj.setName(row.getString("player_name"));
                        obj.setPoints(row.getInt("points"));

                        list.add(obj);
                    }
            }
        catch (Exception e)
            {
                e.printStackTrace();
            }

        return list;
    }

    public static List<PlayerStats> loadTopContestants(VertigoGame game)
    {
        String sql =
            "select player_name, count(id) as games, sum(rounds_played) as rounds " +
            "from vertigo_playerstats " +
            "where sp_game = 0 " +
            "group by player_name " +
            "order by games desc, rounds desc limit 0, 3";

        List<PlayerStats> list = new ArrayList<PlayerStats>();

        try(ResultSet row = game.db.executeQuery(sql))
            {
                while(row.next())
                    {
                        PlayerStats obj = new PlayerStats();
                        obj.setName(row.getString("player_name"));
                        obj.setGamesPlayed(row.getInt("games"));
                        obj.setRoundsPlayed(row.getInt("rounds"));

                        list.add(obj);
                    }
            }
        catch (Exception e)
            {
                e.printStackTrace();
            }

        return list;
    }

    public static List<PlayerStats> loadTopRingers(VertigoGame game)
    {
        String sql =
            "select player_name, sum(golden_rings) as golden_rings " +
            "from vertigo_playerstats " +
            "where sp_game = 0 " +
            "group by player_name " +
            "having golden_rings > 0 " +
            "order by golden_rings desc limit 0, 3";

        List<PlayerStats> list = new ArrayList<PlayerStats>();

        try(ResultSet row = game.db.executeQuery(sql))
            {
                while(row.next())
                    {
                        PlayerStats obj = new PlayerStats();
                        obj.setName(row.getString("player_name"));
                        obj.setGoldenRings(row.getInt("golden_rings"));

                        list.add(obj);
                    }
            }
        catch (Exception e)
            {
                e.printStackTrace();
            }

        return list;
    }
}
