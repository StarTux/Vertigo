package io.github.feydk.vertigo;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.winthier.playercache.PlayerCache;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class VertigoAdminCommand extends AbstractCommand<VertigoLoader> {
    protected VertigoAdminCommand(final VertigoLoader plugin) {
        super(plugin, "vertigoadmin");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("list").denyTabCompletion()
            .description("List maps")
            .senderCaller(this::list);
        rootNode.addChild("start").arguments("<map>")
            .description("Start a game")
            .completers(CommandArgCompleter.supplyList(() -> plugin.getConfig().getStringList("maps")))
            .senderCaller(this::start);
        rootNode.addChild("test").arguments("<world>")
            .description("Test a world")
            .completers(CommandArgCompleter.supplyList(() -> List.of(plugin.mapFolder.list())))
            .senderCaller(this::test);
        rootNode.addChild("stop").denyTabCompletion()
            .description("Discard the game")
            .senderCaller(this::stop);
        rootNode.addChild("discard").denyTabCompletion()
            .description("Discard the map")
            .senderCaller(this::discard);
        rootNode.addChild("event").arguments("true|false")
            .description("Set event mode")
            .completers(CommandArgCompleter.list("true", "false"))
            .senderCaller(this::event);
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

    private void list(CommandSender sender) {
        List<Component> list = new ArrayList<>();
        int c = 0;
        int perLine = 4;
        for (String mapWorldName : plugin.getConfig().getStringList("maps")) {
            File file = new File(plugin.mapFolder, mapWorldName + "/config.yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String niceName = config.getString("map.name");
            if (niceName == null) {
                niceName = mapWorldName;
            }
            String cmd = "/vertigoadmin load " + mapWorldName;
            list.add(text("[" + niceName + "] ", AQUA)
                     .hoverEvent(showText(text(cmd, GRAY)))
                     .clickEvent(runCommand(cmd)));
            c += 1;
            if (c == perLine) {
                list.add(newline());
                c = 0;
            }
        }
        sender.sendMessage(join(noSeparators(), list));
    }
    private boolean start(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String arg = args[0];
        if (!plugin.getConfig().getStringList("maps").contains(arg)) {
            throw new CommandWarn("Map not found: " + arg);
        }
        sender.sendMessage("Starting map: " + arg);
        plugin.loadAndPlayWorld(arg);
        return true;
    }

    private boolean test(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String arg = args[0];
        File file = new File(plugin.mapFolder, arg);
        if (!file.isDirectory()) {
            throw new CommandWarn("World not found: " + arg);
        }
        sender.sendMessage("Testing world: " + arg);
        plugin.loadAndPlayWorld(arg);
        plugin.game.setTesting(true);
        return true;
    }

    private void stop(CommandSender sender) {
        if (plugin.game == null || !plugin.map_loaded) {
            throw new CommandWarn("There is no map loaded!");
        }
        plugin.game.end();
        sender.sendMessage(text("Game stopped", AQUA));
    }

    private void discard(CommandSender sender) {
        if (plugin.game == null || !plugin.map_loaded) {
            throw new CommandWarn("There is no map loaded!");
        }
        if (!plugin.discardWorld(plugin.game)) {
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
        boolean value;
        try {
            value = Boolean.parseBoolean(args[0]);
        } catch (IllegalStateException iae) {
            throw new CommandWarn("Invalid event mode: " + args[0]);
        }
        plugin.state.event = value;
        plugin.saveState();
        sender.sendMessage(text("Event mode: " + plugin.state.event, plugin.state.event ? AQUA : RED));
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
