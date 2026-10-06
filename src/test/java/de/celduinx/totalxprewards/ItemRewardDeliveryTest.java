package de.celduinx.totalxprewards;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemRewardDeliveryTest {
    @Test void dropsOnlyInventoryOverflowAtPlayer() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        ItemFactory factory = mock(ItemFactory.class);
        ItemStack template = mock(ItemStack.class);
        ItemStack stack = mock(ItemStack.class);
        ItemStack overflow = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        when(factory.createItemStack("minecraft:diamond")).thenReturn(template);
        when(template.getMaxStackSize()).thenReturn(64);
        when(template.clone()).thenReturn(stack);
        when(inventory.addItem(stack)).thenReturn(new java.util.HashMap<>(Map.of(0, overflow)));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
            assertTrue(ItemRewardDelivery.deliver(player, "give %player% minecraft:diamond 12"));
        }
        verify(stack).setAmount(12);
        verify(world).dropItemNaturally(location, overflow);
    }

    @Test void preservesComponentArgumentsAndLeavesOtherCommandsAlone() {
        String component = "minecraft:netherite_sword[minecraft:custom_name={\"text\":\"My Sword\"}]";
        ItemFactory factory = mock(ItemFactory.class);
        ItemStack template = mock(ItemStack.class);
        ItemStack stack = mock(ItemStack.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(factory.createItemStack(component)).thenReturn(template);
        when(template.getMaxStackSize()).thenReturn(1);
        when(template.clone()).thenReturn(stack);
        when(inventory.addItem(stack)).thenReturn(new java.util.HashMap<>());
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
            assertTrue(ItemRewardDelivery.deliver(player, "minecraft:give %player% " + component + " 1"));
            assertFalse(ItemRewardDelivery.deliver(player, "eco give %player% 100"));
        }
        verify(factory).createItemStack(component);
    }
}
