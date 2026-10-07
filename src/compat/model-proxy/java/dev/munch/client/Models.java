package dev.munch.client;

import dev.munch.core.Plan;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

public final class Models {
    private static final Object UNCHANGED = new Object();

    private Models() {
    }

    @SuppressWarnings("unchecked")
    public static BakedModel cut(Eating eating, BakedModel model, IntPredicate tints, Supplier<BakedModel> remainder) {
        if (model == null || model instanceof Proxy) {
            return model;
        }
        List<BakedQuad> all = quads(model);
        List<TextureAtlasSprite> sprites = new ArrayList<>();
        List<Boolean> tinted = new ArrayList<>();
        for (BakedQuad quad : all) {
            TextureAtlasSprite sprite = Quads.sprite(quad);
            if (!sprites.contains(sprite)) {
                sprites.add(sprite);
                tinted.add(tints.test(Quads.tintIndex(quad)));
            }
        }

        BakedModel after = eating.remainder().isEmpty() ? null : remainder.get();
        List<BakedQuad> afterQuads = after == null ? List.of() : quads(after);
        List<TextureAtlasSprite> leftover = new ArrayList<>();
        for (BakedQuad quad : afterQuads) {
            if (!leftover.contains(Quads.sprite(quad))) {
                leftover.add(Quads.sprite(quad));
            }
        }

        Plan plan = eating.plan(sprites, tinted, leftover);
        if (plan == null) {
            return model;
        }
        int stage = eating.stage(plan);
        if (stage <= 0) {
            return model;
        }
        Object cut = eating.cut(new Eating.Same(model), () -> Quads.read(all), Quads::write, UNCHANGED, plan, stage, false);
        if (cut == UNCHANGED) {
            return model;
        }
        List<BakedQuad> shown = new ArrayList<>((List<BakedQuad>) cut);
        if (eating.draining() && after != null && !leftover.isEmpty()) {
            Object inside = eating.cut(new Eating.Same(after), () -> Quads.read(afterQuads), Quads::write, UNCHANGED,
                    plan, stage, true);
            if (inside != UNCHANGED) {
                shown.addAll((List<BakedQuad>) inside);
            }
        }
        return (BakedModel) Proxy.newProxyInstance(Models.class.getClassLoader(), new Class<?>[]{BakedModel.class},
                new Handler(model, List.copyOf(shown)));
    }

    private static List<BakedQuad> quads(BakedModel model) {
        List<BakedQuad> all = new ArrayList<>(model.getQuads(null, null, RandomSource.create(42L)));
        for (Direction direction : Direction.values()) {
            all.addAll(model.getQuads(null, direction, RandomSource.create(42L)));
        }
        return all;
    }

    private static final class Handler implements InvocationHandler {
        private final BakedModel original;
        private final List<BakedQuad> quads;

        Handler(BakedModel original, List<BakedQuad> quads) {
            this.original = original;
            this.quads = quads;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return switch (name) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "Munch(" + original + ")";
                };
            }
            if (name.equals("getQuads") && args != null && args.length >= 3) {
                return args[1] == null ? quads : List.of();
            }
            Object result;
            try {
                result = method.invoke(original, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            if (result == original) {
                return proxy;
            }
            if (result instanceof List<?> list && list.contains(original)) {
                List<Object> swapped = new ArrayList<>(list);
                swapped.replaceAll(entry -> entry == original ? proxy : entry);
                return swapped;
            }
            return result;
        }
    }
}
