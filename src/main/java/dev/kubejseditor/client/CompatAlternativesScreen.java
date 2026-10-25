package dev.kubejseditor.client;

import com.google.gson.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.storage.ProjectStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Alternatives remain one consuming ingredient; they never become multiple required slots. */
public final class CompatAlternativesScreen extends Screen {
    private final Screen parent;private final RecipePresentation.Binding binding;private final Consumer<JsonObject> accept;private final JsonObject original;private JsonArray candidates;private EditBox quantity;private int offset;private String status="每次消耗任意一种候选；标签、组件和公共数量按原生编码保存";
    public CompatAlternativesScreen(Screen parent,RecipePresentation.Binding binding,JsonObject original,Consumer<JsonObject> accept){super(Component.literal("任意一种（OR） · "+CompatLabels.name(binding.field())));this.parent=parent;this.binding=binding;this.original=original.deepCopy();this.accept=accept;if(original.has("alternatives"))candidates=original.getAsJsonArray("alternatives").deepCopy();else{candidates=new JsonArray();var item=original.deepCopy();item.remove("count");item.remove("chance");candidates.add(item);}}
    private RecipePresentation.Binding singleBinding(){var f=new CompatDefinition.Field(binding.field().path,"候选原料",CompatDefinition.Kind.ITEM,CompatDefinition.Role.INPUT);return new RecipePresentation.Binding(f,binding.path(),0);}
    private void button(String title,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(title),b->{try{action.run();}catch(Exception e){status=e.getMessage();}}).bounds(x,y,w,20).build());}
    @Override protected void init(){String count=quantity==null?original.get("count").getAsString():quantity.getValue();quantity=new EditBox(font,16,36,96,20,Component.literal("数量"));quantity.setValue(count);quantity.active=!binding.field().quantity.isBlank();addRenderableWidget(quantity);button("+ 添加候选",120,36,112,()->{var item=new JsonObject();item.addProperty("item","minecraft:stone");item.addProperty("count",1);minecraft.setScreen(new CompatSlotScreen(this,singleBinding(),item,value->{value.remove("count");value.remove("chance");candidates.add(value);rebuildWidgets();}));});int rows=Math.max(1,(height-150)/26);offset=Math.min(offset,Math.max(0,candidates.size()-rows));for(int i=offset;i<Math.min(candidates.size(),offset+rows);i++){int index=i;var value=candidates.get(i);button("候选 "+(i+1)+" · "+value,16,70+(i-offset)*26,width-80,()->{if(!value.isJsonObject()){minecraft.setScreen(new CompatValueScreen(this,"高级候选",ProjectStore.JSON.toJson(value),text->candidates.set(index,JsonParser.parseString(text))));return;}var item=value.getAsJsonObject().deepCopy();item.addProperty("count",1);minecraft.setScreen(new CompatSlotScreen(this,singleBinding(),item,next->{next.remove("count");next.remove("chance");candidates.set(index,next);rebuildWidgets();}));});button("×",width-52,70+(i-offset)*26,36,()->{if(candidates.size()==1)throw new IllegalArgumentException("至少保留一个候选");candidates.remove(index);rebuildWidgets();});}button("保存",16,height-32,96,()->{var next=original.deepCopy();next.add("alternatives",candidates.deepCopy());next.addProperty("count",new java.math.BigDecimal(quantity.getValue()));accept.accept(next);minecraft.setScreen(parent);});button("取消",width-112,height-32,96,this::onClose);}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF101923);g.drawString(font,title,16,14,0xFF7DD3FC,false);g.drawString(font,font.plainSubstrByWidth(status,width-32),16,height-54,0xFFFFCC88,false);super.render(g,x,y,partial);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){offset=Math.max(0,offset-(int)dy*2);rebuildWidgets();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
    @Override public boolean isPauseScreen(){return false;}
}
