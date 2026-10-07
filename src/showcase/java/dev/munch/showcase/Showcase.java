package dev.munch.showcase;

import com.mojang.datafixers.util.Pair;
import dev.munch.client.MunchClient;
import dev.munch.client.gui.MunchSettingsScreen;
import dev.munch.config.MunchConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class Showcase {
    private static final String WORLD = "munch-showcase";
    private static final long SETTLE_NANOS = 3_000_000_000L;
    private static final int GLIDE = 24;
    private static final int EDGE = 28;
    private static final String POTION = "minecraft:potion[minecraft:potion_contents={potion:\"minecraft:healing\"}]";
    private static final String[] ARROW = {
            "B",
            "BB",
            "BWB",
            "BWWB",
            "BWWWB",
            "BWWWWB",
            "BWWWWWB",
            "BWWWWWWB",
            "BWWWWWWWB",
            "BWWWWWWWWB",
            "BWWWWWWWWWB",
            "BWWWWWWBBBBB",
            "BWWWBWWB",
            "BWWB BWWB",
            "BWB  BWWB",
            "BB    BWWB",
            "B     BWWB",
            "       BWWB",
            "        BB"};
    private static Showcase instance;

    private final Path out;
    private final Recorder recorder;
    private final long seed;
    private final List<Action> actions = new ArrayList<>();
    private final AtomicReference<BlockPos> site = new AtomicReference<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int frame;
    private long actionStart;
    private Path pendingStill;
    private boolean cursorShown;
    private boolean showHand;
    private boolean eating;
    private double cursorX;
    private double cursorY;

    private interface Action {
        boolean run(int frame);
    }

    private record Stop(int frame, Supplier<double[]> where) {
    }

    private Showcase(Path out, String ffmpeg, long seed) {
        this.out = out;
        this.recorder = new Recorder(ffmpeg);
        this.seed = seed;
    }

    public static void start() {
        String ffmpeg = System.getProperty("munch.ffmpeg", "ffmpeg");
        long seed = Long.parseLong(System.getProperty("munch.seed", "12345"));
        instance = new Showcase(Path.of(System.getProperty("munch.showcase")), ffmpeg, seed);
    }

    public static boolean hideHand() {
        return instance != null && instance.started && !instance.showHand;
    }

    public static boolean hideHud() {
        return instance != null && instance.started;
    }

    public static void frame() {
        if (instance != null) {
            instance.onFrame();
        }
    }

    public static void drawCursor(GuiGraphicsExtractor graphics) {
        if (instance == null || !instance.started || !instance.cursorShown) {
            return;
        }
        float cell = 2.0F / (float) Minecraft.getInstance().getWindow().getGuiScale();
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) instance.cursorX, (float) instance.cursorY);
        graphics.pose().scale(cell, cell);
        for (int row = 0; row < ARROW.length; row++) {
            String line = ARROW[row];
            int x = 0;
            while (x < line.length()) {
                char c = line.charAt(x);
                int end = x;
                while (end < line.length() && line.charAt(end) == c) {
                    end++;
                }
                if (c != ' ') {
                    graphics.fill(x, row, end, row + 1, c == 'B' ? 0xFF000000 : 0xFFFFFFFF);
                }
                x = end;
            }
        }
        graphics.pose().popMatrix();
    }

    private void onFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 120) {
                started = true;
                setUpOptions(mc);
                plan(mc);
                log("creating world with seed " + seed);
                deleteWorld(mc);
                Worlds.create(mc, WORLD, false, seed);
            }
            return;
        }
        Clock.step();
        mc.gui.toastManager().clear();
        try {
            while (index < actions.size()) {
                if (frame == 0) {
                    actionStart = System.nanoTime();
                }
                if (!actions.get(index).run(frame++)) {
                    if (cursorShown && Screens.current(mc) != null) {
                        double scale = mc.getWindow().getGuiScale();
                        Ui.moveMouse(mc, cursorX * scale, cursorY * scale);
                    }
                    return;
                }
                index++;
                frame = 0;
            }
        } catch (Throwable e) {
            log("FAILED: " + e);
            e.printStackTrace();
        }
        finish(mc);
    }

    private void finish(Minecraft mc) {
        finished = true;
        mc.options.keyUse.setDown(false);
        Clock.release();
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(90_000L);
            } catch (InterruptedException e) {
                return;
            }
            log("the game did not close, forcing it");
            Runtime.getRuntime().halt(1);
        }, "munch-showcase-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        MunchClient.config().resetToDefaults();
        MunchClient.saveConfig();
        log("showcase done");
        mc.stop();
    }

    private static void setUpOptions(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.renderDistance().set(12);
        mc.options.bobView().set(false);
        // Keep vanilla's eating crumbs: food without them looks fake.
        mc.options.particles().set(ParticleStatus.ALL);
        mc.options.gamma().set(Double.parseDouble(System.getProperty("munch.gamma", "0.5")));
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.guiScale().set(3);
        mc.resizeGui();
    }

    private void once(Runnable action) {
        actions.add(f -> {
            action.run();
            return true;
        });
    }

    private void until(BooleanSupplier ready) {
        actions.add(f -> ready.getAsBoolean());
    }

    private void holdFor(int frames, Runnable hold) {
        actions.add(f -> {
            hold.run();
            return f >= frames;
        });
    }

    private void settle(Minecraft mc, Runnable hold) {
        actions.add(f -> {
            hold.run();
            return f >= 90 && System.nanoTime() - actionStart > SETTLE_NANOS
                    && (mc.levelRenderer.hasRenderedAllSections() || System.nanoTime() - actionStart > 20 * SETTLE_NANOS);
        });
    }

    private void record(String name, int frames, IntConsumer script) {
        actions.add(f -> {
            if (f == 0) {
                log("recording " + name);
                recorder.start(out.resolve("clips").resolve(name + ".mp4"));
            } else {
                Path still = pendingStill;
                pendingStill = null;
                recorder.capture(still);
            }
            if (f < frames) {
                hungry(Minecraft.getInstance());
                script.accept(f);
                return false;
            }
            return true;
        });
        until(recorder::drained);
        once(recorder::stop);
    }

    private void shot(String name) {
        pendingStill = out.resolve("stills").resolve(name + ".png");
    }

    static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void cmd(Minecraft mc, String format, Object... args) {
        run(mc, String.format(Locale.ROOT, format, args));
    }

    private static void face(Minecraft mc, double yaw, double pitch) {
        LocalPlayer player = mc.player;
        player.setYRot((float) yaw);
        player.setXRot((float) pitch);
        player.yRotO = (float) yaw;
        player.xRotO = (float) pitch;
        player.setYHeadRot((float) yaw);
        player.yHeadRotO = (float) yaw;
        player.yBodyRot = (float) yaw;
        player.yBodyRotO = (float) yaw;
    }

    private static void lookAt(Minecraft mc, Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        face(mc, Math.toDegrees(Math.atan2(-dx, dz)), -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private static void place(Minecraft mc, Vec3 feet) {
        LocalPlayer player = mc.player;
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(feet.x, feet.y, feet.z);
        player.xo = feet.x;
        player.yo = feet.y;
        player.zo = feet.z;
        player.xOld = feet.x;
        player.yOld = feet.y;
        player.zOld = feet.z;
    }

    private void cursor(int f, Stop... stops) {
        double[] at = stops[0].where().get();
        for (int i = 1; i < stops.length; i++) {
            Stop stop = stops[i];
            if (f >= stop.frame()) {
                at = stop.where().get();
            } else if (f >= stop.frame() - GLIDE) {
                double[] to = stop.where().get();
                double s = smooth((f - (stop.frame() - GLIDE)) / (double) GLIDE);
                at = new double[] {lerp(at[0], to[0], s), lerp(at[1], to[1], s)};
                break;
            } else {
                break;
            }
        }
        cursorShown = true;
        cursorX = at[0];
        cursorY = at[1];
    }

    private static void select(Minecraft mc, int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
    }

    /** Survival with a hungry player, so food is really eaten and bottles, bowls and buckets are left behind. */
    private static void hungry(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.getFoodData().setFoodLevel(6);
                player.getFoodData().setSaturation(0.0F);
                player.setHealth(player.getMaxHealth());
            }
        });
    }

    /** Eats hotbar slot i from frame starts[i]: holds use until that item is used up. */
    private void meals(Minecraft mc, int f, int... starts) {
        for (int i = 0; i < starts.length; i++) {
            if (f == starts[i]) {
                select(mc, i);
                mc.options.keyUse.setDown(true);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                eating = true;
                return;
            }
        }
        if (eating && !mc.player.isUsingItem()) {
            eating = false;
            mc.options.keyUse.setDown(false);
        }
    }

    private void stopEating(Minecraft mc) {
        eating = false;
        mc.options.keyUse.setDown(false);
        if (mc.player.isUsingItem()) {
            mc.gameMode.releaseUsingItem(mc.player);
        }
    }

    private static boolean wanted(String scene) {
        String only = System.getProperty("munch.scenes");
        return only == null || List.of(only.split(",")).contains(scene);
    }

    private Vec3 at(double dx, double dy, double dz) {
        BlockPos c = site.get();
        return new Vec3(c.getX() + dx + 0.5, c.getY() + dy, c.getZ() + dz + 0.5);
    }

    private static int height(ServerLevel level, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
    }

    private void locate(Minecraft mc) {
        once(() -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerLevel level = server.overworld();
                ChunkGenerator generator = level.getChunkSource().getGenerator();
                RandomState noise = level.getChunkSource().randomState();
                Pair<BlockPos, Holder<Biome>> pair = level.findClosestBiome3d(h -> h.is(Biomes.PLAINS),
                        BlockPos.ZERO, 6400, 32, 64);
                BlockPos found = pair == null ? BlockPos.ZERO : pair.getFirst();
                BlockPos best = null;
                int bestScore = Integer.MAX_VALUE;
                for (int ox = -1536; ox <= 1536; ox += 96) {
                    for (int oz = -1536; oz <= 1536; oz += 96) {
                        int cx = found.getX() + ox;
                        int cz = found.getZ() + oz;
                        int centre = generator.getBaseHeight(cx, cz, Heightmap.Types.WORLD_SURFACE_WG, level, noise);
                        if (!level.getBiome(new BlockPos(cx, centre, cz)).is(Biomes.PLAINS)) {
                            continue;
                        }
                        List<Integer> heights = new ArrayList<>();
                        int wet = 0;
                        for (int dx = -40; dx <= 40; dx += 8) {
                            for (int dz = -40; dz <= 40; dz += 8) {
                                int top = generator.getBaseHeight(cx + dx, cz + dz, Heightmap.Types.WORLD_SURFACE_WG,
                                        level, noise);
                                int floor = generator.getBaseHeight(cx + dx, cz + dz, Heightmap.Types.OCEAN_FLOOR_WG,
                                        level, noise);
                                heights.add(top);
                                if (top != floor) {
                                    wet++;
                                }
                            }
                        }
                        int usual = mode(heights);
                        int score = wet * 3;
                        for (int h : heights) {
                            if (Math.abs(h - usual) > 1) {
                                score++;
                            }
                        }
                        if (score < bestScore) {
                            bestScore = score;
                            best = new BlockPos(cx, centre, cz);
                        }
                    }
                }
                site.set(best == null ? found : best);
                log("plains at " + site.get().toShortString() + ", " + bestScore + " uneven samples");
            });
        });
        until(() -> site.get() != null);
    }

    private static int mode(List<Integer> values) {
        Map<Integer, Integer> counts = new HashMap<>();
        int best = values.get(0);
        for (int value : values) {
            int count = counts.merge(value, 1, Integer::sum);
            if (count > counts.get(best)) {
                best = value;
            }
        }
        return best;
    }

    private void level(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        int ground = server.submit(() -> {
            ServerLevel level = server.overworld();
            BlockPos c = site.get();
            List<Integer> heights = new ArrayList<>();
            for (int dx = -EDGE; dx <= EDGE; dx += 2) {
                for (int dz = -EDGE; dz <= EDGE; dz += 2) {
                    heights.add(height(level, c.getX() + dx, c.getZ() + dz));
                }
            }
            return mode(heights);
        }).join();
        BlockPos c = site.get();
        site.set(new BlockPos(c.getX(), ground + 1, c.getZ()));
        log("meadow at y " + (ground + 1));
    }

    private static void fill(Minecraft mc, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        int layers = Math.max(1, 32768 / ((Math.abs(x2 - x1) + 1) * (Math.abs(z2 - z1) + 1)));
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y += layers) {
            cmd(mc, "fill %d %d %d %d %d %d %s", x1, y, z1, x2, Math.min(y + layers - 1, Math.max(y1, y2)), z2, block);
        }
    }

    private void buildMeadow(Minecraft mc) {
        BlockPos c = site.get();
        int x = c.getX();
        int z = c.getZ();
        int ground = c.getY() - 1;
        fill(mc, x - EDGE, ground + 1, z - EDGE, x + EDGE, ground + 40, z + EDGE, "minecraft:air");
        fill(mc, x - EDGE, ground - 6, z - EDGE, x + EDGE, ground - 1, z + EDGE, "minecraft:dirt");
        fill(mc, x - EDGE, ground, z - EDGE, x + EDGE, ground, z + EDGE, "minecraft:grass_block");
        String[] kinds = {"oak", "birch", "fancy_oak"};
        int[][] trees = {{-9, -19}, {7, -21}, {-19, -15}, {18, -17}, {-3, -25}, {22, -24}, {-24, -23}, {13, -13},
                {-14, -12}, {25, -9}, {-26, -6}, {-12, 14}, {10, 16}, {-22, 11}, {21, 13}, {-4, 19}, {16, 22}};
        for (int i = 0; i < trees.length; i++) {
            cmd(mc, "place feature minecraft:%s %d %d %d", kinds[i % kinds.length], x + trees[i][0], ground + 1,
                    z + trees[i][1]);
        }
        Random random = new Random(seed);
        String[] flowers = {"dandelion", "poppy", "oxeye_daisy", "cornflower", "azure_bluet", "lily_of_the_valley"};
        for (int dx = -EDGE + 1; dx <= EDGE - 1; dx++) {
            for (int dz = -EDGE + 1; dz <= EDGE - 1; dz++) {
                // Keep the spot the mobs stand on clear, so grass does not hide their feet.
                if (Math.abs(dx) <= 3 && dz >= 0 && dz <= 5) {
                    continue;
                }
                double roll = random.nextDouble();
                String block;
                if (roll < 0.16) {
                    block = "short_grass";
                } else if (roll < 0.20) {
                    block = flowers[random.nextInt(flowers.length)];
                } else if (roll < 0.21) {
                    block = "tall_grass";
                } else {
                    continue;
                }
                cmd(mc, "setblock %d %d %d minecraft:%s keep", x + dx, ground + 1, z + dz, block);
            }
        }
        run(mc, "kill @e[type=!minecraft:player]");
    }

    private void summon(Minecraft mc, String type, Vec3 at, double yaw, String extra) {
        cmd(mc, "summon minecraft:%s %.3f %.3f %.3f {Silent:1b,PersistenceRequired:1b,NoAI:1b,Tags:[\"scene\",\"eater\"],"
                + "Rotation:[%.1ff,0f]%s}", type, at.x, at.y, at.z, yaw, extra);
    }

    private static String holding(String item, int count) {
        return ",equipment:{mainhand:{id:\"minecraft:" + item + "\",count:" + count + "}}";
    }

    /** Mobs tagged "eater" start eating again whenever they finish. */
    private static void feed(Minecraft mc, int f, int... starts) {
        if (f % 3 != 0) {
            return;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            int i = 0;
            for (Entity e : server.overworld().getAllEntities()) {
                if (e instanceof LivingEntity mob && e.entityTags().contains("eater")) {
                    int start = i < starts.length ? starts[i] : 0;
                    if (f >= start && !mob.isUsingItem() && !mob.getMainHandItem().isEmpty()) {
                        mob.startUsingItem(InteractionHand.MAIN_HAND);
                    }
                    i++;
                }
            }
        });
    }

    private static void kit(Minecraft mc, String... items) {
        run(mc, "clear @p");
        for (int i = 0; i < items.length; i++) {
            cmd(mc, "item replace entity @p hotbar.%d with %s", i, items[i].contains(":") ? items[i]
                    : "minecraft:" + items[i]);
        }
    }

    private void scene(Minecraft mc, MunchConfig config, Vec3 feet, Runnable setup, Runnable hold, String... kit) {
        once(() -> {
            Screens.open(mc, null);
            stopEating(mc);
            config.resetToDefaults();
            cursorShown = false;
            showHand = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            run(mc, "gamemode survival @p");
            run(mc, "effect clear @p");
            cmd(mc, "tp @p %.3f %.3f %.3f", feet.x, feet.y, feet.z);
            run(mc, "kill @e[type=!minecraft:player]");
            kit(mc, kit);
            select(mc, 0);
        });
        holdFor(80, () -> {
            place(mc, feet);
            hold.run();
        });
        once(() -> run(mc, "kill @e[type=minecraft:item]"));
        holdFor(20, () -> {
            place(mc, feet);
            hold.run();
        });
        once(setup);
        settle(mc, () -> {
            place(mc, feet);
            hungry(mc);
            hold.run();
        });
    }

    private void plan(Minecraft mc) {
        MunchConfig config = MunchClient.config();

        until(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null);
        once(() -> {
            log("world loaded");
            Clock.fix();
            config.resetToDefaults();
            run(mc, "time set 2500");
            run(mc, "weather clear 1000000");
            run(mc, "gamerule advance_time false");
            run(mc, "gamerule spawn_mobs false");
            run(mc, "gamerule random_tick_speed 0");
            run(mc, "difficulty easy");
        });
        locate(mc);
        once(() -> {
            BlockPos c = site.get();
            cmd(mc, "forceload add %d %d %d %d", c.getX() - 40, c.getZ() - 40, c.getX() + 40, c.getZ() + 40);
            cmd(mc, "tp @p %d %d %d", c.getX(), c.getY() + 45, c.getZ());
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        });
        settle(mc, () -> {
        });
        once(() -> level(mc));
        once(() -> buildMeadow(mc));
        holdFor(60, () -> {
        });
        once(() -> run(mc, "time set " + System.getProperty("munch.time", "4000")));
        once(() -> film(mc, config));
    }

    private void film(Minecraft mc, MunchConfig config) {
        if (wanted("intro")) {
            Vec3 start = at(0, 0, 9);
            Vec3 end = at(0, 0, 2);
            scene(mc, config, start, () -> showHand = true, () -> face(mc, 180, 4), "bread");
            record("01-intro", 330, f -> {
                place(mc, lerp(start, end, smooth(f / 330.0) * 0.85 + f / 330.0 * 0.15));
                face(mc, 180 - 3 * Math.sin(f / 60.0), 4 + 2 * Math.sin(f / 50.0));
                if (f == 240) {
                    shot("intro");
                }
            });
        }

        if (wanted("bites")) {
            Vec3 feet = at(0, 0, 2);
            scene(mc, config, feet, () -> showHand = true, () -> face(mc, 180, 6), "bread", "apple");
            record("02-bites", 300, f -> {
                place(mc, feet);
                face(mc, 180 + 2 * Math.sin(f / 70.0), 6);
                meals(mc, f, 20, 150);
                if (f == 85) {
                    shot("bites-bread");
                } else if (f == 225) {
                    shot("bites-apple");
                }
            });
        }

        if (wanted("foods")) {
            Vec3 feet = at(0, 0, 2);
            scene(mc, config, feet, () -> showHand = true, () -> face(mc, 160, 6),
                    "cookie", "golden_carrot", "pumpkin_pie", "cooked_salmon");
            record("03-foods", 450, f -> {
                place(mc, feet);
                face(mc, lerp(160, 200, smooth(f / 450.0)), 6);
                meals(mc, f, 15, 125, 235, 345);
                if (f == 280) {
                    shot("foods");
                }
            });
        }

        if (wanted("drinks")) {
            Vec3 feet = at(0, 0, 2);
            scene(mc, config, feet, () -> showHand = true, () -> face(mc, 200, 4),
                    POTION, "milk_bucket", "honey_bottle");
            record("04-drinks", 420, f -> {
                place(mc, feet);
                face(mc, lerp(200, 165, smooth(f / 420.0)), 4);
                meals(mc, f, 15, 130, 245);
                if (f == 75) {
                    shot("drinks-potion");
                } else if (f == 190) {
                    shot("drinks-milk");
                }
            });
        }

        if (wanted("bowls")) {
            Vec3 feet = at(0, 0, 2);
            scene(mc, config, feet, () -> showHand = true, () -> face(mc, 175, 8), "mushroom_stew", "beetroot_soup");
            record("05-bowls", 260, f -> {
                place(mc, feet);
                face(mc, 175 + 3 * Math.sin(f / 60.0), 8);
                meals(mc, f, 15, 130);
                if (f == 80) {
                    shot("bowls");
                }
            });
        }

        if (wanted("modded") && FabricLoader.getInstance().isModLoaded("farmersdelight")) {
            Vec3 feet = at(0, 0, 2);
            scene(mc, config, feet, () -> showHand = true, () -> face(mc, 190, 6),
                    "farmersdelight:hamburger", "farmersdelight:sweet_berry_cheesecake_slice",
                    "farmersdelight:hot_cocoa", "farmersdelight:vegetable_soup");
            record("06-modded", 450, f -> {
                place(mc, feet);
                face(mc, lerp(190, 165, smooth(f / 450.0)), 6);
                meals(mc, f, 15, 125, 235, 345);
                if (f == 70) {
                    shot("modded");
                }
            });
        }

        if (wanted("third")) {
            Vec3 feet = at(0, 0, 2);
            scene(mc, config, feet, () -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                run(mc, "attribute @p minecraft:camera_distance base set 2.6");
            }, () -> face(mc, 180, 12), "golden_apple", "cooked_chicken");
            record("07-third", 260, f -> {
                place(mc, feet);
                face(mc, 180, 12);
                meals(mc, f, 15, 130);
                if (f == 85) {
                    shot("third");
                }
            });
            once(() -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                run(mc, "attribute @p minecraft:camera_distance base reset");
            });
        }

        if (wanted("mobs")) {
            Vec3 feet = at(0, 0, 4.6);
            Vec3 look = at(0, 1.25, 1);
            scene(mc, config, feet, () -> {
                summon(mc, "husk", at(-1.7, 0, 1), 15, holding("bread", 16));
                // The helmet keeps the zombie from burning in the sun.
                summon(mc, "zombie", at(0, 0, 0.6), 0, ",equipment:{mainhand:{id:\"minecraft:cooked_chicken\",count:16},"
                        + "head:{id:\"minecraft:leather_helmet\",count:1}}");
                summon(mc, "husk", at(1.7, 0, 1), -15, holding("apple", 16));
            }, () -> lookAt(mc, look));
            record("08-mobs", 330, f -> {
                place(mc, feet);
                lookAt(mc, look);
                feed(mc, f, 10, 40, 70);
                if (f == 200) {
                    shot("mobs");
                }
            });
        }

        if (wanted("settings")) {
            Vec3 feet = at(0, 0, 2);
            scene(mc, config, feet, () -> {
            }, () -> face(mc, 180, 5));
            once(() -> Screens.open(mc, new MunchSettingsScreen(null, mc.options)));
            holdFor(20, () -> {
                place(mc, feet);
                cursor(0, new Stop(0, () -> corner(mc)));
            });
            record("09-settings", 330, f -> {
                place(mc, feet);
                if (f < 235) {
                    cursor(f, new Stop(0, () -> corner(mc)),
                            new Stop(45, () -> labelAt(mc, "Bites", 0.5)),
                            new Stop(110, () -> labelAt(mc, "Draining", 0.5)),
                            new Stop(165, () -> labelAt(mc, "Other Players & Mobs", 0.5)),
                            new Stop(215, () -> corner(mc)));
                }
                if (f == 55) {
                    clickLabel(mc, "Bites", 0.5);
                } else if (f == 120) {
                    clickLabel(mc, "Draining", 0.5);
                } else if (f == 175) {
                    clickLabel(mc, "Other Players & Mobs", 0.5);
                } else if (f == 210) {
                    shot("settings");
                } else if (f == 235) {
                    cursorShown = false;
                    Screens.open(mc, null);
                }
            });
            once(() -> Screens.open(mc, null));
        }

        if (wanted("outro")) {
            Vec3 start = at(0, 0, 2);
            Vec3 end = at(0, 0, -4);
            scene(mc, config, start, () -> showHand = true, () -> face(mc, 180, 4), "melon_slice");
            record("10-outro", 360, f -> {
                place(mc, lerp(start, end, smooth(f / 360.0)));
                face(mc, 180, 4);
                meals(mc, f, 30);
                if (f == 90) {
                    shot("outro");
                }
            });
        }
        once(() -> stopEating(mc));
    }

    private static double[] corner(Minecraft mc) {
        Screen screen = Screens.current(mc);
        return new double[] {screen.width - 36, screen.height - 30};
    }

    private static double[] labelAt(Minecraft mc, String label, double along) {
        AbstractWidget widget = find(Screens.current(mc), label);
        if (widget == null) {
            throw new IllegalStateException("no widget " + label);
        }
        return new double[] {widget.getX() + widget.getWidth() * along, widget.getY() + widget.getHeight() / 2.0};
    }

    private static void clickLabel(Minecraft mc, String label, double along) {
        double[] at = labelAt(mc, label, along);
        Ui.click(Screens.current(mc), at[0], at[1]);
    }

    private static AbstractWidget find(GuiEventListener node, String label) {
        if (node instanceof AbstractWidget widget && widget.getMessage().getString().startsWith(label + ":")) {
            return widget;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractWidget found = find(child, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void deleteWorld(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        if (Files.exists(dir)) {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    static void log(String message) {
        System.out.println("[munch-showcase] " + message);
    }
}
