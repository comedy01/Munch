package dev.munch.client;

import dev.munch.core.Plan;
import dev.munch.mixin.ItemStackRenderStateAccessor;
import dev.munch.mixin.LayerRenderStateAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class Hook {
    private Hook() {
    }

    public static void apply(ItemModelResolver resolver, ItemStackRenderState output, ItemStack item,
                             ItemDisplayContext context, Level level, ItemOwner owner, int seed) {
        Eating eating = Eating.start(item, context, owner);
        if (eating == null) {
            return;
        }
        try {
            cut(eating, resolver, output, context, level, owner, seed);
        } catch (RuntimeException e) {
            Eating.fail(e);
        }
    }

    private static void cut(Eating eating, ItemModelResolver resolver, ItemStackRenderState output,
                            ItemDisplayContext context, Level level, ItemOwner owner, int seed) {
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
            resolver.updateForTopItem(scratch, after, context, level, owner, seed);
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
            resolver.appendItemLayers(output, after, context, level, owner, seed);
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
            IntList tints = layers[i].tintLayers();
            for (BakedQuad quad : layer.munch$quads().all()) {
                TextureAtlasSprite sprite = quad.materialInfo().sprite();
                if (!sprites.contains(sprite)) {
                    int index = Quads.tintIndex(quad);
                    sprites.add(sprite);
                    tinted.add(index >= 0 && index < tints.size() && tints.getInt(index) != -1);
                }
            }
        }
        return !sprites.isEmpty();
    }

    private static boolean cutLayers(Eating eating, ItemStackRenderState output, int from, int to, Plan plan, int stage,
                                     boolean inside) {
        ItemStackRenderState.LayerRenderState[] layers = ((ItemStackRenderStateAccessor) output).munch$layers();
        boolean any = false;
        for (int i = from; i < to; i++) {
            ItemQuads quads = ((LayerRenderStateAccessor) layers[i]).munch$quads();
            if (quads.isEmpty()) {
                continue;
            }
            ItemQuads cut = eating.cut(new Eating.Same(quads), () -> Quads.read(quads.all()),
                    result -> ItemQuads.split(Quads.write(result)), quads, plan, stage, inside);
            if (cut != quads) {
                layers[i].setQuads(cut);
                any = true;
            }
        }
        return any;
    }
}
