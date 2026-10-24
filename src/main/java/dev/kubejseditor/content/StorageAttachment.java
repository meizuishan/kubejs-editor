package dev.kubejseditor.content;

import dev.latvian.mods.kubejs.block.entity.*;
import dev.kubejseditor.model.StorageOptions;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.*;
import java.util.List;

public final class StorageAttachment implements BlockEntityAttachment {
    public static final String KEY="inventory";
    public static final BlockEntityAttachmentType TYPE=new BlockEntityAttachmentType(ResourceLocation.parse("kubejs_editor:storage"),Factory.class);
    public record Factory(StorageOptions options) implements BlockEntityAttachmentFactory {
        @Override public BlockEntityAttachment create(BlockEntityAttachmentInfo info,KubeBlockEntity entity){return new StorageAttachment(entity,options);}
        @Override public List<BlockCapability<?,?>> getCapabilities(){return List.of(Capabilities.ItemHandler.BLOCK);}
    }
    public final KubeBlockEntity entity;
    public final StorageInventory inventory;
    public final int rows,columns,capacity;
    public StorageAttachment(KubeBlockEntity entity,StorageOptions options){
        this.entity=entity;options.validate();rows=options.rows;columns=options.columns;capacity=options.slotCapacity;
        inventory=new StorageInventory(rows,columns,capacity,entity::save);
    }
    @Override public Object getWrappedObject(){return this;}
    @SuppressWarnings("unchecked") @Override public <C,S> C getCapability(BlockCapability<C,S> capability){return capability==Capabilities.ItemHandler.BLOCK?(C)inventory:null;}
    @Override public Tag serialize(HolderLookup.Provider registries){return inventory.save(registries);}
    @Override public void deserialize(HolderLookup.Provider registries,Tag data){inventory.load(registries,data);}
    @Override public void onRemove(ServerLevel level,KubeBlockEntity entity,BlockState state){inventory.drain(stack->Containers.dropItemStack(level,entity.getBlockPos().getX()+.5,entity.getBlockPos().getY()+.5,entity.getBlockPos().getZ()+.5,stack));}
}
