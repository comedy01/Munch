package dev.munch.client;

import dev.munch.core.Plan;
import dev.munch.mixin.ItemStackRenderStateAccessor;
import dev.munch.mixin.LayerRenderStateAccessor;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class Hook {
    private static final Object UNCHANGED = new Object();

    private Hook() {
    }

    public static void apply(ItemStackRenderState output, ItemStack item, ItemDisplayContext context, Object owner,
                             BiConsumer<ItemStackRenderState, ItemStack> top,
                             BiConsumer<ItemStackRenderState, ItemStack> append) {
        Eating eating = Eating.start(item, context, owner);
        if (eating == null) {
            return;
        }
        try {
            cut(eating, output, top, append);
        } catch (RuntimeException e) {
            Eating.fail(e);
        }
    }

    private static void cut(Eating eating, ItemStackRenderState output, BiConsumer<ItemStackRenderState, ItemStack> top,
                            BiConsumer<ItemStackRenderState, ItemStack> append) {
        ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) output;
        int count = state.munch$activeLayerCount();
        List<TextureAtlasSprite> sprites = new ArrayList<>();
        List<Boolean> tinted = new ArrayList<>();
        if (!gather(output, 0, count, sprites, tinted)) {
            return;
        }

        ItemStack after = eating.remainder();
        List<TextureAtlasSprite> leftover = new ArrayList<>();
        if (!after.isEmpty()) {
            ItemStackRenderState scratch = new ItemStackRenderState();
            top.accept(scratch, after);
            if (!gather(scratch, 0, ((ItemStackRenderStateAccessor) scratch).munch$activeLayerCount(), leftover, new ArrayList<>())) {
                leftover.clear();
            }
        }

        Plan plan = eating.plan(sprites, tinted, leftover);
        if (plan == null) {
            return;
        }
        int stage = eating.stage(plan);
        if (stage <= 0) {
            return;
        }
        if (cutLayers(eating, output, 0, count, plan, stage, false) && eating.draining() && !leftover.isEmpty()) {
            append.accept(output, after);
            cutLayers(eating, output, count, state.munch$activeLayerCount(), plan, stage, true);
        }
    }

    private static boolean gather(ItemStackRenderState output, int from, int to,
                                  List<TextureAtlasSprite> sprites, List<Boolean> tinted) {
        ItemStackRenderState.LayerRenderState[] layers = ((ItemStackRenderStateAccessor) output).munch$layers();
        for (int i = from; i < to; i++) {
            LayerRenderStateAccessor layer = (LayerRenderStateAccessor) layers[i];
            if (layer.munch$specialRenderer() != null) {
                return false;
            }
            for (Object quad : layer.munch$quads()) {
                TextureAtlasSprite sprite = Quads.sprite(quad);
                if (!sprites.contains(sprite)) {
                    sprites.add(sprite);
                    tinted.add(Tints.tinted(layers[i], Quads.tintIndex(quad)));
                }
            }
        }
        return !sprites.isEmpty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean cutLayers(Eating eating, ItemStackRenderState output, int from, int to, Plan plan, int stage,
                                     boolean inside) {
        ItemStackRenderState.LayerRenderState[] layers = ((ItemStackRenderStateAccessor) output).munch$layers();
        boolean any = false;
        for (int i = from; i < to; i++) {
            List quads = ((LayerRenderStateAccessor) layers[i]).munch$quads();
            if (quads.isEmpty()) {
                continue;
            }
            List<Object> original = new ArrayList<>(quads);
            Object cut = eating.cut(new Eating.Same(original), () -> Quads.read(original), Quads::write, UNCHANGED,
                    plan, stage, inside);
            if (cut != UNCHANGED) {
                quads.clear();
                quads.addAll((List) cut);
                any = true;
            }
        }
        return any;
    }
}
