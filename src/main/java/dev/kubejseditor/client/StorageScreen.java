package dev.kubejseditor.client;

import dev.kubejseditor.content.StorageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class StorageScreen extends AbstractContainerScreen<StorageMenu> {
    private int visibleRows,rowOffset;
    public StorageScreen(StorageMenu menu,Inventory player,Component title){super(menu,player,title);}
    @Override protected void init(){
        visibleRows=Math.max(1,Math.min(menu.rows,(height-128)/18));rowOffset=Math.min(rowOffset,menu.rows-visibleRows);
        imageWidth=Math.max(176,14+menu.columns*18);imageHeight=114+visibleRows*18;
        super.init();inventoryLabelX=(imageWidth-162)/2;inventoryLabelY=21+visibleRows*18;menu.layout(visibleRows,rowOffset);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.fill(leftPos-1,topPos-1,leftPos+imageWidth+1,topPos+imageHeight+1,0xFF64748B);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF192536);
        for(Slot slot:menu.slots)if(slot.isActive()){int x=leftPos+slot.x,y=topPos+slot.y;g.fill(x-1,y-1,x+17,y+17,0xFF0B1320);g.fill(x,y,x+16,y+16,0xFF35445B);}
        if(visibleRows<menu.rows){int y=topPos+18+(visibleRows*18-8)*rowOffset/Math.max(1,menu.rows-visibleRows);g.fill(leftPos+imageWidth-4,topPos+18,leftPos+imageWidth-2,topPos+18+visibleRows*18,0xFF475569);g.fill(leftPos+imageWidth-4,y,leftPos+imageWidth-2,y+8,0xFF38BDF8);}
    }
    @Override protected void renderSlotContents(GuiGraphics g,ItemStack stack,Slot slot,String countString){
        if(slot.index>=menu.storageSlots){super.renderSlotContents(g,stack,slot,countString);return;}
        if(!slot.isActive()||stack.isEmpty())return;
        g.renderItem(stack,slot.x,slot.y);int count=menu.quantity(slot.index);String text=Integer.toString(count);
        float scale=text.length()>2?.65f:1;g.pose().pushPose();g.pose().translate(slot.x+17,slot.y+17,220);g.pose().scale(scale,scale,1);g.drawString(font,text,-font.width(text),-8,0xFFFFFFFF,true);g.pose().popPose();
    }
    @Override protected List<Component> getTooltipFromContainerItem(ItemStack stack){
        var lines=new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if(hoveredSlot!=null&&hoveredSlot.index<menu.storageSlots){lines.add(Component.translatable("storage.kubejs_editor.quantity",menu.quantity(hoveredSlot.index),menu.physicalLimit(stack)));}
        return lines;
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){g.drawString(font,font.plainSubstrByWidth(title.getString(),imageWidth-65),titleLabelX,titleLabelY,0xFFE2E8F0,false);g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xFFCBD5E1,false);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(visibleRows<menu.rows&&x>=leftPos&&x<leftPos+imageWidth&&y>=topPos+18&&y<topPos+18+visibleRows*18){rowOffset=Math.clamp(rowOffset-(int)Math.signum(dy),0,menu.rows-visibleRows);menu.layout(visibleRows,rowOffset);return true;}
        return super.mouseScrolled(x,y,dx,dy);
    }
}
