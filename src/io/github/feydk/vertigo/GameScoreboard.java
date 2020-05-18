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

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

class GameScoreboard
{
    private Objective objective = null;
    private Scoreboard board = null;
    private Team currentJumperTeam = null;
    private Team winnerTeam = null;
    private VertigoGame game;

    GameScoreboard(VertigoGame game)
    {
        this.game = game;
        init();
    }

    // Create scoreboard and objective. Set scoreboard to display in sidebar.
    private void init()
    {
        ScoreboardManager manager = this.game.loader.getServer().getScoreboardManager();

        this.board = manager.getNewScoreboard();
        this.objective = this.board.registerNewObjective("Vertigo", "dummy", ChatColor.GOLD + "=== Vertigo ===");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        currentJumperTeam = board.registerNewTeam("Current");
        currentJumperTeam.setPrefix(ChatColor.GREEN.toString() + ChatColor.BOLD.toString() + "⇨ ");
        winnerTeam = board.registerNewTeam("Winner");
        winnerTeam.setPrefix(ChatColor.GOLD.toString() + ChatColor.BOLD.toString() + "✯ ");
    }

    void reset()
    {
        //board.resetScores("");
        objective.unregister();
        this.objective = this.board.registerNewObjective("Vertigo", "dummy", ChatColor.GOLD + "=== Vertigo ===");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        for(String p : currentJumperTeam.getEntries())
            currentJumperTeam.removeEntry(p);

        for(String p : winnerTeam.getEntries())
            winnerTeam.removeEntry(p);
    }

    /*void makeLines(ArrayList<String> lines)
    {
        int empty = 0;
        int score = lines.size();
        int i = 0;

        for(String line : lines)
        {
            if(line.isEmpty())
            {
                empty++;
                String blank =  String.format("%-" + empty + "s", line);
                this.objective.getScore(blank).setScore(score);
            }
            else if(line.contains("#timer#"))
            {
                this.objective.getScore(line.replace("#timer#", getFormattedTime(2500))).setScore(score);
            }
            else
            {
                this.objective.getScore(line).setScore(score);
            }

            score--;
            i++;
        }
    }*/

    // Add a player to the scoreboard.
    void addPlayer(Player player)
    {
        player.setScoreboard(board);
    }

    // Refresh the scoreboard title.
    /*private void refreshTitle()
    {
        String title = this.title;

        objective.setDisplayName(title);
    }*/

    // Refresh the scoreboard title with a timer (which is an amount of seconds).
    /*public void refreshTitle(long ticks)
    {
        String title = this.title;

        title += " " + getFormattedTime(ticks);

        objective.setDisplayName(title);
    }*/

    void setPlayerCurrent(Player player)
    {
        resetCurrent();

        currentJumperTeam.addEntry(player.getName());
    }

    void setPlayerWinner(Player player)
    {
        winnerTeam.addEntry(player.getName());
    }

    void resetCurrent()
    {
        for(String p : currentJumperTeam.getEntries())
            currentJumperTeam.removeEntry(p);
    }

    void removePlayer(Player player)
    {
        board.resetScores(player.getName());
    }

    /*public void updatePlayers()
    {
        for(String p : board.getEntries())
        {
            Player player = game.loader.getServer().getPlayerExact(p);

            if(player == null)
                board.resetScores(player.getName());
        }
    }*/

    int getScore(Player player)
    {
        return objective.getScore(player.getName()).getScore();
    }

    boolean isSomeoneLeadingBy(int lead, List<VertigoPlayer> players)
    {
        if(players.size() > 1)
        {
            Map<VertigoPlayer, Integer> scores = new HashMap<>();

            for(VertigoPlayer vp : players)
            {
                if(vp.isPlaying)
                {
                    int score = objective.getScore(vp.getPlayer().getName()).getScore();
                    scores.put(vp, score);
                }
            }

            scores = sortByValue(scores);

            int pos1 = 0;
            int pos2 = 0;
            int i = 0;

            for(Entry<VertigoPlayer, Integer> entry : scores.entrySet())
            {
                if(i == 0)
                    pos1 = entry.getValue();
                else if(i == 1)
                    pos2 = entry.getValue();

                i++;
            }

            return pos1 - pos2 >= lead;
        }

        return false;
    }

    List<VertigoPlayer> getWinners(List<VertigoPlayer> players)
    {
        Map<VertigoPlayer, Integer> scores = new HashMap<>();

        for(VertigoPlayer vp : players)
        {
            if(vp.isPlaying)
            {
                int score = objective.getScore(vp.getPlayer().getName()).getScore();
                scores.put(vp, score);
            }
        }

        scores = sortByValue(scores);

        int max = -1;

        for(Entry<VertigoPlayer, Integer> entry : scores.entrySet())
        {
            if(entry.getValue() > max)
                max = entry.getValue();
        }

        List<VertigoPlayer> winners = new ArrayList<>();

        for(Entry<VertigoPlayer, Integer> entry : scores.entrySet())
        {
            if(entry.getValue() == max)
            {
                winners.add(entry.getKey());
            }
        }

        return winners;
    }

    // Format seconds into mm:ss.
    /*private String getFormattedTime(long ticks)
    {
        long timer = ticks / 20;
        long minutes = timer / 60;
        long seconds = timer - (minutes * 60);

        return ChatColor.WHITE + String.format("%02d", minutes) + ChatColor.GRAY + ":" + ChatColor.WHITE + String.format("%02d", seconds);
    }*/

    // Set the title of the scoreboard.
    /*public void setTitle(String title)
    {
        this.title = title;
        refreshTitle();
    }*/

    // Set player score.
    void setPlayerScore(Player player, int score)
    {
        objective.getScore(player.getName()).setScore(score);
    }

    void addPoints(Player player, int points)
    {
        Score s = objective.getScore(player.getName());
        s.setScore(s.getScore() + points);
    }

    private <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map)
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
