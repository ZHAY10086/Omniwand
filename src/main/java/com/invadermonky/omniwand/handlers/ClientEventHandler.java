package com.invadermonky.omniwand.handlers;

import com.invadermonky.omniwand.Omniwand;
import com.invadermonky.omniwand.config.ConfigHandler;
import com.invadermonky.omniwand.config.ConfigTags;
import com.invadermonky.omniwand.network.MessageWandTransform;
import com.invadermonky.omniwand.registry.Registry;
import com.invadermonky.omniwand.util.WandHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = Omniwand.MOD_ID)
public class ClientEventHandler {

    // 添加静态变量来存储上一次的目标信息
    private static String lastTargetMod = Omniwand.MOD_ID;
    private static String lastActualKey = Omniwand.MOD_ID;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseEvent(MouseEvent event) {
        EntityPlayerSP playerSP = Minecraft.getMinecraft().player;
        ItemStack heldItem = playerSP.getHeldItem(ConfigTags.getConfiguredHand());

        if (WandHelper.isOmniwand(heldItem)) {
            RayTraceResult result = playerSP.rayTrace(playerSP.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue(), 1.0f);

            String targetMod = Omniwand.MOD_ID; // 默认为目标是Omniwand本身
            if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
                IBlockState lookState = playerSP.world.getBlockState(result.getBlockPos());
                targetMod = WandHelper.getModOrAlias(lookState);
            }

            // 只有当目标mod发生变化时才重新计算实际目标
            if (!lastTargetMod.equals(targetMod)) {
                lastTargetMod = targetMod;
                lastActualKey = getActualTargetKey(heldItem, targetMod);
            }

            // 使用稳定的实际目标key进行转换
            if (!WandHelper.getModSlot(heldItem).equals(lastActualKey) && WandHelper.getAutoMode(heldItem)) {
                ItemStack newStack = WandHelper.getTransformedStack(heldItem, lastActualKey, false);
                WandHelper.setAutoMode(newStack, true);
                
                int slot = ConfigTags.getConfiguredHand() == EnumHand.OFF_HAND ? playerSP.inventory.getSizeInventory() - 1 : playerSP.inventory.currentItem;
                playerSP.inventory.setInventorySlotContents(slot, newStack);
                Omniwand.network.sendToServer(new MessageWandTransform(newStack, slot));
            }
        }
    }

    /**
     * 获取魔杖实际应该转换到的目标key
     * @param wandStack 魔杖物品堆
     * @param targetMod 目标模组ID
     * @return 实际应该转换到的key
     */
    private static String getActualTargetKey(ItemStack wandStack, String targetMod) {
        // 如果目标是Omniwand本身，则返回Omniwand
        if (targetMod.equals(Omniwand.MOD_ID)) {
            return targetMod;
        }
        
        // 获取魔杖数据
        NBTTagCompound wandData = WandHelper.getWandData(wandStack);
        
        // 直接包含目标模组的工具
        if (wandData.hasKey(targetMod)) {
            return targetMod;
        }
        
        // 检查是否有Transform Items可以用于该模组
        for (String key : wandData.getKeySet()) {
            if (!key.equals(Omniwand.MOD_ID)) {
                NBTTagCompound itemTag = wandData.getCompoundTag(key);
                ItemStack itemStack = new ItemStack(itemTag);
                if (!itemStack.isEmpty()) {
                    String itemMod = WandHelper.getModOrAlias(itemStack);
                    if (itemMod.equals(targetMod) && ConfigTags.isTransformItem(itemStack)) {
                        return key; // 返回实际存储的键名
                    }
                }
            }
        }
        
        // 如果没有找到合适的工具，则返回Omniwand本身
        return Omniwand.MOD_ID;
    }

    @SubscribeEvent
    public static void onPlayerSwing(PlayerInteractEvent.LeftClickEmpty event) {
        ItemStack stack = event.getItemStack();
        if (!ConfigHandler.crouchRevert || event.getEntityPlayer().isSneaking()) {
            if (WandHelper.isOmniwand(stack) && stack.getItem() != Registry.OMNIWAND) {
                ItemStack newStack = WandHelper.getTransformedStack(stack, Omniwand.MOD_ID, false);
                Omniwand.network.sendToServer(new MessageWandTransform(newStack, event.getEntityPlayer().inventory.currentItem));
            }
        }
    }
}
