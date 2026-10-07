package dev.munch.selftest;

import dev.munch.mixin.ItemStackRenderStateAccessor;
import dev.munch.mixin.LayerRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

final class Probe {
    private Probe() {
    }

    static int quads(Minecraft mc, ItemStack stack, ItemDisplayContext context) {
        ItemStackRenderState state = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, context, mc.level, mc.player, 0);
        return count(state);
    }

    static int thirdPerson(LivingEntity entity, ItemStack stack) {
        ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForLiving(state, stack,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        return count(state);
    }

    private static int count(ItemStackRenderState state) {
        ItemStackRenderStateAccessor accessor = (ItemStackRenderStateAccessor) state;
        int total = 0;
        for (int i = 0; i < accessor.munch$activeLayerCount(); i++) {
            total += ((LayerRenderStateAccessor) accessor.munch$layers()[i]).munch$quads().all().size();
        }
        return total;
    }
}
