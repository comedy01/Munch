package dev.munch.client;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.UseRemainder;

final class Use {
    static final int NONE = 0;
    static final int EAT = 1;
    static final int DRINK = 2;

    private Use() {
    }

    static int kind(ItemStack stack) {
        ItemUseAnimation animation = stack.getUseAnimation();
        return animation == ItemUseAnimation.EAT ? EAT : animation == ItemUseAnimation.DRINK ? DRINK : NONE;
    }

    static ItemStack remainder(ItemStack stack) {
        UseRemainder remainder = stack.get(DataComponents.USE_REMAINDER);
        if (remainder != null) {
            return remainder.convertInto().copy();
        }
        ItemStack crafting = stack.getItem().getCraftingRemainder();
        return crafting == null ? ItemStack.EMPTY : crafting.copy();
    }

    static int duration(ItemStack stack, LivingEntity entity) {
        return stack.getUseDuration(entity);
    }

    static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }
}
