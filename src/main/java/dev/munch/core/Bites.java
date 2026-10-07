package dev.munch.core;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public final class Bites {
    private static final double DIR_X = Math.sqrt(0.5);
    private static final double DIR_Y = -Math.sqrt(0.5);
    private static final double LAST_PIECE = 0.1;

    private Bites() {
    }

    public static int[] plan(boolean[] opaque, int width, int height, int stages, long seed) {
        int[] removedAt = new int[width * height];
        int total = count(opaque);
        if (total == 0 || stages <= 0) {
            return removedAt;
        }

        int[] original = label(opaque, width, height);
        int[] originalSizes = sizes(original);
        boolean[] left = opaque.clone();
        int remaining = total;
        int keep = Math.max(Math.min(4, total), (int) Math.ceil(total * LAST_PIECE));
        double radius = radius(total, width, height);
        double[] grip = grip(opaque, width, total);
        SplittableRandom random = new SplittableRandom(seed);
        int bite = 0;

        for (int stage = 1; stage <= stages; stage++) {
            int target = Math.min(total - keep, (int) Math.round(total * eaten(stage, stages)));
            while (total - remaining < target) {
                int removed = bite(left, removedAt, width, height, radius, grip, random, seed, bite++, stage,
                        target - (total - remaining));
                if (removed == 0) {
                    break;
                }
                remaining -= removed;
            }
            remaining += fillNotches(left, removedAt, width, height, stage);
            if (remaining > keep) {
                remaining -= trim(opaque, left, removedAt, width, height, stage, remaining - keep);
            }
            remaining -= dropIslands(left, removedAt, width, height, stage, original, originalSizes);
        }
        return removedAt;
    }

    static double eaten(int stage, int stages) {
        double left = 1.0 - (double) stage / stages;
        return (1.0 - LAST_PIECE) * (1.0 - Math.pow(left, 1.5));
    }

    static double radius(int total, int width, int height) {
        double scaled = Math.sqrt(total) * 0.3;
        double cap = Math.min(width, height) * 0.35;
        return Math.max(1.5, Math.min(scaled, Math.max(1.5, cap)));
    }

    static double[] grip(boolean[] opaque, int width, int total) {
        double sumX = 0;
        double sumY = 0;
        int corner = -1;
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < opaque.length; i++) {
            if (!opaque[i]) {
                continue;
            }
            double x = i % width + 0.5;
            double y = i / width + 0.5;
            sumX += x;
            sumY += y;
            double score = x * DIR_X + y * DIR_Y;
            if (score < lowest) {
                lowest = score;
                corner = i;
            }
        }
        double cx = sumX / total;
        double cy = sumY / total;
        double gx = corner % width + 0.5;
        double gy = corner / width + 0.5;
        return new double[]{gx + (cx - gx) * 0.35, gy + (cy - gy) * 0.35};
    }

    private static int bite(boolean[] left, int[] removedAt, int width, int height, double radius, double[] grip,
                            SplittableRandom random, long seed, int bite, int stage, int budget) {
        int front = -1;
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < left.length; i++) {
            if (!left[i]) {
                continue;
            }
            double dx = i % width + 0.5 - grip[0];
            double dy = i / width + 0.5 - grip[1];
            double score = dx * dx + dy * dy + (dx * DIR_X + dy * DIR_Y) * 0.5;
            if (score > best) {
                best = score;
                front = i;
            }
        }
        if (front < 0 || budget <= 0) {
            return 0;
        }

        double fx = front % width + 0.5;
        double fy = front / width + 0.5;
        double ax = fx - grip[0];
        double ay = fy - grip[1];
        double length = Math.sqrt(ax * ax + ay * ay);
        if (length < 1.0E-6) {
            ax = DIR_X;
            ay = DIR_Y;
        } else {
            ax /= length;
            ay /= length;
        }
        double sideways = (random.nextDouble() - 0.5) * radius * 1.2;
        double centerX = fx + ax * radius * 0.45 - ay * sideways;
        double centerY = fy + ay * radius * 0.45 + ax * sideways;

        List<double[]> inside = new ArrayList<>();
        int minX = Math.max(0, (int) Math.floor(centerX - radius - 1));
        int maxX = Math.min(width - 1, (int) Math.ceil(centerX + radius + 1));
        int minY = Math.max(0, (int) Math.floor(centerY - radius - 1));
        int maxY = Math.min(height - 1, (int) Math.ceil(centerY + radius + 1));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int i = y * width + x;
                if (!left[i]) {
                    continue;
                }
                double dx = x + 0.5 - centerX;
                double dy = y + 0.5 - centerY;
                double distance = Math.sqrt(dx * dx + dy * dy);
                double edge = radius + (noise(seed, bite, x, y) - 0.5) * 0.9;
                if (distance < edge) {
                    inside.add(new double[]{distance, i});
                }
            }
        }
        if (inside.isEmpty()) {
            inside.add(new double[]{0, front});
        }
        inside.sort((a, b) -> Double.compare(a[0], b[0]));
        int removed = Math.min(budget, inside.size());
        for (int k = 0; k < removed; k++) {
            int i = (int) inside.get(k)[1];
            left[i] = false;
            removedAt[i] = stage;
        }
        return removed;
    }

    private static int trim(boolean[] opaque, boolean[] left, int[] removedAt, int width, int height, int stage,
                            int budget) {
        int trimmed = 0;
        boolean changed = true;
        while (changed && trimmed < budget) {
            changed = false;
            for (int i = 0; i < left.length && trimmed < budget; i++) {
                if (!left[i]) {
                    continue;
                }
                int neighbours = neighbours(left, i, width, height);
                if (neighbours <= 1 && neighbours < neighbours(opaque, i, width, height)) {
                    left[i] = false;
                    removedAt[i] = stage;
                    trimmed++;
                    changed = true;
                }
            }
        }
        return trimmed;
    }

    private static int fillNotches(boolean[] left, int[] removedAt, int width, int height, int stage) {
        int filled = 0;
        for (int i = 0; i < left.length; i++) {
            if (removedAt[i] == stage && neighbours(left, i, width, height) >= 3) {
                left[i] = true;
                removedAt[i] = 0;
                filled++;
            }
        }
        return filled;
    }

    private static int neighbours(boolean[] cells, int i, int width, int height) {
        int x = i % width;
        int y = i / width;
        return (x > 0 && cells[i - 1] ? 1 : 0) + (x < width - 1 && cells[i + 1] ? 1 : 0)
                + (y > 0 && cells[i - width] ? 1 : 0) + (y < height - 1 && cells[i + width] ? 1 : 0);
    }

    private static int dropIslands(boolean[] left, int[] removedAt, int width, int height, int stage,
                                   int[] original, int[] originalSizes) {
        if (count(left) == 0) {
            return 0;
        }
        int[] now = label(left, width, height);
        int[] nowSizes = sizes(now);
        int largest = 1;
        for (int c = 1; c < nowSizes.length; c++) {
            if (nowSizes[c] > nowSizes[largest]) {
                largest = c;
            }
        }
        boolean[] intact = new boolean[nowSizes.length];
        java.util.Arrays.fill(intact, true);
        for (int i = 0; i < left.length; i++) {
            int c = now[i];
            if (c > 0 && nowSizes[c] != originalSizes[original[i]]) {
                intact[c] = false;
            }
        }
        int dropped = 0;
        for (int i = 0; i < left.length; i++) {
            int c = now[i];
            if (c > 0 && c != largest && !intact[c]) {
                left[i] = false;
                removedAt[i] = stage;
                dropped++;
            }
        }
        return dropped;
    }

    private static int[] label(boolean[] cells, int width, int height) {
        int[] labels = new int[cells.length];
        int next = 0;
        int[] stack = new int[cells.length];
        for (int start = 0; start < cells.length; start++) {
            if (!cells[start] || labels[start] != 0) {
                continue;
            }
            next++;
            int top = 0;
            stack[top++] = start;
            labels[start] = next;
            while (top > 0) {
                int i = stack[--top];
                int x = i % width;
                int y = i / width;
                int[] around = {x > 0 ? i - 1 : -1, x < width - 1 ? i + 1 : -1, y > 0 ? i - width : -1,
                        y < height - 1 ? i + width : -1};
                for (int n : around) {
                    if (n >= 0 && cells[n] && labels[n] == 0) {
                        labels[n] = next;
                        stack[top++] = n;
                    }
                }
            }
        }
        return labels;
    }

    private static int[] sizes(int[] labels) {
        int max = 0;
        for (int label : labels) {
            max = Math.max(max, label);
        }
        int[] sizes = new int[max + 1];
        for (int label : labels) {
            if (label > 0) {
                sizes[label]++;
            }
        }
        return sizes;
    }

    private static int count(boolean[] cells) {
        int n = 0;
        for (boolean cell : cells) {
            if (cell) {
                n++;
            }
        }
        return n;
    }

    private static double noise(long seed, int bite, int x, int y) {
        long h = seed ^ (bite * 0x9E3779B97F4A7C15L) ^ (x * 0xC2B2AE3D27D4EB4FL) ^ (y * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
        h = (h ^ (h >>> 33)) * 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
