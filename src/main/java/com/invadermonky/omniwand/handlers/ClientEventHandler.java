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

    // Using static variable to store the last target mod
    private static String lastTargetMod = Omniwand.MOD_ID;
    private static String lastActualKey = Omniwand.MOD_ID;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseEvent(MouseEvent event) {
        EntityPlayerSP playerSP = Minecraft.getMinecraft().player;
        ItemStack heldItem = playerSP.getHeldItem(ConfigTags.getConfiguredHand());

        if (WandHelper.isOmniwand(heldItem)) {
            RayTraceResult result = playerSP.rayTrace(playerSP.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue(), 1.0f);

            String targetMod = Omniwand.MOD_ID; // Default to Omniwand
            if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
                IBlockState lookState = playerSP.world.getBlockState(result.getBlockPos());
                targetMod = WandHelper.getModOrAlias(lookState);
            }

            // Only update the actual key if the target mod has changed
            if (!lastTargetMod.equals(targetMod)) {
                lastTargetMod = targetMod;
                lastActualKey = getActualTargetKey(heldItem, targetMod);
            }

            // Use the actual key to transform the wand
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
     * Get the actual key to transform to based on the target mod
     * @param wandStack Omniwand item stack
     * @param targetMod Target mod id
     * @return The actual key to transform to
     */
    private static String getActualTargetKey(ItemStack wandStack, String targetMod) {
        // If the target mod is Omniwand, return Omniwand
        if (targetMod.equals(Omniwand.MOD_ID)) {
            return targetMod;
        }
        
        // Get the wand data
        NBTTagCompound wandData = WandHelper.getWandData(wandStack);
        
        // If the target mod is in the wand data, return it
        if (wandData.hasKey(targetMod)) {
            return targetMod;
        }
        
        // Check if the target mod is an alias in the wand data
        for (String key : wandData.getKeySet()) {
            if (!key.equals(Omniwand.MOD_ID)) {
                NBTTagCompound itemTag = wandData.getCompoundTag(key);
                ItemStack itemStack = new ItemStack(itemTag);
                if (!itemStack.isEmpty()) {
                    String itemMod = WandHelper.getModOrAlias(itemStack);
                    if (itemMod.equals(targetMod) && ConfigTags.isTransformItem(itemStack)) {
                        return key; // Return the key
                    }
                }
            }
        }
        
        // If no mods match, return the default mod
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
