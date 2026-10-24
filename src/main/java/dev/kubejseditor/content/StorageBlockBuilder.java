package dev.kubejseditor.content;

import dev.latvian.mods.kubejs.block.custom.BasicKubeBlock;
import dev.latvian.mods.kubejs.block.entity.KubeBlockEntity;
import dev.kubejseditor.model.StorageOptions;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import java.util.EnumSet;

public final class StorageBlockBuilder extends BasicKubeBlock.Builder {
    private final StorageOptions options=new StorageOptions();
    public StorageBlockBuilder(ResourceLocation id){
        super(id);
        blockEntity(info->info.attach(StorageAttachment.KEY,StorageAttachment.TYPE,EnumSet.noneOf(Direction.class),new StorageAttachment.Factory(options)));
        rightClick(event->{if(event.getPlayer() instanceof ServerPlayer player&&event.getBlock().getEntity() instanceof KubeBlockEntity entity&&entity.attachments.get(StorageAttachment.KEY) instanceof StorageAttachment storage){
            player.openMenu(new SimpleMenuProvider((containerId,inventory,p)->new StorageMenu(containerId,inventory,storage),get().getName()),buf->{buf.writeBlockPos(entity.getBlockPos());buf.writeVarInt(storage.rows);buf.writeVarInt(storage.columns);buf.writeVarInt(storage.capacity);});
        }});
    }
    private static int integer(double value){if(!Double.isFinite(value)||value!=Math.rint(value)||value<1||value>4096)throw new IllegalArgumentException("Storage values must be finite positive integers");return (int)value;}
    public StorageBlockBuilder rows(double value){options.rows=integer(value);options.validate();return this;}
    public StorageBlockBuilder columns(double value){options.columns=integer(value);options.validate();return this;}
    public StorageBlockBuilder slotCapacity(double value){options.slotCapacity=integer(value);options.validate();return this;}
}
