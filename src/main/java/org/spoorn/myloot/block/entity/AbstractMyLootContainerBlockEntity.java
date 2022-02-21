package org.spoorn.myloot.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ViewerCountManager;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spoorn.myloot.mixin.ItemStackAccessor;

import javax.annotation.Nullable;
import java.util.*;

public abstract class AbstractMyLootContainerBlockEntity extends ChestBlockEntity {

    protected static final String NBT_KEY = "myLoot";

    protected Map<String, MyLootInventory> inventories = new HashMap<>();
    protected final Set<String> playersOpened = new HashSet<>();

    public final ViewerCountManager stateManager = new ViewerCountManager(){

        @Override
        protected void onContainerOpen(World world, BlockPos pos, BlockState state) {
            MyLootChestBlockEntity.playSound(world, pos, state, SoundEvents.BLOCK_CHEST_OPEN);
        }

        @Override
        protected void onContainerClose(World world, BlockPos pos, BlockState state) {
            MyLootChestBlockEntity.playSound(world, pos, state, SoundEvents.BLOCK_CHEST_CLOSE);
        }

        @Override
        protected void onViewerCountUpdate(World world, BlockPos pos, BlockState state, int oldViewerCount, int newViewerCount) {
            AbstractMyLootContainerBlockEntity.this.onInvOpenOrClose(world, pos, state, oldViewerCount, newViewerCount);
        }

        // This checks if player is viewing their instance of the loot chest.  Otherwise, the chest will instantly close.
        @Override
        protected boolean isPlayerViewing(PlayerEntity player) {
            if (player.currentScreenHandler instanceof GenericContainerScreenHandler) {
                Inventory inventory = ((GenericContainerScreenHandler)player.currentScreenHandler).getInventory();
                Inventory thisInventory = AbstractMyLootContainerBlockEntity.this.inventories.get(player.getGameProfile().getId().toString());
                return thisInventory != null && inventory == thisInventory || inventory instanceof DoubleInventory && ((DoubleInventory)inventory).isPart(thisInventory);
            }
            return false;
        }
    };
    
    protected AbstractMyLootContainerBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    public boolean hasPlayerOpened(PlayerEntity player) {
        return this.playersOpened.contains(player.getGameProfile().getId().toString());
    }

    @Nullable
    public Inventory getPlayerInstancedInventory(PlayerEntity player) {
        return this.inventories.get(player.getGameProfile().getId().toString());
    }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
        PlayerEntity player = playerInventory.player;
        String playerId = player.getGameProfile().getId().toString();
        MyLootInventory myLootInventory;
        if (!this.inventories.containsKey(playerId)) {
            DefaultedList<ItemStack> clonedList = DefaultedList.ofSize(27, ItemStack.EMPTY);
            DefaultedList<ItemStack> defaultList = this.getInvStackList();
            for (int i = 0; i < defaultList.size(); ++i) {
                ItemStack defaultItemStack = defaultList.get(i);
                clonedList.set(i, ItemStackAccessor.create(defaultItemStack.getItem(), defaultItemStack.getCount(), Optional.ofNullable(defaultItemStack.getNbt())));
            }
            myLootInventory = new MyLootInventory(clonedList, this);
            this.inventories.put(playerId, myLootInventory);
        } else {
            myLootInventory = this.inventories.get(playerId);
        }
        return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, myLootInventory);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.inventories.clear();
        this.playersOpened.clear();
        if (!this.deserializeLootTable(nbt)) {
            NbtCompound root = nbt.getCompound(NBT_KEY);
            // Inventories
            for (String playerId : root.getKeys()) {
                NbtCompound sub = root.getCompound(playerId);
                MyLootInventory inventory = new MyLootInventory(this);
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
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (!this.serializeLootTable(nbt)) {
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
    }

    @Override
    public void onOpen(PlayerEntity player) {
        if (!this.removed) {
            if (!player.isSpectator()) {
                this.stateManager.openContainer(player, this.getWorld(), this.getPos(), this.getCachedState());
            }

            String playerId = player.getGameProfile().getId().toString();
            if (!this.playersOpened.contains(playerId)) {
                this.playersOpened.add(playerId);
                this.markDirty();
                if (this.world != null) {
                    // Force sync with clients
                    this.world.updateListeners(this.getPos(), this.getCachedState(), this.getCachedState(), Block.NOTIFY_ALL);
                }
            }
        }
    }

    // Allow syncing to client
    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public void onClose(PlayerEntity player) {
        if (!this.removed && !player.isSpectator()) {
            this.stateManager.closeContainer(player, this.getWorld(), this.getPos(), this.getCachedState());
        }
    }

    @Override
    public void onScheduledTick() {
        if (!this.removed) {
            this.stateManager.updateViewerCount(this.getWorld(), this.getPos(), this.getCachedState());
        }
    }

    @Override
    public void clear() {
        this.inventories.clear();
    }

    @Override
    protected abstract Text getContainerName();

    public Map<String, MyLootInventory> getInventories() {
        return this.inventories;
    }

    public void setInventories(Map<String, MyLootInventory> inventories) {
        this.inventories = inventories;
    }

    static void playSound(World world, BlockPos pos, BlockState state, SoundEvent soundEvent) {
        ChestType chestType = state.get(ChestBlock.CHEST_TYPE);
        if (chestType == ChestType.LEFT) {
            return;
        }
        double d = (double)pos.getX() + 0.5;
        double e = (double)pos.getY() + 0.5;
        double f = (double)pos.getZ() + 0.5;
        if (chestType == ChestType.RIGHT) {
            Direction direction = ChestBlock.getFacing(state);
            d += (double)direction.getOffsetX() * 0.5;
            f += (double)direction.getOffsetZ() * 0.5;
        }
        world.playSound(null, d, e, f, soundEvent, SoundCategory.BLOCKS, 0.5f, world.random.nextFloat() * 0.1f + 0.9f);
    }
}