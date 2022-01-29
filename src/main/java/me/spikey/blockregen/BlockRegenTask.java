package me.spikey.blockregen;

import org.bukkit.Location;
import org.bukkit.Material;

import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.TimeUnit;

public class BlockRegenTask {
    private Location location;
    private Material material;
    private Instant timeToRegen;


    public BlockRegenTask(Location location, Material material, long seconds) {
        this.location = location;
        this.material = material;
        this.timeToRegen = Instant.now().plusSeconds(seconds);
    }

    public Location getLocation() {
        return location;
    }

    public Material getMaterial() {
        return material;
    }

    public Instant getTimeToRegen() {
        return timeToRegen;
    }
}
