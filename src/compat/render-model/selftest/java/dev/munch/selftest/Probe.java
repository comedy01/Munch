package dev.munch.selftest;

import dev.munch.client.Hook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

final class Probe {
    private Probe() {
    }

    static int quads(Minecraft mc, ItemStack stack, ItemDisplayContext context) {
        return count(model(stack, context, mc.player));
    }

    static int thirdPerson(LivingEntity entity, ItemStack stack) {
        return count(model(stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity));
    }

    private static BakedModel model(ItemStack stack, ItemDisplayContext context, LivingEntity entity) {
        ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
        BakedModel model = renderer.getModel(stack, entity.level(), entity, 0);
        return Hook.model(renderer, model, stack, context, entity, entity.level());
    }

    private static int count(BakedModel model) {
        int total = model.getQuads(null, null, RandomSource.create(42L)).size();
        for (Direction direction : Direction.values()) {
            total += model.getQuads(null, direction, RandomSource.create(42L)).size();
        }
        return total;
    }
}
