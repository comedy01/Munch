package dev.munch.core;

public final class Drain {
    private Drain() {
    }

    public static int[] plan(boolean[] liquid, int width, int height) {
        int[] removedAt = new int[width * height];
        int level = 0;
        for (int y = 0; y < height; y++) {
            boolean any = false;
            for (int x = 0; x < width; x++) {
                if (liquid[y * width + x]) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                continue;
            }
            level++;
            for (int x = 0; x < width; x++) {
                if (liquid[y * width + x]) {
                    removedAt[y * width + x] = level;
                }
            }
        }
        return removedAt;
    }

    public static int stages(int[] removedAt) {
        int max = 0;
        for (int b : removedAt) {
            max = Math.max(max, b);
        }
        return max;
    }

    public static int stage(int stages, float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        return Math.min(stages, (int) (clamped * stages));
    }
}
