package io.github.feydk.vertigo;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.playercache.PlayerCache;
import com.winthier.creative.BuildWorld;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class VertigoAdminCommand extends AbstractCommand<VertigoLoader> {
    protected VertigoAdminCommand(final VertigoLoader plugin) {
        super(plugin, "vertigoadmin");
    }

    @Override
    protected void onEnable() {
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

    private boolean start(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        BuildWorld buildWorld = BuildWorld.findWithPath(args[0]);
        if (buildWorld == null || buildWorld.getRow().parseMinigame() != plugin.MINIGAME_TYPE) {
            throw new CommandWarn("Vertigo world not found: " + args[0]);
        }
        plugin.loadAndPlayWorld(buildWorld, false);
        return true;
    }

    private boolean test(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        BuildWorld buildWorld = BuildWorld.findWithPath(args[0]);
        if (buildWorld == null || buildWorld.getRow().parseMinigame() != plugin.MINIGAME_TYPE) {
            throw new CommandWarn("Vertigo world not found: " + args[0]);
        }
        plugin.loadAndPlayWorld(buildWorld, true);
        return true;
    }

    private void forcewin(CommandSender sender) {
        if (plugin.game == null || !plugin.mapLoaded) {
            throw new CommandWarn("There is no map loaded!");
        }
        plugin.game.end();
        sender.sendMessage(text("Game stopped", AQUA));
    }

    private void stop(CommandSender sender) {
        if (plugin.game == null || !plugin.mapLoaded) {
            throw new CommandWarn("There is no map loaded!");
        }
        if (!plugin.discardGame()) {
            throw new CommandWarn("The game world could not be unloaded");
        }
        sender.sendMessage(text("The game has been discarded", AQUA));
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
        int count = plugin.rewardHighscore();
        sender.sendMessage(text(count + " highscores rewarded", AQUA));
    }
}
