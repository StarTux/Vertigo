package io.github.feydk.vertigo;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.winthier.creative.BuildWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class VertigoAdminCommand extends AbstractCommand<VertigoPlugin> {
    protected VertigoAdminCommand(final VertigoPlugin plugin) {
        super(plugin, "vertigoadmin");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("list").denyTabCompletion()
            .description("List all games")
            .senderCaller(this::list);
        rootNode.addChild("here").denyTabCompletion()
            .description("Get game here")
            .playerCaller(this::here);
        rootNode.addChild("start").arguments("<map>")
            .description("Start a game")
            .completers(CommandArgCompleter.supplyList(() -> listMapPaths(true)))
            .senderCaller(this::start);
        rootNode.addChild("test").arguments("<world>")
            .description("Test a world")
            .completers(CommandArgCompleter.supplyList(() -> listMapPaths(false)))
            .senderCaller(this::test);
        rootNode.addChild("stop").denyTabCompletion()
            .description("Stop the game")
            .senderCaller(this::stop);
        rootNode.addChild("forcewin").denyTabCompletion()
            .description("Force a winner")
            .senderCaller(this::forcewin);
        rootNode.addChild("event").arguments("true|false")
            .description("Set event mode")
            .completers(CommandArgCompleter.BOOLEAN)
            .senderCaller(this::event);
        rootNode.addChild("pause").arguments("true|false")
            .description("Set pause mode")
            .completers(CommandArgCompleter.BOOLEAN)
            .senderCaller(this::pause);
        CommandNode scoreNode = rootNode.addChild("score")
            .description("Score subcommands");
        scoreNode.addChild("get").arguments("<player>")
            .description("Get score")
            .completers(PlayerCache.NAME_COMPLETER)
            .senderCaller(this::scoreGet);
        scoreNode.addChild("add").arguments("<player> <score>")
            .description("Add score")
            .completers(PlayerCache.NAME_COMPLETER,
                        CommandArgCompleter.integer(i -> i != 0))
            .senderCaller(this::scoreAdd);
        scoreNode.addChild("clear").denyTabCompletion()
            .description("Clear scores")
            .senderCaller(this::scoreClear);
        scoreNode.addChild("reward").denyTabCompletion()
            .description("Reward scores")
            .senderCaller(this::scoreReward);
    }

    private List<String> listMapPaths(boolean requireConfirmation) {
        List<String> result = new ArrayList<>();
        for (BuildWorld it : BuildWorld.findMinigameWorlds(plugin.MINIGAME_TYPE, requireConfirmation)) {
            result.add(it.getPath());
        }
        return result;
    }

    private void list(CommandSender sender) {
        sender.sendMessage(text("Total " + plugin.games.getWorldGameMap().size() + " games", YELLOW));
        for (Map.Entry<String, VertigoGame> entry : plugin.games.getWorldGameMap().entrySet()) {
            sender.sendMessage(text(entry.getKey() + ": " + entry.getValue().getPlayerCount(), AQUA));
        }
    }

    private void here(Player player) {
        final VertigoGame game = plugin.games.of(player);
        if (game == null) throw new CommandWarn("No game here!");
        player.sendMessage(text("Game here: " + game.getPath(), AQUA));
    }

    private boolean start(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        BuildWorld buildWorld = BuildWorld.findWithPath(args[0]);
        if (buildWorld == null || buildWorld.getRow().parseMinigame() != plugin.MINIGAME_TYPE) {
            throw new CommandWarn("Vertigo world not found: " + args[0]);
        }
        plugin.loadAndPlayWorld(buildWorld, game -> {
                game.setPublicGame(true);
            });
        return true;
    }

    private boolean test(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        BuildWorld buildWorld = BuildWorld.findWithPath(args[0]);
        if (buildWorld == null || buildWorld.getRow().parseMinigame() != plugin.MINIGAME_TYPE) {
            throw new CommandWarn("Vertigo world not found: " + args[0]);
        }
        plugin.loadAndPlayWorld(buildWorld, game -> {
                game.setPublicGame(true);
                game.setTesting(true);
            });
        return true;
    }

    /**
     * Find the game that the CommandSender most likely wants to
     * address with a command.
     * Either the game of the world they're in, or the public game.
     */
    private VertigoGame findBlindGame(CommandSender sender) {
        if (sender instanceof Player player) {
            VertigoGame game = plugin.games.in(player.getWorld());
            if (game != null) return game;
        }
        return plugin.games.getFirstGame();
    }

    private VertigoGame requireBlindGame(CommandSender sender) {
        final VertigoGame game = findBlindGame(sender);
        if (game == null) {
            throw new CommandWarn("There is no active game!");
        }
        return game;
    }

    private void forcewin(CommandSender sender) {
        final VertigoGame game = requireBlindGame(sender);
        game.end();
        sender.sendMessage(text("Game ended: " + game.getPath(), YELLOW));
    }

    private void stop(CommandSender sender) {
        final VertigoGame game = requireBlindGame(sender);
        plugin.games.discard(game);
        sender.sendMessage(text("Game discarded: " + game.getPath(), YELLOW));
    }

    private boolean event(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 0) {
            sender.sendMessage(text("Event mode: " + plugin.state.event, plugin.state.event ? AQUA : RED));
            return true;
        }
        final boolean value = CommandArgCompleter.requireBoolean(args[0]);
        plugin.state.event = value;
        plugin.saveState();
        sender.sendMessage(text("Event mode: " + plugin.state.event, plugin.state.event ? AQUA : RED));
        return true;
    }

    private boolean pause(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length == 0) {
            sender.sendMessage(text("Pause mode: " + plugin.state.pause, plugin.state.pause ? AQUA : RED));
            return true;
        }
        final boolean value = CommandArgCompleter.requireBoolean(args[0]);
        plugin.state.pause = value;
        plugin.saveState();
        sender.sendMessage(text("Pause mode: " + plugin.state.pause, plugin.state.pause ? AQUA : RED));
        return true;
    }

    private boolean scoreGet(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        PlayerCache target = PlayerCache.require(args[0]);
        sender.sendMessage(text("Score of " + target.name + " is " + plugin.state.getScore(target.uuid), AQUA));
        return true;
    }

    private boolean scoreAdd(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        PlayerCache target = PlayerCache.require(args[0]);
        int value = CommandArgCompleter.requireInt(args[1], i -> i != 0);
        plugin.state.addScore(target.uuid, value);
        plugin.saveState();
        plugin.computeHighscore();
        sender.sendMessage(text("Score of " + target.name + " is now " + plugin.state.getScore(target.uuid), AQUA));
        return true;
    }

    private void scoreClear(CommandSender sender) {
        plugin.state.scores.clear();
        plugin.saveState();
        plugin.computeHighscore();
        sender.sendMessage(text("Scores cleared", AQUA));
    }

    private void scoreReward(CommandSender sender) {
        final int count = Highscore.reward(
            plugin.getState().getScores(),
            "vertigo_event",
            TrophyCategory.VERTIGO,
            VertigoPlugin.TOURNAMENT_TITLE,
            hi -> "You collected " + hi.score + " point" + (hi.score == 1 ? "" : "s")
            + " at Vertigo!"
        );
        sender.sendMessage(text(count + " highscores rewarded", AQUA));
        Highscore.rewardMoneyWithFeedback(sender, plugin, plugin.getState().getScores(), "Vertigo Tournament");
    }
}
