package org.spoorn.myloot.block.entity.common;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ViewerCountManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spoorn.myloot.block.entity.MyLootContainerBlockEntity;
import org.spoorn.myloot.block.entity.MyLootInventory;
import org.spoorn.myloot.mixin.ItemStackAccessor;

import java.util.*;

public class MyLootContainerBlockEntityCommon {
    
    private static final String NBT_KEY = "myLoot";

    @Getter
    @Setter
    private Map<String, MyLootInventory> inventories = new HashMap<>();
    private final Set<String> playersOpened = new HashSet<>();
    
    private final ViewerCountManager stateManager;
    
    public MyLootContainerBlockEntityCommon(ViewerCountManager viewerCountManager) {
        this.stateManager = viewerCountManager;
    }

    public boolean hasPlayerOpened(PlayerEntity player) {
        return this.playersOpened.contains(player.getGameProfile().getId().toString());
    }

    public ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory, 
                                                DefaultedList<ItemStack> defaultList, MyLootContainerBlockEntity myLootContainerBlockEntity) {
        PlayerEntity player = playerInventory.player;
        Inventory inventory = getOrCreateNewInstancedInventoryIfAbsent(player, defaultList, myLootContainerBlockEntity);
        return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, inventory);
    }
    
    public Inventory getOrCreateNewInstancedInventoryIfAbsent(PlayerEntity player, DefaultedList<ItemStack> defaultList, MyLootContainerBlockEntity myLootContainerBlockEntity) {
        String playerId = player.getGameProfile().getId().toString();
        MyLootInventory myLootInventory;
        if (!this.inventories.containsKey(playerId)) {
            DefaultedList<ItemStack> clonedList = DefaultedList.ofSize(27, ItemStack.EMPTY);
            for (int i = 0; i < defaultList.size(); ++i) {
                ItemStack defaultItemStack = defaultList.get(i);
                clonedList.set(i, ItemStackAccessor.create(defaultItemStack.getItem(), defaultItemStack.getCount(), Optional.ofNullable(defaultItemStack.getNbt())));
            }
            myLootInventory = new MyLootInventory(clonedList, myLootContainerBlockEntity);
            this.inventories.put(playerId, myLootInventory);
        } else {
            myLootInventory = this.inventories.get(playerId);
        }
        return myLootInventory;
    }

    public void readNbt(NbtCompound nbt, MyLootContainerBlockEntity myLootContainerBlockEntity) {
        this.inventories.clear();
        this.playersOpened.clear();
        NbtCompound root = nbt.getCompound(NBT_KEY);
        // Inventories
        for (String playerId : root.getKeys()) {
            NbtCompound sub = root.getCompound(playerId);
            MyLootInventory inventory = new MyLootInventory(myLootContainerBlockEntity);
            NbtList nbtList = sub.getList("Items", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < nbtList.size(); ++i) {
                NbtCompound nbtCompound = nbtList.getCompound(i);
                int j = nbtCompound.getByte("Slot") & 0xFF;
                if (j < 0 || j >= inventory.size()) continue;
                inventory.setStack(j, ItemStack.fromNbt(nbtCompound));
            }
            this.inventories.put(playerId, inventory);
        }
        // Players opened
        NbtList playersOpened = root.getList("players", NbtElement.STRING_TYPE);
        for (int i = 0; i < playersOpened.size(); ++i) {
            this.playersOpened.add(playersOpened.getString(i));
        }
    }

    public void writeNbt(NbtCompound nbt) {
        NbtCompound root = new NbtCompound();
        // Inventories
        for (Map.Entry<String, MyLootInventory> entry : this.inventories.entrySet()) {
            NbtCompound sub = new NbtCompound();
            NbtList nbtList = new NbtList();
            MyLootInventory inventory = entry.getValue();
            for (int i = 0; i < inventory.size(); ++i) {
                ItemStack stack = inventory.getStack(i);
                if (stack.isEmpty()) continue;
                NbtCompound nbtCompound = new NbtCompound();
                nbtCompound.putByte("Slot", (byte)i);
                stack.writeNbt(nbtCompound);
                nbtList.add(nbtCompound);
            }
            sub.put("Items", nbtList);
            root.put(entry.getKey(), sub);
        }
        // Players opened
        NbtList playersOpenedList = new NbtList();
        for (String player : this.playersOpened) {
            playersOpenedList.add(NbtString.of(player));
        }
        root.put("players", playersOpenedList);
        nbt.put(NBT_KEY, root);
    }
    
    public void clear() {
        this.inventories.clear();
    }

    public void onOpen(PlayerEntity player, BlockEntity blockEntity) {
        if (!blockEntity.isRemoved()) {
            World world = blockEntity.getWorld();
            BlockPos pos = blockEntity.getPos();
            BlockState cachedState = blockEntity.getCachedState();
            if (!player.isSpectator()) {
                this.stateManager.openContainer(player, world, pos, cachedState);
            }

            String playerId = player.getGameProfile().getId().toString();
            if (!this.playersOpened.contains(playerId)) {
                this.playersOpened.add(playerId);
                blockEntity.markDirty();
                if (world != null) {
                    // Force sync with clients
                    world.updateListeners(pos, cachedState, cachedState, Block.NOTIFY_ALL);
                }
            }
        }
    }

    public void onClose(PlayerEntity player, BlockEntity blockEntity) {
        if (!blockEntity.isRemoved() && !player.isSpectator()) {
            this.stateManager.closeContainer(player, blockEntity.getWorld(), blockEntity.getPos(), blockEntity.getCachedState());
        }
    }

    public void onScheduledTick(BlockEntity blockEntity) {
        if (!blockEntity.isRemoved()) {
            this.stateManager.updateViewerCount(blockEntity.getWorld(), blockEntity.getPos(), blockEntity.getCachedState());
        }
    }
}
