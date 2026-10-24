package dev.kubejseditor.content;

import dev.kubejseditor.ModMenuTypes;
import dev.kubejseditor.model.StorageOptions;
import dev.latvian.mods.kubejs.block.entity.KubeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Storage slots are synchronized projections. Only server inventory methods change logical totals. */
public final class StorageMenu extends AbstractContainerMenu {
    public final int rows,columns,capacity,storageSlots;
    public final BlockPos position;
    private final StorageAttachment storage;
    private final SimpleContainer display;
    private final ContainerData quantities;
    private final Set<Integer> dragSlots=new LinkedHashSet<>();
    private int dragMode=-1;
    public StorageMenu(int id,Inventory player,RegistryFriendlyByteBuf buf){this(id,player,null,buf.readBlockPos(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt());}
    public StorageMenu(int id,Inventory player,StorageAttachment storage){this(id,player,storage,storage.entity.getBlockPos(),storage.rows,storage.columns,storage.capacity);}
    private StorageMenu(int id,Inventory player,StorageAttachment storage,BlockPos position,int rows,int columns,int capacity){
        super(ModMenuTypes.STORAGE.get(),id);var options=new StorageOptions();options.rows=rows;options.columns=columns;options.slotCapacity=capacity;options.validate();
        this.storage=storage;this.position=position;this.rows=rows;this.columns=columns;this.capacity=capacity;storageSlots=rows*columns;
        display=new SimpleContainer(storageSlots);quantities=new SimpleContainerData(storageSlots);
        int width=Math.max(176,14+columns*18),start=(width-columns*18)/2;
        for(int r=0;r<rows;r++)for(int c=0;c<columns;c++)addSlot(new Slot(display,r*columns+c,start+c*18,18+r*18));
        int playerX=(width-162)/2,playerY=32+rows*18;
        for(int r=0;r<3;r++)for(int c=0;c<9;c++)addSlot(new Slot(player,c+r*9+9,playerX+c*18,playerY+r*18));
        for(int c=0;c<9;c++)addSlot(new Slot(player,c,playerX+c*18,playerY+58));
        addDataSlots(quantities);refresh();
    }
    public int quantity(int slot){return quantities.get(slot);}
    public int physicalLimit(ItemStack stack){return stack.getMaxStackSize()==1?1:capacity;}
    /** Client-only layout changes replace immutable Slot coordinates without changing slot indexes. */
    public void layout(int visibleRows,int offset){
        int width=Math.max(176,14+columns*18),start=(width-columns*18)/2;
        for(int i=0;i<slots.size();i++){
            Slot old=slots.get(i);int x,y;boolean active;
            if(i<storageSlots){int row=i/columns;active=row>=offset&&row<offset+visibleRows;x=start+(i%columns)*18;y=active?18+(row-offset)*18:-10000;}
            else{int n=i-storageSlots;active=true;x=(width-162)/2+(n%9)*18;y=32+visibleRows*18+(n<27?(n/9)*18:58);}
            final boolean enabled=active;Slot next=new Slot(old.container,old.getContainerSlot(),x,y){@Override public boolean isActive(){return enabled;}};next.index=i;slots.set(i,next);
        }
    }
    private void refresh(){if(storage!=null)for(int i=0;i<storageSlots;i++){display.setItem(i,storage.inventory.getStackInSlot(i));quantities.set(i,storage.inventory.count(i));}}
    @Override public void broadcastChanges(){refresh();super.broadcastChanges();}
    @Override public boolean stillValid(Player player){return player.distanceToSqr(position.getX()+.5,position.getY()+.5,position.getZ()+.5)<=64&&(storage==null||player.level().getBlockEntity(position)==storage.entity);}
    private boolean validSlot(int id){return id>=0&&id<slots.size();}
    private ItemStack take(int id,int amount){return storage.inventory.extractItem(id,amount,false);}
    private ItemStack put(int id,ItemStack stack){return storage.inventory.insertItem(id,stack,false);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(storage==null||!validSlot(index))return ItemStack.EMPTY;
        ItemStack original=slots.get(index).getItem().copy();
        if(index<storageSlots){
            while(storage.inventory.count(index)>0){ItemStack source=storage.inventory.extractItem(index,Integer.MAX_VALUE,true),left=source.copy();
                moveItemStackTo(left,storageSlots,slots.size(),true);int moved=source.getCount()-left.getCount();if(moved==0)break;take(index,moved);
            }
        }else{
            var slot=slots.get(index);ItemStack left=slot.getItem().copy();
            for(int pass=0;pass<2&&!left.isEmpty();pass++)for(int i=0;i<storageSlots&&!left.isEmpty();i++)if((storage.inventory.count(i)==0)==(pass==1))left=put(i,left);
            slot.setByPlayer(left);slot.setChanged();
        }
        refresh();return original;
    }
    @Override public void clicked(int id,int button,ClickType type,Player player){
        if(storage==null)return;
        if(!stillValid(player))return;
        if(type==ClickType.QUICK_CRAFT){drag(id,button,player);refresh();broadcastChanges();return;}
        dragSlots.clear();dragMode=-1;
        if(type==ClickType.QUICK_MOVE){quickMoveStack(player,id);broadcastChanges();return;}
        if(type==ClickType.PICKUP_ALL){
            var carried=getCarried();if(!carried.isEmpty())for(int i=0;i<slots.size()&&carried.getCount()<carried.getMaxStackSize();i++){
                var found=i<storageSlots?storage.inventory.getStackInSlot(i):slots.get(i).getItem();
                if(ItemStack.isSameItemSameComponents(carried,found)){int wanted=carried.getMaxStackSize()-carried.getCount();var extracted=i<storageSlots?take(i,wanted):slots.get(i).remove(wanted);carried.grow(extracted.getCount());}
            }
            setCarried(carried);broadcastChanges();return;
        }
        if(id<0||id>=storageSlots){super.clicked(id,button,type,player);broadcastChanges();return;}
        var current=storage.inventory.getStackInSlot(id);var carried=getCarried();
        switch(type){
            case PICKUP -> {
                if(button!=0&&button!=1)return;
                if(carried.isEmpty()){int amount=button==1?(storage.inventory.count(id)+1)/2:Integer.MAX_VALUE;setCarried(take(id,amount));}
                else if(current.isEmpty()||ItemStack.isSameItemSameComponents(current,carried)){
                    int offered=button==1?1:carried.getCount();ItemStack left=put(id,carried.copyWithCount(offered));carried.shrink(offered-left.getCount());setCarried(carried);
                }else if(storage.inventory.count(id)<=current.getMaxStackSize()&&carried.getCount()<=physicalLimit(carried)){
                    ItemStack removed=take(id,Integer.MAX_VALUE);put(id,carried);setCarried(removed);
                }
            }
            case SWAP -> {
                if(button<0||button>8&&button!=40)return;
                ItemStack hotbar=player.getInventory().getItem(button);
                if(storage.inventory.count(id)<=current.getMaxStackSize()&&(hotbar.isEmpty()||hotbar.getCount()<=physicalLimit(hotbar))){
                    ItemStack removed=take(id,Integer.MAX_VALUE);if(!hotbar.isEmpty())put(id,hotbar);player.getInventory().setItem(button,removed);
                }
            }
            case THROW -> {if(getCarried().isEmpty()&&(button==0||button==1))player.drop(take(id,button==0?1:Integer.MAX_VALUE),true);}
            case CLONE -> {if(player.getAbilities().instabuild&&getCarried().isEmpty()&&!current.isEmpty())setCarried(current.copyWithCount(current.getMaxStackSize()));}
            default -> {}
        }
        broadcastChanges();
    }
    private void drag(int id,int button,Player player){
        int stage=getQuickcraftHeader(button),mode=getQuickcraftType(button);
        if(stage==0){dragSlots.clear();dragMode=(mode==0||mode==1||mode==2&&player.getAbilities().instabuild)?mode:-1;return;}
        if(dragMode<0||mode!=dragMode||getCarried().isEmpty()){dragSlots.clear();dragMode=-1;return;}
        if(stage==1&&validSlot(id)){
            ItemStack at=slots.get(id).getItem();if(at.isEmpty()||ItemStack.isSameItemSameComponents(at,getCarried()))dragSlots.add(id);return;
        }
        if(stage==2){
            ItemStack carried=getCarried();int each=dragMode==0?carried.getCount()/Math.max(1,dragSlots.size()):1;
            for(int slotId:dragSlots){if(carried.isEmpty()&&dragMode!=2)break;int offered=dragMode==2?carried.getMaxStackSize():Math.min(each,carried.getCount());if(offered<=0)continue;
                ItemStack part=carried.copyWithCount(offered);int accepted;
                if(slotId<storageSlots){var left=put(slotId,part);accepted=offered-left.getCount();}
                else{var slot=slots.get(slotId);var at=slot.getItem();if(!at.isEmpty()&&!ItemStack.isSameItemSameComponents(at,part))continue;
                    accepted=Math.min(offered,Math.max(0,slot.getMaxStackSize(part)-at.getCount()));if(accepted>0)slot.setByPlayer(part.copyWithCount(at.getCount()+accepted));}
                if(dragMode!=2)carried.shrink(accepted);
            }
            setCarried(carried);dragSlots.clear();dragMode=-1;
        }
    }
}
