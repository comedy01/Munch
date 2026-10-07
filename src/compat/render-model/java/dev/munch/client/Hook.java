package dev.munch.client;

import dev.munch.mixin.ItemRendererAccessor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class Hook {
    private Hook() {
    }

    public static BakedModel model(ItemRenderer renderer, BakedModel model, ItemStack stack, ItemDisplayContext context,
                                   LivingEntity entity, Level level) {
        Eating eating = Eating.start(stack, context, entity);
        if (eating == null) {
            return model;
        }
        try {
            ItemColors colors = ((ItemRendererAccessor) renderer).munch$itemColors();
            return Models.cut(eating, model, index -> index >= 0 && colors.getColor(stack, index) != -1,
                    () -> renderer.getModel(eating.remainder(), level, entity, 0));
        } catch (RuntimeException e) {
            Eating.fail(e);
            return model;
        }
    }
}
