package dev.kubejseditor.content;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import dev.kubejseditor.model.StorageOptions;
import java.util.*;

/** Logical quantities never enter ItemStack codecs, menus, or automation as oversized stacks. */
public final class StorageInventory implements IItemHandler {
    private final ItemStack[] items;
    private final int[] counts;
    private final int capacity;
    private final Runnable changed;
    public StorageInventory(int rows,int columns,int capacity,Runnable changed){
        var options=new StorageOptions();options.rows=rows;options.columns=columns;options.slotCapacity=capacity;options.validate();
        this.capacity=capacity;this.changed=changed;items=new ItemStack[rows*columns];Arrays.fill(items,ItemStack.EMPTY);counts=new int[items.length];
    }
    private void check(int slot){Objects.checkIndex(slot,items.length);}
    public int count(int slot){check(slot);return counts[slot];}
    public int limit(ItemStack stack){return stack.getMaxStackSize()==1?1:capacity;}
    @Override public int getSlots(){return items.length;}
    @Override public ItemStack getStackInSlot(int slot){check(slot);return counts[slot]==0?ItemStack.EMPTY:items[slot].copyWithCount(Math.min(counts[slot],items[slot].getMaxStackSize()));}
    @Override public int getSlotLimit(int slot){check(slot);return capacity;}
    @Override public boolean isItemValid(int slot,ItemStack stack){check(slot);return !stack.isEmpty();}
    @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){
        check(slot);if(stack.isEmpty())return ItemStack.EMPTY;
        if(counts[slot]>0&&!ItemStack.isSameItemSameComponents(items[slot],stack))return stack;
        int accepted=Math.min(stack.getCount(),Math.max(0,limit(stack)-counts[slot]));
        if(accepted>0&&!simulate){items[slot]=stack.copyWithCount(1);counts[slot]+=accepted;changed.run();}
        return accepted==stack.getCount()?ItemStack.EMPTY:stack.copyWithCount(stack.getCount()-accepted);
    }
    @Override public ItemStack extractItem(int slot,int amount,boolean simulate){
        check(slot);if(amount<=0||counts[slot]==0)return ItemStack.EMPTY;
        int taken=Math.min(Math.min(counts[slot],amount),items[slot].getMaxStackSize());ItemStack result=items[slot].copyWithCount(taken);
        if(!simulate){counts[slot]-=taken;if(counts[slot]==0)items[slot]=ItemStack.EMPTY;changed.run();}return result;
    }
    public ListTag save(HolderLookup.Provider registries){
        var data=new ListTag();for(int i=0;i<items.length;i++)if(counts[i]>0){var entry=new CompoundTag();entry.putInt("slot",i);entry.putInt("quantity",counts[i]);entry.put("item",items[i].save(registries));data.add(entry);}return data;
    }
    public void load(HolderLookup.Provider registries,Tag data){
        if(!(data instanceof ListTag list))throw new IllegalArgumentException("Invalid storage inventory data");
        var next=new ItemStack[items.length];Arrays.fill(next,ItemStack.EMPTY);var quantities=new int[items.length];
        for(Tag tag:list){var entry=(CompoundTag)tag;int slot=entry.getInt("slot"),quantity=entry.getInt("quantity");check(slot);
            var stack=ItemStack.parse(registries,entry.getCompound("item")).orElseThrow(()->new IllegalArgumentException("Unknown stored item"));
            if(quantity<1||quantity>limit(stack)||quantities[slot]!=0)throw new IllegalArgumentException("Invalid stored quantity or duplicate slot");
            next[slot]=stack.copyWithCount(1);quantities[slot]=quantity;
        }
        System.arraycopy(next,0,items,0,items.length);System.arraycopy(quantities,0,counts,0,counts.length);
    }
    public void drain(java.util.function.Consumer<ItemStack> output){for(int i=0;i<items.length;i++)while(counts[i]>0)output.accept(extractItem(i,Integer.MAX_VALUE,false));}
}
