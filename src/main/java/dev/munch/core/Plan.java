package dev.munch.core;

import java.util.Set;

public final class Plan {
    private final int width;
    private final int height;
    private final int[] removedAt;
    private final int stages;
    private final Set<Object> targets;
    private final boolean[] cover;

    public Plan(int width, int height, int[] removedAt, int stages) {
        this(width, height, removedAt, stages, null, null);
    }

    public Plan(int width, int height, int[] removedAt, int stages, Set<Object> targets, boolean[] cover) {
        this.width = width;
        this.height = height;
        this.removedAt = removedAt;
        this.stages = stages;
        this.targets = targets;
        this.cover = cover;
    }

    public boolean targets(Object sprite) {
        return targets == null || targets.contains(sprite);
    }

    public boolean covered(int x, int y, int spriteWidth, int spriteHeight) {
        return cover != null && cover[cell(x, y, spriteWidth, spriteHeight)];
    }

    private int cell(int x, int y, int spriteWidth, int spriteHeight) {
        int gx = Math.min(width - 1, x * width / spriteWidth);
        int gy = Math.min(height - 1, y * height / spriteHeight);
        return gy * width + gx;
    }

    public int stages() {
        return stages;
    }

    public boolean removed(int x, int y, int spriteWidth, int spriteHeight, int stage) {
        if (stage <= 0) {
            return false;
        }
        int at = removedAt[cell(x, y, spriteWidth, spriteHeight)];
        return at > 0 && at <= stage;
    }
}
