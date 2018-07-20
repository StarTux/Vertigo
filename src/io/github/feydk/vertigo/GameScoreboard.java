package io.github.feydk.vertigo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

public class GameScoreboard
{
    private Objective objective = null;
    private Scoreboard board = null;
    private Team currentJumperTeam = null;
    private VertigoGame game;

    private String title;

    public GameScoreboard(VertigoGame game)
    {
        this.game = game;
        init();
    }

    // Create scoreboard and objective. Set scoreboard to display in sidebar.
    public void init()
    {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        board = manager.getNewScoreboard();

        currentJumperTeam = board.registerNewTeam("Current");
        //currentJumperTeam.setPrefix(ChatColor.GREEN.toString());
        currentJumperTeam.setPrefix("* ");

        if(board.getObjective("Timer") == null)
            objective = board.registerNewObjective("Timer", "timer", "Timer");
        else
            objective = board.getObjective("Timer");

        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    // Add a player to the scoreboard.
    public void addPlayer(Player player)
    {
        player.setScoreboard(board);
    }

    // Refresh the scoreboard title.
    public void refreshTitle()
    {
        String title = this.title;

        objective.setDisplayName(title);
    }

    // Refresh the scoreboard title with a timer (which is an amount of seconds).
    public void refreshTitle(long ticks)
    {
        String title = this.title;

        title += " " + getFormattedTime(ticks);

        objective.setDisplayName(title);
    }

    public void setPlayerCurrent(Player player)
    {
        resetCurrent();

        currentJumperTeam.addEntry(player.getName());
    }

    public void resetCurrent()
    {
        for(String p : currentJumperTeam.getEntries())
            currentJumperTeam.removeEntry(p);
    }

    public void removePlayer(Player player)
    {
        board.resetScores(player.getName());
    }

    public void updatePlayers()
    {
        for(String p : board.getEntries())
            {
                Player player = game.getServer().getPlayerExact(p);
                if(player == null)
                    board.resetScores(player.getName());
            }
    }

    public boolean isSomeoneLeadingBy(int lead, List<Player> players)
    {
        if(players.size() > 1)
            {
                Map<Player, Integer> scores = new HashMap<Player, Integer>();

                for(Player player : players)
                    {
                        if(player.isOnline())
                            {
                                GamePlayer gp = game.getGamePlayer(player);

                                if(!gp.joinedAsSpectator())
                                    {
                                        int score = objective.getScore(player.getName()).getScore();
                                        scores.put(player, score);
                                    }
                            }
                    }

                scores = sortByValue(scores);

                int pos1 = 0;
                int pos2 = 0;
                int i = 0;

                for(Entry<Player, Integer> entry : scores.entrySet())
                    {
                        if(i == 0)
                            pos1 = entry.getValue();
                        else if(i == 1)
                            pos2 = entry.getValue();

                        i++;
                    }

                if(pos1 - pos2 >= lead)
                    {
                        return true;
                    }
            }

        return false;
    }

    public List<Player> getWinners(List<Player> players)
    {
        Map<Player, Integer> scores = new HashMap<Player, Integer>();

        for(Player player : players)
            {
                if(player.isOnline())
                    {
                        GamePlayer gp = game.getGamePlayer(player);

                        if(!gp.joinedAsSpectator())
                            {
                                int score = objective.getScore(player.getName()).getScore();
                                scores.put(player, score);
                            }
                    }
            }

        scores = sortByValue(scores);

        int max = -1;

        for(Entry<Player, Integer> entry : scores.entrySet())
            {
                if(entry.getValue() > max)
                    max = entry.getValue();
            }

        List<Player> winners = new ArrayList<Player>();

        for(Entry<Player, Integer> entry : scores.entrySet())
            {
                if(entry.getValue() == max)
                    {
                        //System.out.println(entry.getKey().getName() + ": " + entry.getValue());
                        winners.add(entry.getKey());
                    }
            }

        return winners;
    }

    // Format seconds into mm:ss.
    private String getFormattedTime(long ticks)
    {
        long timer = ticks / 20;
        long minutes = timer / 60;
        long seconds = timer - (minutes * 60);

        return ChatColor.WHITE + String.format("%02d", minutes) + ChatColor.GRAY + ":" + ChatColor.WHITE + String.format("%02d", seconds);
    }

    // Set the title of the scoreboard.
    public void setTitle(String title)
    {
        this.title = title;
        refreshTitle();
    }

    // Set player score.
    public void setPlayerScore(Player player, int score)
    {
        objective.getScore(player.getName()).setScore(score);
    }

    public void addPoints(Player player, int points)
    {
        Score s = objective.getScore(player.getName());
        s.setScore(s.getScore() + points);
    }

    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map)
    {
        List<Map.Entry<K, V>> list = new LinkedList<>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<K, V>>()
                         {
                             @Override
                             public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2)
                             {
                                 return (o2.getValue()).compareTo(o1.getValue());
                             }
            });

        Map<K, V> result = new LinkedHashMap<>();

        for(Map.Entry<K, V> entry : list)
            {
                result.put(entry.getKey(), entry.getValue());
            }

        return result;
    }
}
