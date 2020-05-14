package io.github.feydk.vertigo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.*;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class GameMap
{
    private List<Location> spawnLocations = new ArrayList<>();
    private List<String> credits = new ArrayList<>();
    private List<ItemStack> blocks = new ArrayList<>();
    private List<Location> jumpSpots = new ArrayList<>();
    private List<Block> skulls = new ArrayList<>();
    private Location jumpThreshold;
    private int waterCount;
    private int blockCount;
    private int time;
    private boolean lockTime;

    private World world;
    private VertigoGame game;
    private int chunkRadius;
    private boolean spawnLocationsRandomized;
    private int spawnLocationIter = 0;

    // Golden ring stuff.
    private double ringChance;
    Location currentRingCenter = null;
    private List<Block> currentRing = new ArrayList<>();

    // Stuff used for boundaries.
    private List<Location> boundaries = new ArrayList<>();
    private double minX, minZ, maxX, maxZ, maxY;

    GameMap(int chunkRadius, VertigoGame game)
    {
        this.chunkRadius = chunkRadius;
        this.game = game;

        ringChance = game.loader.getConfig().getDouble("general.ringChance");
    }

    void addBlock()
    {
        blockCount++;
    }

    void addSkull(Block block)
    {
        skulls.add(block);
    }

    int getWaterLeft()
    {
        return waterCount - blockCount;
    }

    int getStartingTime()
    {
        return time;
    }

    boolean getLockTime()
    {
        return lockTime;
    }

    void removeRing()
    {
        if(currentRingCenter != null)
        {
            for(Block b : currentRing)
                game.world.getBlockAt(b.getLocation()).setType(Material.AIR);

            game.world.getBlockAt(currentRingCenter).setType(Material.AIR);

            currentRing.clear();

            currentRingCenter = null;
        }
    }

    boolean spawnRing()
    {
        double number = Math.random() * 100;

        if(number - ringChance <= 0)
        {
            // The upper limit of the ring is 10 blocks below the jump threshold. This is to make sure there's a decent enough chance to actually hit it.
            // The lower limit is 5 blocks above the boundary.
            int max = (int) (jumpThreshold.getY() - 10);
            int min = (int) (maxY + 5);

            if(max > min)
            {
                // Then pick a random location within the boundaries.
                Random r = new Random(System.currentTimeMillis());

                int y = r.nextInt(max - min) + min;
                int x = (int) (r.nextInt((int) (maxX - minX)) + minX);
                int z = (int) (r.nextInt((int) (maxZ - minZ)) + minZ);

                currentRingCenter = new Location(game.world, x, y, z);

                currentRing.add(game.world.getBlockAt(currentRingCenter.clone().add(1, 0, 0)));
                currentRing.add(game.world.getBlockAt(currentRingCenter.clone().subtract(1, 0, 0)));
                currentRing.add(game.world.getBlockAt(currentRingCenter.clone().add(0, 0, 1)));
                currentRing.add(game.world.getBlockAt(currentRingCenter.clone().subtract(0, 0, 1)));

                for(Block b : currentRing)
                    game.world.getBlockAt(b.getLocation()).setType(Material.GOLD_BLOCK);

                return true;
            }
        }

        return false;
    }

    void reset()
    {
        blockCount = 0;
        removeRing();

        if(skulls.size() > 0)
        {
            for(Block b : skulls)
            {
                b.setType(Material.AIR);
                game.world.getBlockAt(b.getLocation().clone().subtract(0, 1, 0)).setType(Material.WATER);
            }

            skulls.clear();
        }
    }

    String getCredits()
    {
        if(credits.size() > 0)
        {
            if(credits.size() == 1)
            {
                return credits.get(0);
            }
            else
            {
                String c = "";

                for(int i = 0; i < credits.size(); i++)
                {
                    c += credits.get(i);

                    int left = credits.size() - (i + 1);

                    if(left == 1)
                        c += " and ";
                    else if(left > 1)
                        c += ", ";
                }

                return c;
            }
        }

        return "";
    }

    /*public void animateBlocks(ColorBlock currentColor)
      {
      for(Block b : replacedBlocks)
      {
      if(b.getType() != Material.AIR && b.getTypeId() == currentColor.TypeId && b.getData() == currentColor.DataId)
      {
      world.spigot().playEffect(b.getLocation().add(.5, 1.5, .5), Effect.COLOURED_DUST, 0, 0, .5f, .5f, .5f, .01f, 5, 50);
      //(Location location, Effect effect, int id, int data, float offsetX, float offsetY, float offsetZ, float speed, int particleCount, int radius)
      }
      }
      }*/

    ItemStack getRandomBlock()
    {
        Random r = new Random(System.currentTimeMillis());

        return blocks.get(r.nextInt(blocks.size()));
    }

    boolean isBlock(Material material)
    {
        for(ItemStack item : blocks)
        {
            if(item.getType() == material)
            {
                return true;
            }
        }

        return false;
    }

    Location getJumpSpot()
    {
        if(jumpSpots.size() == 1)
            return jumpSpots.get(0);

        Random r = new Random(System.currentTimeMillis());

        return jumpSpots.get(r.nextInt(jumpSpots.size()));
    }

    double getJumpThresholdY()
    {
        return jumpThreshold.getY();
    }

    Location dealSpawnLocation()
    {
        if(spawnLocations.isEmpty())
        {
            if(game.loader.debug)
                game.loader.getLogger().warning("No [SPAWN] points were set. Falling back to world spawn.");

            return world.getSpawnLocation();
        }

        if(!spawnLocationsRandomized)
        {
            Random random = new Random(System.currentTimeMillis());
            spawnLocationsRandomized = true;
            Collections.shuffle(spawnLocations, random);
        }

        if(spawnLocationIter >= spawnLocations.size())
            spawnLocationIter = 0;

        int i = spawnLocationIter++;

        return spawnLocations.get(i);
    }

    boolean process(Chunk startingChunk)
    {
        world = startingChunk.getWorld();

        int cx = startingChunk.getX();
        int cz = startingChunk.getZ();

        // Crawl the map in a <chunkRadius> chunk radius in all directions.
        for(int dx = -chunkRadius; dx <= chunkRadius; dx++)
        {
            for(int dz = -chunkRadius; dz <= chunkRadius; dz++)
            {
                int x = cx + dx;
                int z = cz + dz;

                // Find signs to register the blocks used in the map.
                findChunkSigns(x, z);
            }
        }

        if(jumpSpots.size() == 0)
        {
            if(game.loader.debug)
            {
                game.loader.getLogger().warning("No [JUMP] sign was found. At least one is required.");
            }

            return false;
        }

        if(jumpThreshold == null)
        {
            if(game.loader.debug)
            {
                game.loader.getLogger().warning("No [THRESHOLD] sign was found.");
            }

            return false;
        }

        if(blocks.size() == 0)
        {
            if(game.loader.debug)
            {
                game.loader.getLogger().warning("No [BLOCKS] chest found and/or chest was empty.");
            }

            return false;
        }

        if(waterCount == 0)
        {
            if(game.loader.debug)
            {
                game.loader.getLogger().warning("No [WATER] sign was found.");
            }

            return false;
        }

        // Determine boundaries.
        if(boundaries.size() == 2)
        {
            Location b1 = boundaries.get(0);
            Location b2 = boundaries.get(1);

            if(b1.getX() >= b2.getX())
            {
                minX = b2.getX();
                maxX = b1.getX();
            }
            else
            {
                minX = b1.getX();
                maxX = b2.getX();
            }

            //minY = b2.getY();
            //minY = b1.getY();
            maxY = Math.max(b1.getY(), b2.getY());

            if(b1.getZ() >= b2.getZ())
            {
                minZ = b2.getZ();
                maxZ = b1.getZ();
            }
            else
            {
                minZ = b1.getZ();
                maxZ = b2.getZ();
            }

            //System.out.println("X: " + minX + ", " + maxX);
            //System.out.println("Y: " + minY + ", " + maxY);
            //System.out.println("Z: " + minZ + ", " + maxZ);
        }
        else
        {
            if(game.loader.debug)
            {
                game.loader.getLogger().warning("Not enough [BOUNDARY] signs. Must have exactly two.");
            }

            return false;
        }

        return true;
    }

    // Searches a chunk for map configuration signs.
    private void findChunkSigns(int x, int z)
    {
        // Process the chunk.
        Chunk chunk = world.getChunkAt(x, z);
        chunk.load();

        for(BlockState state : chunk.getTileEntities())
        {
            if(state instanceof Chest)
            {
                Chest chestBlock = (Chest) state;

                if(chestBlock.getCustomName() != null && chestBlock.getCustomName().equalsIgnoreCase("[blocks]"))
                {
                    Inventory inv = chestBlock.getInventory();
                    for(ItemStack item : inv.getContents())
                    {
                        if(item != null)
                            blocks.add(item);
                    }

                    inv.clear();
                    state.getBlock().setType(Material.AIR);
                }
            }
            else if(state instanceof Sign)
            {
                Block sign_block = state.getBlock();
                BlockData data = sign_block.getBlockData();
                Block attached = null;

                if(data instanceof Directional)
                {
                    Directional dir = (Directional)data;
                    attached = sign_block.getRelative(dir.getFacing().getOppositeFace());
                }

                String firstLine = ((Sign)state).getLine(0).toLowerCase();

                if(firstLine.startsWith("[") && firstLine.endsWith("]"))
                {
                    if(firstLine.equals("[spawn]"))
                    {
                        Location location = state.getBlock().getLocation().add(.5, .5, .5);
                        Vector lookAt = world.getSpawnLocation().toVector().subtract(location.toVector());
                        location.setDirection(lookAt);
                        spawnLocations.add(location);

                        state.getBlock().setType(Material.AIR);
                    }
                    else if(firstLine.equals("[jump]"))
                    {
                        Location location = state.getBlock().getLocation().add(.5, .5, .5);
                        Location spawn = world.getSpawnLocation();
                        spawn.setY(location.getY());
                        Vector lookAt = spawn.toVector().subtract(location.toVector());
                        location.setDirection(lookAt);
                        jumpSpots.add(location);

                        state.getBlock().setType(Material.AIR);
                    }
                    else if(firstLine.equals("[threshold]"))
                    {
                        jumpThreshold = state.getBlock().getLocation();

                        state.getBlock().setType(Material.AIR);
                    }
                    else if(firstLine.equals("[water]"))
                    {
                        String t = ((Sign)state).getLine(1);

                        if(!t.isEmpty())
                        {
                            try
                            {
                                waterCount = Integer.parseInt(t);
                            }
                            catch(NumberFormatException ignored)
                            {}
                        }

                        state.getBlock().setType(Material.AIR);
                    }
                    // Boundaries.
                    else if(firstLine.equals("[boundary]"))
                    {
                        Location location = attached.getLocation();
                        boundaries.add(location);

                        state.getBlock().setType(Material.AIR);
                        //attachedBlock.setType(Material.AIR);
                    }
                    else if(firstLine.equals("[credits]"))
                    {
                        for(int i = 1; i < 4; ++i)
                        {
                            String credit = ((Sign) state).getLine(i);

                            if(credit != null && !credit.isEmpty())
                                credits.add(credit);
                        }

                        state.getBlock().setType(Material.AIR);

                        //if(attached != null)
                        //    attached.setType(Material.AIR);
                    }
                    else if(firstLine.equals("[time]"))
                    {
                        String t = ((Sign) state).getLine(1);

                        if(!t.isEmpty())
                        {
                            try
                            {
                                time = Integer.parseInt(t);
                            }
                            catch(NumberFormatException ignored)
                            {}
                        }

                        if(time > -1)
                        {
                            String l = ((Sign) state).getLine(2);

                            if(!l.isEmpty())
                            {
                                if(l.toLowerCase().equals("lock"))
                                    lockTime = true;
                            }
                        }

                        state.getBlock().setType(Material.AIR);

                        //if(attached != null)
                        //    attached.setType(Material.AIR);
                    }
                }
            }
        }
    }
}
