package dev.munch.client;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

final class Use {
    static final int NONE = 0;
    static final int EAT = 1;
    static final int DRINK = 2;

    private Use() {
    }

    static int kind(ItemStack stack) {
        UseAnim animation = stack.getUseAnimation();
        return animation == UseAnim.EAT ? EAT : animation == UseAnim.DRINK ? DRINK : NONE;
    }

    static ItemStack remainder(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null && food.usingConvertsTo().isPresent()) {
            return food.usingConvertsTo().get().copy();
        }
        Item left = stack.getItem().getCraftingRemainingItem();
        return left == null ? ItemStack.EMPTY : new ItemStack(left);
    }

    static int duration(ItemStack stack, LivingEntity entity) {
        return stack.getUseDuration(entity);
    }

    static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }
}
