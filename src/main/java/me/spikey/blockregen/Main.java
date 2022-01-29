package me.spikey.blockregen;

import com.google.common.collect.Lists;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import me.spikey.blockregen.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class Main extends JavaPlugin implements Listener {

    public static Flag blockRegen;

    private List<BlockRegenTask> blockRegenTasks;

    private Long timeDelay;


    @Override
    public void onLoad() {

        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

        try {
            StateFlag flag = new StateFlag("blockregen", false);
            registry.register(flag);
            blockRegen = flag;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get("blockregen");
            if (existing instanceof StateFlag) {
                blockRegen = (StateFlag) existing;
            } else {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.timeDelay = getConfig().getLong("blockDelay");

        SchedulerUtils.setPlugin(this);

        this.blockRegenTasks = Lists.newArrayList();


        SchedulerUtils.runRepeating(() -> {

            SchedulerUtils.runAsync(() -> {
                List<BlockRegenTask> toRemove = Lists.newArrayList();
                for (BlockRegenTask regenTask : blockRegenTasks) {
                    if (!regenTask.getTimeToRegen().isBefore(Instant.now())) continue;
                    SchedulerUtils.runSync(() -> {
                        Location location = regenTask.getLocation();
                        location.getWorld().getBlockAt(location).setType(regenTask.getMaterial());
                    });
                    toRemove.add(regenTask);
                }
                blockRegenTasks.removeAll(toRemove);
            });

        }, getConfig().getLong("timebetweenchecks"));

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (BlockRegenTask regenTask : blockRegenTasks) {
            Location location = regenTask.getLocation();
            location.getWorld().getBlockAt(location).setType(regenTask.getMaterial());
        }
    }

    @EventHandler
    public void blockBreak(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("blockregen.bypass")) return;
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(event.getPlayer().getLocation()));

        ProtectedRegion highPR = null;
        for (ProtectedRegion region : set.getRegions()) {
            if (highPR == null || region.getPriority() > highPR.getPriority()) highPR = region;
        }

        if (highPR == null) return;

        if (!(highPR.getFlags().get(blockRegen) == StateFlag.State.ALLOW)) return;

        blockRegenTasks.add(new BlockRegenTask(event.getBlock().getLocation(), event.getBlock().getType(), timeDelay));
    }

    @EventHandler
    public void placeBlock(BlockPlaceEvent event) {

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(event.getBlock().getLocation()));

        ProtectedRegion highPR = null;
        for (ProtectedRegion region : set.getRegions()) {
            if (highPR == null || region.getPriority() > highPR.getPriority()) highPR = region;
        }

        if (highPR == null) return;

        if (!(highPR.getFlags().get(blockRegen) == StateFlag.State.ALLOW)) return;

        for (BlockRegenTask regenTask : Lists.newCopyOnWriteArrayList(blockRegenTasks)) {
            if (event.getBlock().getLocation().equals(regenTask.getLocation())) {
                if (event.getPlayer().hasPermission("blockregen.bypass")) {
                    blockRegenTasks.remove(regenTask);
                    event.getPlayer().sendMessage(ChatColor.BLUE + "Regen task for this block has been canceled.");
                    return;
                }
                event.getPlayer().sendMessage(ChatColor.BLUE + "This block is currently regenerating...");
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void blockExplode(EntityExplodeEvent event) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        for (Block block : event.blockList()) {
            if (block.getType().equals(Material.AIR)) continue;

            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(block.getLocation()));

            ProtectedRegion highPR = null;
            for (ProtectedRegion region : set.getRegions()) {
                if (highPR == null || region.getPriority() > highPR.getPriority()) highPR = region;
            }

            if (highPR == null) return;

            if (!(highPR.getFlags().get(blockRegen) == StateFlag.State.ALLOW)) return;

            blockRegenTasks.add(new BlockRegenTask(block.getLocation(), block.getType(), timeDelay));
        }
    }

}
