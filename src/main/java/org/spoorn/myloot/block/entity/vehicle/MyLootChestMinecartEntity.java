package org.spoorn.myloot.block.entity.vehicle;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spoorn.myloot.block.MyLootBlocks;
import org.spoorn.myloot.block.entity.MyLootContainerBlockEntity;
import org.spoorn.myloot.block.entity.common.MyLootContainerBlockEntityCommon;
import org.spoorn.myloot.entity.MyLootEntities;
import org.spoorn.myloot.item.MyLootItems;
import org.spoorn.myloot.mixin.EntityAccessor;
import org.spoorn.myloot.mixin.StorageMinecartEntityAccessor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class MyLootChestMinecartEntity extends ChestMinecartEntity implements MyLootContainerBlockEntity {

    private static final TrackedData<NbtCompound> PLAYERS_OPENED_DATA = DataTracker.registerData(MyLootChestMinecartEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    
    private final MyLootContainerBlockEntityCommon common = new MyLootContainerBlockEntityCommon(null);
    
    public MyLootChestMinecartEntity(EntityType<? extends ChestMinecartEntity> entityType, World world) {
        super(entityType, world);
        ((EntityAccessor) this).setType(MyLootEntities.MY_LOOT_CHEST_MINECART_ENTITY_TYPE);
    }
    
    public MyLootChestMinecartEntity(World world, double x, double y, double z) {
        super(world, x, y, z);
        ((EntityAccessor) this).setType(MyLootEntities.MY_LOOT_CHEST_MINECART_ENTITY_TYPE);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(PLAYERS_OPENED_DATA, new NbtCompound());
    }

    @Override
    public Text getContainerName() {
        return new TranslatableText("myloot.loot_chest_minecart.container.name");
    }

    public boolean hasPlayerOpened(PlayerEntity player) {
        return this.common.hasPlayerOpened(player);
    }

    @Nullable
    public Inventory getPlayerInstancedInventory(PlayerEntity player) {
        return this.common.getOrCreateNewInstancedInventoryIfAbsent(player, this.getOriginalInventory(), this);
    }

    @Override
    public List<Inventory> getAllInstancedInventories() {
        return new ArrayList<>(this.common.getInventories().values());
    }

    @Override
    public DefaultedList<ItemStack> getOriginalInventory() {
        return ((StorageMinecartEntityAccessor) this).getOriginalStorageInventory();
    }

    @Override
    public DefaultedList<ItemStack> getDefaultLoot() {
        return this.common.getDefaultLoot();
    }

    @Override
    public void setDefaultLoot() {
        this.common.setDefaultLoot(this.getOriginalInventory());
    }

    @Override
    public BlockState getDefaultContainedBlock() {
        checkAndLoadPlayersOpenedTrackedData();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && this.hasPlayerOpened(player)) {
            return MyLootBlocks.OPENED_MY_LOOT_CHEST_BLOCK.getDefaultState().with(ChestBlock.FACING, Direction.NORTH);
        } else {
            return MyLootBlocks.MY_LOOT_CHEST_BLOCK.getDefaultState().with(ChestBlock.FACING, Direction.NORTH);
        }
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!player.world.isClient && this.common.addPlayerOpenedIfAbsent(player)) {
            this.updatePlayersOpenedTrackedData();
        }
        return super.interact(player, hand);
    }

    @Override
    public ScreenHandler getScreenHandler(int syncId, PlayerInventory playerInventory) {
        return this.common.createScreenHandler(syncId, playerInventory, this.getOriginalInventory(), this);
    }

    
    /*
        Nbt data is on the server only.
     */
    
    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.common.readNbt(nbt, this);
        this.updatePlayersOpenedTrackedData();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        this.common.writeNbt(nbt);
        this.updatePlayersOpenedTrackedData();
    }

    /**
     * We update the tracked data every time the data changes on the server side so it gets sent to clients.
     * Since read and write Nbt are all on the server, we update it in both of those methods above.
     */
    private void updatePlayersOpenedTrackedData() {
        NbtCompound root = new NbtCompound();
        this.common.loadPlayersOpenedToNbt(root);
        this.dataTracker.set(PLAYERS_OPENED_DATA, root);
    }
    
    private void checkAndLoadPlayersOpenedTrackedData() {
        if (this.dataTracker.isDirty()) {
            NbtCompound nbt = this.dataTracker.get(PLAYERS_OPENED_DATA);
            this.common.unloadPlayersOpenedFromNbt(nbt);
        }
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(MyLootItems.MY_LOOT_CHEST_MINECART);
    }

    @Override
    public void clear() {
        super.clear();
        this.common.clear();
    }

    /*
       The following methods just invoke the super method, but are required as a multiplayer server will complain at
       runtime that these methods which are part of the MyLootContainerBlockEntity interface aren't implemented.
       Not sure why inheriting superclass methods do not count for interface implementations, but this gets around it.
    */
    @Override
    public void setMyLootLootTable(Identifier id, long seed) {
        super.setLootTable(id, seed);
    }

    @Override
    public void markDirty() {
        super.markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return super.canPlayerUse(player);
    }

    @Override
    public void onClose(PlayerEntity player) {
        super.onClose(player);
    }

    @Override
    public void onOpen(PlayerEntity player) {
        super.onOpen(player);
    }
}
