package io.github.feydk.vertigo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javafx.geometry.Point2D;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class GameMap
{
	private Set<Point2D> processedChunks = new HashSet<>();
	private List<Location> spawnLocations = new ArrayList<>();
	private List<String> credits = new ArrayList<>();
	private List<ItemStack> blocks = new ArrayList<ItemStack>();
	private List<Location> jumpSpots = new ArrayList<Location>();
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
	
	// Stuff used for boundaries.
	private List<Location> boundaries = new ArrayList<Location>();
	private double minX, minZ, maxX, maxZ, minY, maxY;
			
	public GameMap(int chunkRadius, VertigoGame game)
	{
		this.chunkRadius = chunkRadius;
		this.game = game;
	}
	
	public void addBlock()
	{
		blockCount++;
	}
	
	public int getWaterLeft()
	{
		return waterCount - blockCount;
	}
	
	public int getStartingTime()
	{
		return time;
	}
	
	public boolean getLockTime()
	{
		return lockTime;
	}
	
	public Location getRingLocation(double ringChance)
	{
		double number = Math.random() * 100;
		
		if(number - ringChance <= 0)
		{
			// The upper limit of the ring is 10 blocks below the jump threshold. This is to make sure there's a decent enough chance to actually hit it.
			// The lower limit is 5 blocks above the boundary.
			int max = (int)(jumpThreshold.getY() - 10);
			int min = (int)(maxY + 5);
			
			if(max > min)
			{
				// Then pick a random location within the boundaries.
				
				Random r = new Random(System.currentTimeMillis());
			
				int y = r.nextInt(max - min) + min;
				int x = (int)(r.nextInt((int)(maxX - minX)) + minX);
				int z = (int)(r.nextInt((int)(maxZ - minZ)) + minZ);
				
				return new Location(game.world, x, y, z);
			}
		}
		
		return null;
	}
	
	public String getCredits()
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
	
	/*@SuppressWarnings("deprecation")
	public void animateBlocks(ColorBlock currentColor)
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
	
	public ItemStack getRandomBlock()
	{
		Random r = new Random(System.currentTimeMillis());
		
		return blocks.get(r.nextInt(blocks.size()));
	}
	
	@SuppressWarnings("deprecation")
	public boolean isBlock(Material material, byte dataId)
	{
		for(ItemStack item : blocks)
		{
			if(item.getType() == material && item.getData().getData() == dataId)
			{
				return true;
			}
		}
		
		return false;
	}
	
	public Location getJumpSpot()
	{
		if(jumpSpots.size() == 1)
			return jumpSpots.get(0);
		
		Random r = new Random(System.currentTimeMillis());
		
		return jumpSpots.get(r.nextInt(jumpSpots.size()));
	}
	
	public double getJumpThresholdY()
	{
		return jumpThreshold.getY();
	}
	
	public Location dealSpawnLocation()
    {
    	if(spawnLocations.isEmpty())
    	{
    		if(game.debug)
    		{
    			game.getLogger().warning("No [SPAWN] points were set. Falling back to world spawn.");
    			game.debugStrings.add("No [SPAWN] points were set.");
    		}
    		
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
	
	public void process(Chunk startingChunk)
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
    		game.denyStart = true;
    		
    		if(game.debug)
    		{
    			game.getLogger().warning("No [JUMP] sign defined.");
   				game.debugStrings.add("No [JUMP] sign was found. At least one is required.");
    		}
    	}
    	
    	if(jumpThreshold == null)
    	{
    		game.denyStart = true;
    		
    		if(game.debug)
    		{
    			game.getLogger().warning("No [THRESHOLD] sign defined.");
   				game.debugStrings.add("No [THRESHOLD] sign was found.");
    		}
    	}
    	
    	if(blocks.size() == 0)
    	{
    		game.denyStart = true;
    		
    		if(game.debug)
    		{
    			game.getLogger().warning("No [BLOCKS] chest defined and/or chest was empty.");
   				game.debugStrings.add("No [BLOCKS] chest and/or chest was empty.");
    		}
    	}
    	
    	if(waterCount == 0)
    	{
    		game.denyStart = true;
    		
    		if(game.debug)
    		{
    			game.getLogger().warning("No [WATER] sign defined.");
   				game.debugStrings.add("No [WATER] sign was found.");
    		}
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
        	
        	if(b1.getY() >= b2.getY())
        	{
        		minY = b2.getY();
        		maxY = b1.getY();
        	}
        	else
        	{
        		minY = b1.getY();
        		maxY = b2.getY();
        	}
        	
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
        	game.denyStart = true;
    		
    		if(game.debug)
    		{
    			game.getLogger().warning("Not enough [BOUNDARY] signs defined.");
   				game.debugStrings.add("Not enough [BOUNDARY] signs. Must have exactly two.");
    		}
        }
    }
	
	// Searches a chunk for map configuration signs.
    private void findChunkSigns(int x, int z)
    {
    	Point2D cc = new Point2D(x, z);
    	
    	if(processedChunks.contains(cc))
    		return;
    	
        processedChunks.add(cc);

        // Process the chunk.
        Chunk chunk = world.getChunkAt(x, z);
        chunk.load();

        for(BlockState state : chunk.getTileEntities())
        {
            if(state instanceof Chest)
            {
            	Chest chestBlock = (Chest)state;
            	Inventory inv = chestBlock.getInventory();
            	
            	if(inv.getName().equalsIgnoreCase("[blocks]"))
            	{
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
            	org.bukkit.material.Sign signMaterial = (org.bukkit.material.Sign)state.getData();
            	Sign signBlock = (Sign)state;
                Block attachedBlock = state.getBlock().getRelative(signMaterial.getAttachedFace());

                String firstLine = signBlock.getLine(0).toLowerCase();

                if(firstLine != null && firstLine.startsWith("[") && firstLine.endsWith("]"))
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
                    	Location location = state.getBlock().getLocation();
                    	jumpThreshold = location;
                    	
                        state.getBlock().setType(Material.AIR);
                    }
                	else if(firstLine.equals("[water]"))
                    {
                    	String t = signBlock.getLine(1);
                    	
                    	if(t != null && !t.isEmpty())
                    	{
                    		try
                    		{
                    			waterCount = Integer.parseInt(t);
                    		}
                    		catch(NumberFormatException e)
                    		{}
                    	}
                    	
                    	state.getBlock().setType(Material.AIR);
                    }
                	// Boundaries.
                    else if(firstLine.equals("[boundary]"))
                    {
                    	Location location = attachedBlock.getLocation();
                        boundaries.add(location);
                    	
                        state.getBlock().setType(Material.AIR);
                        //attachedBlock.setType(Material.AIR);
                    }
                    else if(firstLine.equals("[credits]"))
                    {
                    	for(int i = 1; i < 4; ++i)
                    	{
                    		String credit = signBlock.getLine(i);

                    		if(credit != null && !credit.isEmpty())
                    			credits.add(credit);
                        }
                    	
                        state.getBlock().setType(Material.AIR);
                        attachedBlock.setType(Material.AIR);
                    }
                    else if(firstLine.equals("[time]"))
                    {
                    	String t = signBlock.getLine(1);
                    	
                    	if(t != null && !t.isEmpty())
                    	{
                    		try
                    		{
                    			time = Integer.parseInt(t);
                    		}
                    		catch(NumberFormatException e)
                    		{}
                    	}
                    	
                    	if(time > -1)
                    	{
                    		String l = signBlock.getLine(2);
                    		
                    		if(l != null && !l.isEmpty())
                    		{
                    			if(l.toLowerCase().equals("lock"))
                    				lockTime = true;
                    		}
                    	}
                    	
                        state.getBlock().setType(Material.AIR);
                        attachedBlock.setType(Material.AIR);
                    }
                }
            }
        }
    }
}