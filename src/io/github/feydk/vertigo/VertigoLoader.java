package io.github.feydk.vertigo;

import org.apache.commons.lang.RandomStringUtils;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

public final class VertigoLoader extends JavaPlugin implements Listener
{
    boolean debug;

    private VertigoGame game;
    static String chatPrefix = "§7[§bVertigo§7] ";

    private boolean map_loaded;

    public void onEnable()
    {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        this.debug = true;
        this.game = new VertigoGame(this);

        getCommand("vertigo").setExecutor((a, b, c, d) -> onGameCommand(a, d));
        getCommand("vertigoadmin").setExecutor((a, b, c, d) -> onAdminCommand(a, d));
    }

    public void onDisable()
    {
        for(World w : getServer().getWorlds())
        {
            if(w.getWorldFolder().getName().startsWith("Vertigo_temp_"))
            {
                boolean unloaded = getServer().unloadWorld(w, false);

                if(unloaded)
                    deleteFiles(w.getWorldFolder());
            }
        }
    }

    @EventHandler
    public void onPlayerWorldChange(PlayerChangedWorldEvent e)
    {
        if(game.world == null)
            return;

        if(e.getFrom().getName().equals(game.world.getName()))
        {
            Player player = e.getPlayer();
            game.leave(player);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event)
    {
        if(game.world == null)
            return;

        if(!(event.getEntity() instanceof Player))
            return;

        Player player = (Player)event.getEntity();

        if(player.getWorld().getName().equals(game.world.getName()))
        {
            event.setCancelled(true);

            // Pass it on to game.
            game.playerDamage(player, event);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event)
    {
        if(game.world == null)
            return;

        Player player = event.getPlayer();

        if(player.getWorld().getName().equals(game.world.getName()))
        {
            game.playerMove(player, event);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event)
    {
        if(game.world == null)
            return;

        if(!(event.getEntity() instanceof Player))
            return;

        Player player = (Player)event.getEntity();

        if(player.getWorld().getName().equals(game.world.getName()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event)
    {
        if(game.world == null)
            return;

        Player player = event.getPlayer();

        if(player.getWorld().getName().equals(game.world.getName()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPickupItem(EntityPickupItemEvent event)
    {
        if(game.world == null)
            return;

        if(!(event.getEntity() instanceof Player))
            return;

        Player player = (Player)event.getEntity();

        if(player.getWorld().getName().equals(game.world.getName()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event)
    {
        if(game.world == null)
            return;

        Player player = event.getPlayer();

        if(player.getWorld().getName().equals(game.world.getName()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event)
    {
        if(game.world == null)
            return;

        Player player = event.getPlayer();

        if(player.getWorld().getName().equals(game.world.getName()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onElytra(EntityToggleGlideEvent event)
    {
        if(game.world == null)
            return;

        if(!(event.getEntity() instanceof Player))
            return;

        Player player = (Player)event.getEntity();

        if(player.getWorld().getName().equals(game.world.getName()))
            event.setCancelled(true);
    }

    private boolean onGameCommand(CommandSender sender, String[] args)
    {
        if(args.length != 0) return false;
        if(!(sender instanceof Player)) return false;

        Player player = (Player) sender;

        if(game.state == VertigoGame.GameState.INIT)
        {
            player.sendMessage("There is no Vertigo game going on right now.");
        }
        else if(game.state == VertigoGame.GameState.READY)
        {
            game.join(player, false);
        }
        else if(game.state == VertigoGame.GameState.COUNTDOWN_TO_START || game.state == VertigoGame.GameState.RUNNING)
        {
            game.join(player, true);
        }

        return true;
    }

    private boolean onAdminCommand(CommandSender sender, String[] args)
    {
        if(!(sender instanceof Player))
            return false;

        Player admin = (Player)sender;

        /*BossBar bar = getServer().createBossBar(ChatColor.BOLD + "Vertigo " + ChatColor.WHITE + "starting..", BarColor.BLUE, BarStyle.SOLID);
        bar.setVisible(true);
        bar.addPlayer(admin);*/

        if(args.length == 0)
        {
            List<Object> list = new ArrayList<>();

            if(!map_loaded)
            {
                list.add("Hello " + admin.getName() + ". There is no Vertigo game set up right now.\nYou can set one up by selecting a map:\n");
                int c = 0;
                int perLine = 4;

                for(Object o : Objects.requireNonNull(getConfig().getList("maps")))
                {
                    if(o instanceof ArrayList)
                    {
                        @SuppressWarnings("unchecked")
                        ArrayList<LinkedHashMap<String, String>> s = (ArrayList<LinkedHashMap<String, String>>) o;

                        list.add(Msg.button(ChatColor.AQUA + s.get(1).get("name") + " " + ChatColor.GRAY + "✦ ", "/vertigoadmin load " + s.get(0).get("world"), "/vertigoadmin load " + s.get(0).get("world")));
                        c++;

                        //c += s.get(1).get("name").length() + 3;

                        if(c == perLine)
                        {
                            list.add("\n");
                            c = 0;
                        }

                    }
                }

                /*List<String> maps = getConfig().getStringList("maps");

                int c = 0;
                for(String s : maps)
                {
                    list.add(Msg.button(ChatColor.AQUA + s + " " + ChatColor.GRAY + "✦ ", "/vertigoadmin load " + s, "/vertigoadmin load " + s));
                    c += s.length() + 3;

                    if(c >= 40)
                    {
                        list.add("\n");
                        c = 0;
                    }
                }*/

                Msg.sendRaw(admin, list);
            }
            else
            {
                if(game.state == VertigoGame.GameState.INIT)
                {
                    list.add("Game is set up. Activate it whenever you want.\n");
                    list.add(Msg.button(ChatColor.AQUA + "[Activate game]", "Make the game accept players.", "/vertigoadmin ready"));
                    list.add(" or ");
                    list.add(Msg.button(ChatColor.AQUA + "[Discard game]", "Discard the game.", "/vertigoadmin discard"));

                    Msg.sendRaw(admin, list);
                }
                else if(game.state == VertigoGame.GameState.READY)
                {
                    list.add("Game is ready.\n");
                    list.add(Msg.button(ChatColor.AQUA + "[Announce game]", "Announce the game to all players.", "/vertigoadmin announce"));
                    list.add(" or ");
                    list.add(Msg.button(ChatColor.AQUA + "[Discard game]", "Discard the game.", "/vertigoadmin discard"));
                    list.add(" or ");
                    list.add(Msg.button(ChatColor.GREEN + "[Start game]", "Start the game.", "/vertigoadmin start"));
                    list.add("\n");
                    list.add(Msg.button(ChatColor.AQUA + "[Join game]", "Join the game.", "/vertigo"));

                    Msg.sendRaw(admin, list);
                }
                else if(game.state == VertigoGame.GameState.RUNNING)
                {
                    list.add("Game is running.\n");
                    list.add(Msg.button(ChatColor.AQUA + "[Discard game]", "Discard the game.", "/vertigoadmin discard"));
                    list.add(" or ");
                    list.add(Msg.button(ChatColor.GREEN + "[End game]", "End the game.", "/vertigoadmin end"));

                    Msg.sendRaw(admin, list);
                }
                else if(game.state == VertigoGame.GameState.ENDED)
                {
                    list.add("Game ended.\n");
                    list.add(Msg.button(ChatColor.AQUA + "[Discard game]", "Discard the game.", "/vertigoadmin discard"));
                    list.add(" or ");
                    list.add(Msg.button(ChatColor.GREEN + "[Reset game]", "Reset the game to be started again.", "/vertigoadmin reset"));

                    Msg.sendRaw(admin, list);
                }
                else
                {
                    admin.sendMessage("Game state is " + game.state.toString());
                }
            }

            return true;
        }

        String cmd = args[0];

        if(cmd.equalsIgnoreCase("load"))
        {
            String worldName = "Vertigo_temp_" + RandomStringUtils.randomAlphabetic(10);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(getServer().getWorldContainer() + "/" + args[1] + "/config.yml"));

            World org = loadWorld(args[1], config);

            File source = org.getWorldFolder();
            File target = new File(getServer().getWorldContainer() + "/" + worldName);

            copyFileStructure(source, target);

            World gameWorld = loadWorld(worldName, config);

            game.setWorld(gameWorld);

            if(game.setup(admin))
            {
                map_loaded = true;
                admin.teleport(game.map.dealSpawnLocation());
                admin.performCommand("vertigoadmin");
            }
        }
        else if(cmd.equalsIgnoreCase("discard"))
        {
            game.shutdown();

            for(Player p : game.world.getPlayers())
            {
                game.leave(p);
                p.teleport(getServer().getWorlds().get(0).getSpawnLocation());

                if(p.getGameMode() == GameMode.ADVENTURE || p.getGameMode() == GameMode.SPECTATOR)
                    p.setGameMode(GameMode.SURVIVAL);
            }

            File dir = game.world.getWorldFolder();
            boolean unloaded = getServer().unloadWorld(game.world, false);

            if(unloaded)
            {
                deleteFiles(dir);
                map_loaded = false;
                game.discard();

                admin.sendMessage("The game has been discarded.");
            }
            else
            {
                admin.sendMessage(ChatColor.RED + "The game world could not be unloaded.");
            }
        }
        else if(cmd.equalsIgnoreCase("ready"))
        {
            game.ready(admin);
            admin.performCommand("vertigoadmin");
        }
        else if(cmd.equalsIgnoreCase("announce"))
        {
            if(game.state != VertigoGame.GameState.READY)
            {
                admin.sendMessage(ChatColor.RED + "No can do. The game isn't ready yet.");
                return true;
            }

            if(getServer().getOnlinePlayers().size() > 0)
            {
                List<Object> list = new ArrayList<>();
                list.add(Msg.format(chatPrefix + "A game of Vertigo is about to start.\n"));
                list.add(Msg.button(ChatColor.DARK_AQUA + "[Click here to join]", "Join this game of Vertigo.", "/vertigo"));

                for(Player player : getServer().getOnlinePlayers())
                {
                    Msg.sendRaw(player, list);
                }
            }
        }
        else if(cmd.equalsIgnoreCase("start"))
        {
            game.start();
        }
        else if(cmd.equalsIgnoreCase("end"))
        {
            game.end();
        }
        else if(cmd.equalsIgnoreCase("reset"))
        {
            game.reset();
        }
        else if(cmd.equalsIgnoreCase("kickplayers"))
        {
            for(Player p : game.world.getPlayers())
            {
                p.teleport(getServer().getWorlds().get(0).getSpawnLocation());

                if(p.getGameMode() == GameMode.ADVENTURE || p.getGameMode() == GameMode.SPECTATOR)
                    p.setGameMode(GameMode.SURVIVAL);
            }
        }
        else if(cmd.equalsIgnoreCase("spectate"))
        {
            game.join(admin, true);
        }

        return true;
    }

    private World loadWorld(String worldname, YamlConfiguration config)
    {
        WorldCreator wc = new WorldCreator(worldname);
        wc.environment(World.Environment.valueOf(config.getString("world.Environment")));
        wc.generateStructures(config.getBoolean("world.GenerateStructures"));
        wc.generator(config.getString("world.Generator"));
        wc.type(WorldType.valueOf(config.getString("world.WorldType")));
        getServer().createWorld(wc);

        World world = getServer().getWorld(worldname);
        world.setAutoSave(false);

        return world;
    }

    private void copyFileStructure(File source, File target)
    {
        try
        {
            ArrayList<String> ignore = new ArrayList<>(Arrays.asList("uid.dat", "session.lock"));

            if(!ignore.contains(source.getName()))
            {
                if(source.isDirectory())
                {
                    if(!target.exists())
                    {
                        if(!target.mkdirs())
                            throw new IOException("Couldn't create world directory!");
                    }

                    String[] files = source.list();

                    for(String file : files)
                    {
                        File srcFile = new File(source, file);
                        File destFile = new File(target, file);
                        copyFileStructure(srcFile, destFile);
                    }
                }
                else
                {
                    InputStream in = new FileInputStream(source);
                    OutputStream out = new FileOutputStream(target);

                    byte[] buffer = new byte[1024];
                    int length;

                    while((length = in.read(buffer)) > 0)
                        out.write(buffer, 0, length);

                    in.close();
                    out.close();
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void deleteFiles(File path)
    {
        if(path.exists())
        {
            for(File file : path.listFiles())
            {
                if(file.isDirectory())
                    deleteFiles(file);
                else
                    file.delete();
            }

            path.delete();
        }
    }
}