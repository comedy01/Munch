package dev.munch.client;

import dev.munch.mixin.ItemStackRenderStateAccessor;
import dev.munch.mixin.LayerModelAccessor;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

public final class Hook {
    private Hook() {
    }

    public static void apply(ItemStackRenderState output, ItemStack item, ItemDisplayContext context, Object owner,
                             BiConsumer<ItemStackRenderState, ItemStack> top) {
        Eating eating = Eating.start(item, context, owner);
        if (eating == null) {
            return;
        }
        try {
            ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) output;
            ItemStackRenderState.LayerRenderState[] layers = state.munch$layers();
            for (int i = 0; i < state.munch$activeLayerCount(); i++) {
                ItemStackRenderState.LayerRenderState layer = layers[i];
                LayerModelAccessor access = (LayerModelAccessor) layer;
                if (access.munch$specialRenderer() != null || access.munch$model() == null) {
                    continue;
                }
                BakedModel cut = Models.cut(eating, access.munch$model(), index -> Tints.tinted(layer, index),
                        () -> remainder(top, eating.remainder()));
                access.munch$setModel(cut);
            }
        } catch (RuntimeException e) {
            Eating.fail(e);
        }
    }

    private static BakedModel remainder(BiConsumer<ItemStackRenderState, ItemStack> top, ItemStack stack) {
        ItemStackRenderState scratch = new ItemStackRenderState();
        top.accept(scratch, stack);
        ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) scratch;
        for (int i = 0; i < state.munch$activeLayerCount(); i++) {
            BakedModel model = ((LayerModelAccessor) state.munch$layers()[i]).munch$model();
            if (model != null) {
                return model;
            }
        }
        return null;
    }
}
