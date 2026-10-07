package dev.munch.selftest;

import dev.munch.mixin.ItemStackRenderStateAccessor;
import dev.munch.mixin.LayerModelAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
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
        ItemStackRenderState state = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, context, false, mc.level, mc.player, 0);
        return count(state);
    }

    static int thirdPerson(LivingEntity entity, ItemStack stack) {
        ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForLiving(state, stack,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, entity);
        return count(state);
    }

    private static int count(ItemStackRenderState state) {
        ItemStackRenderStateAccessor accessor = (ItemStackRenderStateAccessor) state;
        int total = 0;
        for (int i = 0; i < accessor.munch$activeLayerCount(); i++) {
            BakedModel model = ((LayerModelAccessor) accessor.munch$layers()[i]).munch$model();
            if (model == null) {
                continue;
            }
            total += model.getQuads(null, null, RandomSource.create(42L)).size();
            for (Direction direction : Direction.values()) {
                total += model.getQuads(null, direction, RandomSource.create(42L)).size();
            }
        }
        return total;
    }
}
