package dev.munch.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowlFoodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SuspiciousStewItem;
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
        Item item = stack.getItem();
        if (item instanceof BowlFoodItem || item instanceof SuspiciousStewItem) {
            return new ItemStack(Items.BOWL);
        }
        Item left = item.getCraftingRemainingItem();
        return left == null ? ItemStack.EMPTY : new ItemStack(left);
    }

    static int duration(ItemStack stack, LivingEntity entity) {
        return stack.getUseDuration();
    }

    static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b);
    }
}
