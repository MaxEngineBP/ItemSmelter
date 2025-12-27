package com.itemsmelter.models;

import org.bukkit.Material;
import java.util.Map;

public class SmeltableItem {

    private final String id;
    private final Material material;
    private final boolean enabled;
    private final String smeltIn;
    private final boolean ignoreSticks;
    private final boolean durabilityBased;
    private final Material outputMaterial;
    private final int maxDurability;
    private final double smeltTimeMultiplier;
    private final Map<Integer, DurabilityRange> durabilityRanges;

    public SmeltableItem(String id, Material material, boolean enabled, String smeltIn,
                         boolean ignoreSticks, boolean durabilityBased, Material outputMaterial,
                         int maxDurability, double smeltTimeMultiplier,
                         Map<Integer, DurabilityRange> durabilityRanges) {
        this.id = id;
        this.material = material;
        this.enabled = enabled;
        this.smeltIn = smeltIn;
        this.ignoreSticks = ignoreSticks;
        this.durabilityBased = durabilityBased;
        this.outputMaterial = outputMaterial;
        this.maxDurability = maxDurability;
        this.smeltTimeMultiplier = smeltTimeMultiplier;
        this.durabilityRanges = durabilityRanges;
    }

    public int calculateOutput(int currentDurability) {
        if (!durabilityBased) {
            // For items like minecarts that don't use durability
            DurabilityRange range = durabilityRanges.get(100);
            if (range != null) {
                return range.getRandom();
            }
            return 1;
        }

        double durabilityPercent = ((double) currentDurability / maxDurability) * 100.0;

        // Find the appropriate range
        int applicableThreshold = 0;
        for (int threshold : durabilityRanges.keySet()) {
            if (durabilityPercent >= threshold && threshold > applicableThreshold) {
                applicableThreshold = threshold;
            }
        }

        DurabilityRange range = durabilityRanges.get(applicableThreshold);
        if (range == null) {
            return 0;
        }

        return range.getRandom();
    }

    // Getters
    public String getId() { return id; }
    public Material getMaterial() { return material; }
    public boolean isEnabled() { return enabled; }
    public String getSmeltIn() { return smeltIn; }
    public boolean shouldIgnoreSticks() { return ignoreSticks; }
    public boolean isDurabilityBased() { return durabilityBased; }
    public Material getOutputMaterial() { return outputMaterial; }
    public int getMaxDurability() { return maxDurability; }
    public double getSmeltTimeMultiplier() { return smeltTimeMultiplier; }
    public Map<Integer, DurabilityRange> getDurabilityRanges() { return durabilityRanges; }
}