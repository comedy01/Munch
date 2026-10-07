package dev.munch.selftest;

import dev.munch.client.Eating;
import dev.munch.client.MunchClient;
import dev.munch.client.gui.MunchSettingsScreen;
import dev.munch.config.MunchConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class MunchSelfTest {
    private static final String WORLD = "munch-selftest";
    private static final int TIMEOUT = 2400;

    private static volatile int frames;

    private final List<Step> steps = new ArrayList<>();
    private final List<String> shots = new ArrayList<>();
    private final Set<String> existingShots = new HashSet<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int delay;
    private int waited;
    private int seenFrames;
    private int patience;
    private int whole;
    private int cutsBefore;
    private int lastQuads;

    private record Step(int delay, BooleanSupplier ready, Runnable action) {
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 40) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.tutorialStep = TutorialSteps.NONE;
                ServerCompat.hideChat(mc);
                rememberShots(mc);
                plan(mc);
                delay = steps.get(0).delay();
                log("creating world");
                deleteWorld(mc, WORLD);
                Worlds.create(mc, WORLD, true, 0L);
            }
            return;
        }
        int drawn = frames;
        if (drawn == seenFrames) {
            return;
        }
        seenFrames = drawn;
        if (delay > 0) {
            delay--;
            return;
        }
        Step step = steps.get(index);
        if (!step.ready().getAsBoolean()) {
            if (++waited > TIMEOUT) {
                finish(mc, new AssertionError("timed out at step " + index));
            }
            return;
        }
        waited = 0;
        index++;
        try {
            step.action().run();
        } catch (Throwable e) {
            finish(mc, e);
            return;
        }
        if (index >= steps.size()) {
            finish(mc, null);
        } else {
            delay = steps.get(index).delay();
        }
    }

    public static void frame() {
        frames++;
    }

    private void then(int ticks, Runnable action) {
        steps.add(new Step(ticks, () -> true, action));
    }

    private void when(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(0, ready, action));
    }

    private void within(int ticks, BooleanSupplier ready, Runnable action) {
        then(1, () -> patience = ticks);
        when(() -> ready.getAsBoolean() || --patience < 0, action);
    }

    private void finish(Minecraft mc, Throwable failure) {
        finished = true;
        mc.options.keyUse.setDown(false);
        nameShots(mc);
        if (failure == null) {
            log("ALL CHECKS PASSED");
        } else {
            log("FAILED: " + failure);
            failure.printStackTrace();
        }
        MunchConfig config = MunchClient.config();
        config.resetToDefaults();
        MunchClient.saveConfig();
        mc.stop();
    }

    private void plan(Minecraft mc) {
        MunchConfig config = MunchClient.config();
        int floor = ServerCompat.FLOOR;
        boolean foodsOnly = Boolean.getBoolean("munch.foods");

        when(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null, () -> log("world loaded"));
        then(40, () -> {
            config.resetToDefaults();
            run(mc, "time set 6000");
            run(mc, "weather clear");
            run(mc, "fill -6 " + floor + " -8 6 " + (floor + 6) + " -8 minecraft:white_concrete");
            run(mc, "tp @p 0.5 " + floor + " 0.5 180 0");
        });

        if (foodsOnly) {
            modded(mc);
            return;
        }
        eat(mc, "cooked_beef", "minecraft:cooked_beef", false);
        eat(mc, "apple", "minecraft:apple", false);
        eat(mc, "bread", "minecraft:bread", false);
        thirdPerson(mc);
        eat(mc, "potion", Cmds.HEALING_POTION, true);
        eat(mc, "milk", "minecraft:milk_bucket", true);
        eat(mc, "stew", "minecraft:mushroom_stew", true);
        eat(mc, "honey", "minecraft:honey_bottle", true);
        eat(mc, "suspicious_stew", "minecraft:suspicious_stew", true);
        eat(mc, "golden_apple", "minecraft:enchanted_golden_apple", false);
        shortSnack(mc);
        untouched(mc, "ominous_bottle", "minecraft:ominous_bottle");
        offHand(mc);
        mob(mc);

        hold(mc, "minecraft:bread");
        then(10, () -> {
            config.setEnabled(false);
            whole = quads(mc);
            cutsBefore = Eating.cuts();
            startUsing(mc);
        });
        within(60, () -> mc.player.getTicksUsingItem() >= 20, () -> {
            check(mc.player.isUsingItem(), "eating bread never started");
            check(Eating.cuts() == cutsBefore, "Munch bit the bread while turned off");
            check(quads(mc) == whole, "the bread changed while Munch was off");
            stopUsing(mc);
            config.setEnabled(true);
        });

        then(20, () -> {
            Screens.open(mc, new MunchSettingsScreen(null, mc.options));
        });
        then(20, () -> {
            check(Screens.current(mc) instanceof MunchSettingsScreen, "the settings screen did not open");
            screenshot(mc, "settings");
        });
        then(5, () -> Screens.open(mc, null));
    }

    private void eat(Minecraft mc, String name, String item, boolean drink) {
        hold(mc, item);
        then(10, () -> {
            whole = quads(mc);
            check(whole > 0, name + " has no quads to bite");
            cutsBefore = Eating.cuts();
            lastQuads = whole;
            startUsing(mc);
        });
        within(40, () -> mc.player.isUsingItem() && mc.player.getTicksUsingItem() >= 2, () -> {
            check(mc.player.isUsingItem(), "using " + name + " never started");
            if (!drink && mc.player.getTicksUsingItem() < 8) {
                check(quads(mc) == whole, name + " was bitten before the first chew");
            }
            screenshot(mc, name + "-0");
        });
        for (int at : drink ? new int[]{12, 20, 28} : new int[]{10, 14, 18, 22, 26, 30}) {
            within(60, () -> mc.player.getTicksUsingItem() >= at, () -> {
                check(mc.player.isUsingItem(), name + " stopped early at " + mc.player.getTicksUsingItem());
                int now = quads(mc);
                log(name + " at tick " + mc.player.getTicksUsingItem() + ": " + now + " quads (whole " + whole + ")");
                check(Eating.cuts() > cutsBefore, name + " was not cut after " + mc.player.getTicksUsingItem() + " ticks");
                cutsBefore = Eating.cuts();
                lastQuads = now;
                screenshot(mc, name + "-" + at);
            });
        }
        then(1, () -> stopUsing(mc));
        within(40, () -> !mc.player.isUsingItem(), () -> {
            check(!mc.player.isUsingItem(), "still using " + name);
            check(quads(mc) == whole, name + " did not come back whole after stopping");
        });
    }

    private final List<String> moddedCut = new ArrayList<>();
    private final List<String> moddedWhole = new ArrayList<>();

    private void modded(Minecraft mc) {
        then(5, () -> moddedSteps(mc));
    }

    private void moddedSteps(Minecraft mc) {
        List<String> foods = new ArrayList<>();
        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
            if (!id.startsWith("minecraft:") && Eating.edible(new ItemStack(item))) {
                foods.add(id);
            }
        }
        foods.sort(null);
        log("modded foods and drinks: " + foods.size() + " " + foods);
        for (String id : foods) {
            String name = id.replace(':', '_');
            then(3, () -> run(mc, "item replace entity @p weapon.mainhand with minecraft:air"));
            then(3, () -> run(mc, "item replace entity @p weapon.mainhand with " + id));
            within(30, () -> !mc.player.getMainHandItem().isEmpty(), () -> {
                cutsBefore = Eating.cuts();
                startUsing(mc);
            });
            within(40, () -> mc.player.getTicksUsingItem() >= 10, () -> screenshot(mc, name + "-10"));
            within(40, () -> mc.player.getTicksUsingItem() >= 18 || !mc.player.isUsingItem(), () -> {
                if (mc.player.isUsingItem()) {
                    quads(mc);
                    screenshot(mc, name + "-18");
                }
            });
            within(40, () -> mc.player.getTicksUsingItem() >= 26 || !mc.player.isUsingItem(), () -> {
                if (mc.player.isUsingItem()) {
                    quads(mc);
                    screenshot(mc, name + "-26");
                }
                boolean cut = Eating.cuts() > cutsBefore;
                (cut ? moddedCut : moddedWhole).add(id);
                log((cut ? "cut " : "NOT cut ") + id);
                stopUsing(mc);
            });
            within(40, () -> !mc.player.isUsingItem(), () -> {
            });
        }
        then(5, () -> {
            log("modded summary: cut " + moddedCut.size() + ", not cut " + moddedWhole.size() + " " + moddedWhole);
            check(!moddedCut.isEmpty(), "no modded food was cut");
        });
    }

    private void shortSnack(Minecraft mc) {
        hold(mc, "minecraft:dried_kelp");
        then(10, () -> {
            whole = quads(mc);
            startUsing(mc);
        });
        within(40, () -> mc.player.getTicksUsingItem() >= 12, () -> {
            check(mc.player.isUsingItem(), "eating dried kelp never started");
            check(quads(mc) != whole, "dried kelp was not bitten after 12 of 16 ticks");
            screenshot(mc, "dried_kelp");
            stopUsing(mc);
        });
        within(40, () -> !mc.player.isUsingItem(), () -> {
        });
    }

    private void untouched(Minecraft mc, String name, String item) {
        then(5, () -> run(mc, "item replace entity @p weapon.mainhand with minecraft:air"));
        then(10, () -> run(mc, "item replace entity @p weapon.mainhand with " + item));
        then(20, () -> {
            if (mc.player.getMainHandItem().isEmpty()) {
                log(name + " does not exist in this version, skipped");
                return;
            }
            whole = quads(mc);
            cutsBefore = Eating.cuts();
            startUsing(mc);
        });
        within(60, () -> mc.player.getTicksUsingItem() >= 24, () -> {
            if (mc.player.getMainHandItem().isEmpty()) {
                return;
            }
            check(mc.player.isUsingItem(), "using " + name + " never started");
            check(quads(mc) == whole, name + " was changed but has nothing that can drain");
            stopUsing(mc);
        });
        within(40, () -> !mc.player.isUsingItem(), () -> {
        });
    }

    private void offHand(Minecraft mc) {
        then(5, () -> {
            run(mc, "item replace entity @p weapon.mainhand with minecraft:air");
            run(mc, "item replace entity @p weapon.offhand with minecraft:golden_carrot");
        });
        within(40, () -> !mc.player.getOffhandItem().isEmpty(), () -> {
            whole = quads(mc, ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
            mc.options.keyUse.setDown(true);
            mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
        });
        within(60, () -> mc.player.getTicksUsingItem() >= 20, () -> {
            check(mc.player.isUsingItem(), "eating from the off hand never started");
            check(quads(mc, ItemDisplayContext.FIRST_PERSON_LEFT_HAND) != whole, "the off-hand carrot was not bitten");
            check(quads(mc, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) == whole,
                    "the bites were drawn on the empty main hand side");
            screenshot(mc, "off_hand");
            stopUsing(mc);
            run(mc, "item replace entity @p weapon.offhand with minecraft:air");
        });
        within(40, () -> !mc.player.isUsingItem(), () -> {
        });
    }

    private void mob(Minecraft mc) {
        MunchConfig config = MunchClient.config();
        then(5, () -> run(mc, "summon minecraft:husk 0.5 " + ServerCompat.FLOOR + " -3.5 " + Cmds.HUSK_WITH_BREAD));
        within(60, () -> husk(mc) != null && !husk(mc).getMainHandItem().isEmpty(), () -> {
            check(husk(mc) != null, "the husk did not spawn");
            check(!husk(mc).getMainHandItem().isEmpty(), "the husk holds no bread");
            whole = thirdPerson(husk(mc));
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                for (net.minecraft.world.entity.Entity e : server.overworld().getAllEntities()) {
                    if ("minecraft:husk".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()) && e instanceof net.minecraft.world.entity.LivingEntity husk) {
                        husk.startUsingItem(InteractionHand.MAIN_HAND);
                    }
                }
            });
        });
        within(60, () -> husk(mc).isUsingItem() && husk(mc).getTicksUsingItem() >= 14, () -> {
            check(husk(mc).isUsingItem(), "the husk never started eating on the client");
            check(thirdPerson(husk(mc)) != whole, "the husk's bread was not bitten");
            config.setOthers(false);
            check(thirdPerson(husk(mc)) == whole, "Other Players & Mobs off still bit the husk's bread");
            config.setOthers(true);
            screenshot(mc, "husk");
            run(mc, "kill @e[type=minecraft:husk]");
        });
    }

    private static net.minecraft.world.entity.LivingEntity husk(Minecraft mc) {
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if ("minecraft:husk".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()) && e instanceof net.minecraft.world.entity.LivingEntity husk) {
                return husk;
            }
        }
        return null;
    }

    private static int thirdPerson(net.minecraft.world.entity.LivingEntity entity) {
        return Probe.thirdPerson(entity, entity.getMainHandItem());
    }

    private void thirdPerson(Minecraft mc) {
        hold(mc, "minecraft:cookie");
        then(10, () -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            whole = quads(mc);
            startUsing(mc);
        });
        within(60, () -> mc.player.getTicksUsingItem() >= 24, () -> {
            check(mc.player.isUsingItem(), "eating a cookie in third person never started");
            check(Probe.thirdPerson(mc.player, mc.player.getUseItem()) != whole, "the cookie is whole in third person");
            screenshot(mc, "cookie-third-person");
        });
        then(1, () -> stopUsing(mc));
        within(40, () -> !mc.player.isUsingItem(), () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    private void hold(Minecraft mc, String item) {
        then(5, () -> run(mc, "item replace entity @p weapon.mainhand with " + item));
        within(40, () -> !mc.player.getMainHandItem().isEmpty(), () ->
                check(!mc.player.getMainHandItem().isEmpty(), "could not give the player " + item));
    }

    private static void startUsing(Minecraft mc) {
        mc.options.keyUse.setDown(true);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }

    private static void stopUsing(Minecraft mc) {
        mc.options.keyUse.setDown(false);
        mc.gameMode.releaseUsingItem(mc.player);
    }

    private static int quads(Minecraft mc) {
        return quads(mc, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
    }

    private static int quads(Minecraft mc, ItemDisplayContext context) {
        ItemStack stack = mc.player.isUsingItem() ? mc.player.getUseItem()
                : context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? mc.player.getOffhandItem() : mc.player.getMainHandItem();
        return Probe.quads(mc, stack, context);
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> ServerCompat.runCommand(server, command));
    }

    private void screenshot(Minecraft mc, String name) {
        log("screenshot: " + name);
        shots.add(name);
        ServerCompat.screenshot(mc, Screens.renderTarget(mc));
    }

    private void rememberShots(Minecraft mc) {
        File[] files = new File(mc.gameDirectory, "screenshots").listFiles();
        if (files != null) {
            for (File file : files) {
                existingShots.add(file.getName());
            }
        }
    }

    private void nameShots(Minecraft mc) {
        File dir = new File(mc.gameDirectory, "screenshots");
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".png") && !existingShots.contains(name));
        if (files == null) {
            return;
        }
        List<File> fresh = new ArrayList<>(List.of(files));
        fresh.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for (int i = 0; i < fresh.size() && i < shots.size(); i++) {
            File target = new File(dir, String.format(Locale.ROOT, "mu-%02d-%s.png", i, shots.get(i)));
            if ((!target.exists() || target.delete()) && fresh.get(i).renameTo(target)) {
                log("saved " + target.getName());
            }
        }
    }

    private static void deleteWorld(Minecraft mc, String name) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(name);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void log(String message) {
        System.out.println("[munch-selftest] " + message);
    }
}
