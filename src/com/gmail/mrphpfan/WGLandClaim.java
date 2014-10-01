package com.gmail.mrphpfan;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissionsException;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.Selection;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.commands.RegionCommands;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/**
 * @author Mrphpfan
 */
public class WGLandClaim extends JavaPlugin implements Listener {
	WorldEditPlugin worldEdit = (WorldEditPlugin) Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
	WorldGuardPlugin worldGuard = (WorldGuardPlugin) Bukkit.getServer().getPluginManager().getPlugin("WorldGuard");
	private static final Logger log = Logger.getLogger("Minecraft");
	public static Economy econ = null;
	private Map<String, Flag<?>> flags = new HashMap<String, Flag<?>>();
	private Map<String, FlagCost> flagCosts = new HashMap<String, FlagCost>();
	private double townCost = 0;
	private double plotCost = 0;
	private boolean enablePlots = false;
	private boolean autoExpand = false;
	private int maxRegionWidth = 150;
	private int maxPlotWidth = 30;
	
	@Override
	public void onEnable(){
		getServer().getPluginManager().registerEvents(this, this);
		
		if(!setupEconomy()){
            log.severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
		
		//store all available string flags in a map corresponding to their respective StateFlag
		Flag<?>[] allFlags = DefaultFlag.getFlags();
		for(int i=0;i<allFlags.length;i++){
			Flag<?> f = allFlags[i];
			flags.put(f.getName().toLowerCase(), f);
		}
		
		loadConfiguration();
		
		getLogger().info("WGLandClaim Enabled.");
	}
	
	@Override
	public void onDisable(){
		getLogger().info("WGLandClaim Disabled.");
	}
	
	/**
	 * Reads in configuration variables
	 */
    public void loadConfiguration(){
        File pluginFolder = this.getDataFolder();
		if(!pluginFolder.exists()){
			pluginFolder.mkdir();
		}
		
		this.getConfig().options().copyDefaults(true);
        this.saveConfig();
        
        //default enable plots setting
        if(this.getConfig().get("enable_plots") == null){
	        this.getConfig().addDefault("enable_plots", false);
	        this.saveConfig();
        }
        
        //auto expand regions
        if(this.getConfig().get("region_auto_expand_vert") == null){
	        this.getConfig().addDefault("region_auto_expand_vert", false);
	        this.saveConfig();
        }
        
        //max region dimension
        if(this.getConfig().get("max_width_region") == null){
	        this.getConfig().addDefault("max_width_region", 150);
	        this.saveConfig();
        }
        
        //max plot dimension
        if(this.getConfig().get("max_width_plot") == null){
	        this.getConfig().addDefault("max_width_plot", 30);
	        this.saveConfig();
        }
        
        //default region config
        if(this.getConfig().get("regions") == null){
	        Map<String, Double> defaultPerms = new HashMap<String, Double>();
	        defaultPerms.put("region", 5000.0);
	        defaultPerms.put("plot", 500.0);
	        this.getConfig().addDefault("regions", defaultPerms);
	        this.saveConfig();
        }
        
        //default flags config
        if(this.getConfig().get("flags") == null){
        	Map<String, Double> defaultPerms = new HashMap<String, Double>();
	        defaultPerms.put("pvp", 1000.0);
	        this.getConfig().addDefault("flags", defaultPerms);
	        this.saveConfig();
        }
        
        this.reloadConfig();
        
        FileConfiguration config = this.getConfig();
        //read in enable plots boolean
        Object enable = config.get("enable_plots");
        if(enable != null){
        	enablePlots = (Boolean) enable;
        }
        
        //read in region_auto_expand_vert
        Object expand = config.get("region_auto_expand_vert");
        if(expand != null){
        	autoExpand = (Boolean) expand;
        }
        
        //read in max region width
        Object maxRgWidth = config.get("max_width_region");
        if(maxRgWidth != null){
        	maxRegionWidth = (Integer) maxRgWidth;
        }
        
        //read in max plot width
        Object maxPlWidth = config.get("max_width_plot");
        if(maxPlWidth != null){
        	maxPlotWidth = (Integer) maxPlWidth;
        }
        
        //read in flag costs
        ConfigurationSection section1 = config.getConfigurationSection("flags");
        if(section1 != null){
        	Map<String,Object> flagsMap = section1.getValues(false);
        	Iterator it = flagsMap.entrySet().iterator();
    	    while (it.hasNext()) {
    	        Map.Entry pairs = (Map.Entry)it.next();
    	        String rank = pairs.getKey().toString().toLowerCase();
	        	FlagCost fcst = new FlagCost(rank, (Double) pairs.getValue());
	        	flagCosts.put(rank, fcst);
    	    }
        }else{
        	getLogger().info("Failed to read flag costs.");
        }
        
        //read in region costs
        ConfigurationSection section2 = config.getConfigurationSection("regions");
        if(section2 != null){
        	townCost = section2.getDouble("region");
        	plotCost = section2.getDouble("plot");
        }else{
        	getLogger().info("Failed to read region and plot costs.");
        }
        
    }
	
    /**
     * Sets up vault economy
     * @return Boolean if economy set up successfully or not
     */
	public boolean setupEconomy(){
    	if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onCommandPreprocess(PlayerCommandPreprocessEvent event){
		Player player = event.getPlayer();
		String msg = event.getMessage();
		if(player instanceof Player){
			player = (Player) player;
		}else{
			player.sendMessage("You can't do that.");
			return;
		}
		
		//claiming plot
		if(msg.startsWith("/region claimplot") || msg.startsWith("/rg claimplot")){
			//check if plots have been enabled
			if(!enablePlots){
				return;
			}
			
			if(!player.hasPermission("wglandclaim.claimplot")){
				return;
			}
			
			boolean bypassed = player.hasPermission("wglandclaim.bypass.claimplot");
			
			//cancel the event just for fun
			event.setCancelled(true);
			//first check if they even have enough money
			double balance = econ.getBalance(player.getName());
			
			if(balance < plotCost && !bypassed){
				player.sendMessage(ChatColor.RED + "You need $" + plotCost + " to create a plot!");
				return;
			}
			
			String[] cmdTokens = msg.split(" ");
			if (cmdTokens.length < 3){
				player.sendMessage(ChatColor.GOLD + "Usage: /region claimplot <regionname>");
				return;
			}
			String rgname = msg.split(" ")[2];
			
			RegionManager regionManager = worldGuard.getRegionManager(player.getWorld());
			Selection selection = worldEdit.getSelection(player);
			if(selection != null){
				ProtectedRegion existingRg = regionManager.getRegion(rgname);
				if(existingRg == null){
					//region by that name doesn't already exist
					Location loc1 = selection.getMinimumPoint();
					Location loc2 = selection.getMaximumPoint();
					
					if((selection.getWidth() > maxPlotWidth || selection.getLength() > maxPlotWidth) && !bypassed){
						String dimens = (int) maxPlotWidth + "x" + (int) maxPlotWidth;
						player.sendMessage(ChatColor.RED + "Plots are limited to " + dimens + ". Decrease the width/length of your selection.");
						return;
					}
					
					//check that the first point is in a region (any region, as it will fail if it overlaps another persons region anyway.
					ApplicableRegionSet regions = worldGuard.getRegionManager(loc1.getWorld()).getApplicableRegions(loc1);
					ApplicableRegionSet regions2 = worldGuard.getRegionManager(loc2.getWorld()).getApplicableRegions(loc2);
					
					if(regions.size() < 1 || regions2.size() < 1){
						player.sendMessage(ChatColor.RED + "You can only make plots inside a region you own.");
						return;
					}
					
					//plot was inside regions owned by player
					RegionCommands r = new RegionCommands(worldGuard);
					try{
						CommandContext args = new CommandContext(new String[]{"claim", rgname});
						r.claim(args, player);
					}catch(CommandException e){
						player.sendMessage(ChatColor.RED + e.getMessage());
						e.printStackTrace();
						return;
					}
					
					//make sure that the region creation was a success before charging
					ProtectedRegion newRg = regionManager.getRegion(rgname);
					if(newRg == null){
						player.sendMessage(ChatColor.RED + "Error. Something went wrong.");
						return;
					}
					
					//set plot priority to 1
					newRg.setPriority(1);
					
					//charge them money for the region
					EconomyResponse r1 = econ.withdrawPlayer(player.getName(), plotCost);
					if(r1.transactionSuccess()){
						player.sendMessage(ChatColor.GOLD + "Plot saved successfully as " + rgname + " and you have been charged $" + plotCost + ".");
					}else{
						player.sendMessage(ChatColor.RED + "An internal error occured. Try again.");
					}
				}else{
					player.sendMessage(ChatColor.RED + "A region by that name already exists! Pick a different name.");
				}
			}else{
				player.sendMessage(ChatColor.RED + "You need to select points first!");
			}
			
		}else if(msg.startsWith("/region claim ") || msg.startsWith("/rg claim ")){
			//player trying to claim region
			
			if(!player.hasPermission("worldguard.region.claim")){
				return;
			}
			
			if(player.hasPermission("wglandclaim.bypass.claim")){
				//don't need to charge or limit at all, so just return
				return;
			}
			
			//cancel the normal region claim event
			event.setCancelled(true);
			
			//first check if they even have enough money
			double balance = econ.getBalance(player.getName());
			if(balance < townCost){
				player.sendMessage(ChatColor.RED + "You need $" + townCost + " to create a region!");
				return;
			}
			
			String rgname = msg.split(" ")[2];
			
			RegionManager regionManager = worldGuard.getRegionManager(player.getWorld());
			Selection selection = worldEdit.getSelection(player);
			if(selection != null){
				
				if(selection.getWidth() > maxRegionWidth || selection.getLength() > maxRegionWidth){
					String dimens = (int) maxRegionWidth + "x" + (int) maxRegionWidth;
					player.sendMessage(ChatColor.RED + "Regions are limited to " + dimens + ". Decrease the width/length of your selection.");
					return;
				}
				
				ProtectedRegion existingRg = regionManager.getRegion(rgname);
				if(existingRg == null){
					//region by that name doesn't already exist
					if(autoExpand){
						Bukkit.getServer().dispatchCommand(player, "/expand vert");
					}
					RegionCommands r = new RegionCommands(worldGuard);
					try{
						CommandContext args = new CommandContext(new String[]{"claim", rgname});
						r.claim(args, player);
					}catch(CommandException e){
						if(e.getMessage() != null){
							player.sendMessage(ChatColor.RED + e.getMessage());
						}
						e.printStackTrace();
						return;
					}
					
					//make sure that the region creation was a success before charging
					ProtectedRegion newRg = regionManager.getRegion(rgname);
					if(newRg == null){
						player.sendMessage(ChatColor.RED + "Error. Something went wrong.");
						return;
					}
					
					//charge them money for the region
					EconomyResponse r1 = econ.withdrawPlayer(player.getName(), townCost);
					if(r1.transactionSuccess()){
						player.sendMessage(ChatColor.GOLD + "Region saved successfully as " + rgname + " and you have been charged $" + townCost + ".");
					}else{
						player.sendMessage(ChatColor.RED + "An internal error occured. Try again.");;
					}
				}else{
					player.sendMessage(ChatColor.RED + "A region by that name already exists! Pick a different name.");
				}
			}else{
				player.sendMessage(ChatColor.RED + "You need to select points first!");
			}
		}else if(msg.startsWith("/region flag ") || msg.startsWith("/rg flag ") || msg.startsWith("/region f ") || msg.startsWith("/rg f ")){
			if(player.hasPermission("wglandclaim.bypass.flags")){
				return;
			}
			//move onto flag checking
			String[] tokens = msg.split(" ");
			//must have format /region flag rgname flag deny
			if(tokens.length < 5){
				return;
			}
			
			String rgname = tokens[2];
			String flagName = tokens[3].toLowerCase();
			String setting = tokens[4].toLowerCase();
			
			RegionManager regionManager = worldGuard.getRegionManager(player.getWorld());
			ProtectedRegion existingRg = regionManager.getRegion(rgname);
			
			if(!existingRg.isOwner(player.getName())){
				return;
			}
			
			FlagCost fc = flagCosts.get(flagName);
			if(fc == null){
				return;
			}
			
			if(existingRg.getFlag(flags.get(flagName)) == null){	
				event.setCancelled(true);
				//only charge if there is no flag set
				
				RegionCommands r = new RegionCommands(worldGuard);
				CommandContext args;
				try {
					args = new CommandContext(new String[]{"flag", rgname, flagName, setting});
					r.flag(args, player);
				} catch (CommandException e) {
					getLogger().info("There was an error setting " + player.getName() + " flag.");
					if(e instanceof CommandPermissionsException){
						player.sendMessage(ChatColor.RED + "You don't have permission to set that flag.");
					}
					e.printStackTrace();
				}
				
				//check if the region has the flag now, if it does, charge
				if(existingRg.getFlag(flags.get(flagName)) != null){
					double price = fc.getCost();
					EconomyResponse r1 = econ.withdrawPlayer(player.getName(), price);
					if(r1.transactionSuccess()){
						player.sendMessage(ChatColor.GOLD + "Charged $" + price + ".");
					}else{
						player.sendMessage(ChatColor.RED + "You need $" + price + " to set flag " + flagName);
						return;
					}
				}
			}
		}
	}
}
