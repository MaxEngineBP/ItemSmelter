package com.itemsmelter.models;

import java.util.Random;

public class DurabilityRange {

    private static final Random RANDOM = new Random();
    private final int min;
    private final int max;

    public DurabilityRange(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public int getRandom() {
        if (min == max) {
            return min;
        }
        return RANDOM.nextInt(max - min + 1) + min;
    }

    public int getMin() { return min; }
    public int getMax() { return max; }
}