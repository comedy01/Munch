package dev.munch.client;

import dev.munch.config.MunchConfig;
import dev.munch.core.Bites;
import dev.munch.core.Chews;
import dev.munch.core.Drain;
import dev.munch.core.Plan;
import dev.munch.mixin.SpriteContentsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Eating {
    private static final Logger LOGGER = LogManager.getLogger("munch");
    private static final int CACHE = 256;
    private static final int COLOR_DIFFERENCE = 40;

    private static final Map<PlanKey, Plan> PLANS = lru();
    private static final Map<MeshKey, Object> MESHES = lru();

    private static boolean failed;
    private static int cuts;

    private final LivingEntity entity;
    private final ItemStack item;
    private final int duration;
    private final boolean drink;
    private final ItemStack remainder;
    private boolean drain;

    private Eating(LivingEntity entity, ItemStack item, int duration, boolean drink, ItemStack remainder) {
        this.entity = entity;
        this.item = item;
        this.duration = duration;
        this.drink = drink;
        this.remainder = remainder;
        this.drain = drink || !remainder.isEmpty();
    }

    public static int cuts() {
        return cuts;
    }

    public static boolean edible(ItemStack stack) {
        return Use.kind(stack) != Use.NONE;
    }

    public static Eating start(ItemStack item, ItemDisplayContext context, Object owner) {
        if (failed || item.isEmpty()) {
            return null;
        }
        MunchConfig config = MunchClient.config();
        if (!config.enabled() || !(owner instanceof LivingEntity entity) || !entity.isUsingItem()) {
            return null;
        }
        if (!handContext(context) || !usedHandShown(entity, context)) {
            return null;
        }
        if (entity != Minecraft.getInstance().player && !config.others()) {
            return null;
        }
        if (!Use.same(item, entity.getUseItem())) {
            return null;
        }
        int kind = Use.kind(item);
        if (kind == Use.NONE) {
            return null;
        }
        boolean drink = kind == Use.DRINK;
        ItemStack remainder = Use.remainder(item);
        boolean drain = drink || !remainder.isEmpty();
        if (drain ? !config.drinking() : !config.eating()) {
            return null;
        }
        int duration = Use.duration(item, entity);
        if (duration <= 0) {
            return null;
        }
        return new Eating(entity, item, duration, drink, remainder);
    }

    public static void fail(RuntimeException e) {
        failed = true;
        LOGGER.error("Munch could not change the eaten item and turned itself off for this session", e);
    }

    public ItemStack remainder() {
        return drain ? remainder : ItemStack.EMPTY;
    }

    public Plan plan(List<TextureAtlasSprite> sprites, List<Boolean> tinted, List<TextureAtlasSprite> leftover) {
        if (sprites.isEmpty()) {
            return null;
        }
        if (drain && leftover.isEmpty() && !tinted.contains(Boolean.TRUE)) {
            if (drink || !MunchClient.config().eating()) {
                return null;
            }
            drain = false;
        }
        long seed = BuiltInRegistries.ITEM.getKey(item.getItem()).hashCode() * 0x9E3779B97F4A7C15L;
        int chews = Math.max(1, Chews.total(duration));
        PlanKey key = new PlanKey(List.copyOf(sprites), List.copyOf(leftover), drain, drain ? 0 : chews, seed);
        Plan plan = PLANS.get(key);
        if (plan == null) {
            plan = drain ? drainPlan(sprites, tinted, leftover) : bitePlan(sprites, chews, seed);
            PLANS.put(key, plan);
        }
        return plan;
    }

    public boolean draining() {
        return drain;
    }

    public int stage(Plan plan) {
        if (drain) {
            return Drain.stage(plan.stages(), (float) entity.getTicksUsingItem() / duration);
        }
        return Chews.done(duration, entity.getUseItemRemainingTicks());
    }

    @SuppressWarnings("unchecked")
    public <T> T cut(Object key, Supplier<List<Quad>> quads, Function<List<Quad>, T> write, T unchanged,
                     Plan plan, int stage, boolean inside) {
        MeshKey meshKey = new MeshKey(key, plan, stage, inside);
        Object cached = MESHES.get(meshKey);
        if (cached == null) {
            List<Quad> result = Cutter.cut(quads.get(), plan, stage, inside);
            cached = result != null ? write.apply(result) : inside ? write.apply(List.of()) : unchanged;
            MESHES.put(meshKey, cached);
        }
        if (cached != unchanged) {
            cuts++;
        }
        return (T) cached;
    }

    private static Plan bitePlan(List<TextureAtlasSprite> sprites, int chews, long seed) {
        Grid grid = Grid.of(sprites);
        boolean[] opaque = new boolean[grid.width * grid.height];
        for (int i = 0; i < opaque.length; i++) {
            opaque[i] = grid.color(sprites, i % grid.width, i / grid.width, null) != 0;
        }
        return new Plan(grid.width, grid.height, Bites.plan(opaque, grid.width, grid.height, chews, seed), chews);
    }

    private static Plan drainPlan(List<TextureAtlasSprite> sprites, List<Boolean> tinted, List<TextureAtlasSprite> leftover) {
        Grid grid = Grid.of(sprites);
        boolean[] liquid = new boolean[grid.width * grid.height];
        boolean[] cover = leftover.isEmpty() ? null : new boolean[grid.width * grid.height];
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                int color = grid.color(sprites, x, y, null);
                int empty = cover == null ? 0 : grid.color(leftover, x, y, null);
                if (cover != null) {
                    cover[y * grid.width + x] = empty != 0;
                }
                if (color == 0) {
                    continue;
                }
                boolean wet;
                if (cover != null) {
                    wet = empty == 0 || difference(color, empty) > COLOR_DIFFERENCE;
                } else {
                    wet = grid.color(sprites, x, y, tinted) != 0;
                }
                liquid[y * grid.width + x] = wet;
            }
        }
        Set<Object> targets = null;
        if (cover == null) {
            targets = new HashSet<>();
            for (int i = 0; i < sprites.size(); i++) {
                if (tinted.get(i)) {
                    targets.add(sprites.get(i));
                }
            }
        }
        int[] removedAt = Drain.plan(liquid, grid.width, grid.height);
        return new Plan(grid.width, grid.height, removedAt, Drain.stages(removedAt), targets, cover);
    }

    private static int difference(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF))
                + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                + Math.abs((a & 0xFF) - (b & 0xFF));
    }

    private static boolean handContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    private static boolean usedHandShown(LivingEntity entity, ItemDisplayContext context) {
        HumanoidArm arm = entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? entity.getMainArm() : entity.getMainArm().getOpposite();
        boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        return left == (arm == HumanoidArm.LEFT);
    }

    private static <K, V> Map<K, V> lru() {
        return new LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > CACHE;
            }
        };
    }

    private record PlanKey(List<TextureAtlasSprite> sprites, List<TextureAtlasSprite> leftover, boolean drain,
                           int chews, long seed) {
    }

    private static final class MeshKey {
        private final Object key;
        private final Plan plan;
        private final int stage;
        private final boolean inside;

        MeshKey(Object key, Plan plan, int stage, boolean inside) {
            this.key = key;
            this.plan = plan;
            this.stage = stage;
            this.inside = inside;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MeshKey that && that.key.equals(key) && that.plan == plan && that.stage == stage
                    && that.inside == inside;
        }

        @Override
        public int hashCode() {
            return ((key.hashCode() * 31 + System.identityHashCode(plan)) * 31 + stage) * 2 + (inside ? 1 : 0);
        }
    }

    public static final class Same {
        private final Object first;
        private final int size;

        public Same(List<?> list) {
            this.first = list.isEmpty() ? null : list.get(0);
            this.size = list.size();
        }

        public Same(Object only) {
            this.first = only;
            this.size = -1;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Same that && that.first == first && that.size == size;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(first) * 31 + size;
        }
    }

    private static final class Grid {
        final int width;
        final int height;

        private Grid(int width, int height) {
            this.width = width;
            this.height = height;
        }

        static Grid of(List<TextureAtlasSprite> sprites) {
            int width = 1;
            int height = 1;
            for (TextureAtlasSprite sprite : sprites) {
                width = Math.max(width, sprite.contents().width());
                height = Math.max(height, sprite.contents().height());
            }
            return new Grid(width, height);
        }

        int color(List<TextureAtlasSprite> sprites, int x, int y, List<Boolean> only) {
            int color = 0;
            for (int i = 0; i < sprites.size(); i++) {
                if (only != null && !only.get(i)) {
                    continue;
                }
                int pixel = pixel(sprites.get(i).contents(), x, y);
                if ((pixel >>> 24) != 0) {
                    color = pixel;
                }
            }
            return color;
        }

        private int pixel(SpriteContents contents, int x, int y) {
            int sx = Math.min(contents.width() - 1, x * contents.width() / width);
            int sy = Math.min(contents.height() - 1, y * contents.height() / height);
            return Pixels.argb(((SpriteContentsAccessor) contents).munch$originalImage(), sx, sy);
        }
    }
}
