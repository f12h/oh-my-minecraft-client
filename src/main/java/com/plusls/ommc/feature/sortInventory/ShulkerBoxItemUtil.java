package com.plusls.ommc.feature.sortInventory;

import com.plusls.ommc.config.Configs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.hendrixshen.magiclib.compat.minecraft.api.nbt.TagCompatApi;

public class ShulkerBoxItemUtil {
    public static final int SHULKERBOX_MAX_STACK_AMOUNT = 64;

    public static boolean isEmptyShulkerBoxItem(ItemStack itemStack) {
        if (!ShulkerBoxItemUtil.isShulkerBoxBlockItem(itemStack)) {
            return false;
        }

        CompoundTag nbt = itemStack.getTag();

        if (nbt == null || !nbt.contains("BlockEntityTag", TagCompatApi.TAG_COMPOUND)) {
            return true;
        }

        CompoundTag tag = nbt.getCompound("BlockEntityTag");

        if (tag.contains("Items", TagCompatApi.TAG_LIST)) {
            ListTag tagList = tag.getList("Items", TagCompatApi.TAG_COMPOUND);
            return tagList.isEmpty();
        }

        return true;
    }

    public static boolean isShulkerBoxBlockItem(@NotNull ItemStack itemStack) {
        return itemStack.getItem() instanceof BlockItem &&
                ((BlockItem) itemStack.getItem()).getBlock() instanceof ShulkerBoxBlock;
    }

    public static int compareShulkerBox(@Nullable CompoundTag a, @Nullable CompoundTag b) {
        int aSize = 0, bSize = 0;

        if (a != null) {
            CompoundTag tag = a.getCompound("BlockEntityTag");

            if (tag.contains("Items", TagCompatApi.TAG_LIST)) {
                ListTag tagList = tag.getList("Items", TagCompatApi.TAG_COMPOUND);
                aSize = tagList.size();
            }
        }

        if (b != null) {
            CompoundTag tag = b.getCompound("BlockEntityTag");

            if (tag.contains("Items", TagCompatApi.TAG_LIST)) {
                ListTag tagList = tag.getList("Items", TagCompatApi.TAG_COMPOUND);
                bSize = tagList.size();
            }
        }

        return aSize - bSize;
    }

    public static int getMaxCount(ItemStack itemStack) {
        if (Configs.sortInventorySupportEmptyShulkerBoxStack &&
                ShulkerBoxItemUtil.isEmptyShulkerBoxItem(itemStack)) {
            return ShulkerBoxItemUtil.SHULKERBOX_MAX_STACK_AMOUNT;
        } else {
            return itemStack.getMaxStackSize();
        }
    }

    public static boolean isStackable(ItemStack itemStack) {
        return getMaxCount(itemStack) > 1 && (!itemStack.isDamageableItem() || !itemStack.isDamaged());
    }
}