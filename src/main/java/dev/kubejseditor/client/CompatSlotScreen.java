package dev.kubejseditor.client;

import com.google.gson.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.recipe.RecipeCanvas;
import dev.kubejseditor.runtime.EditorRuntime;
import dev.kubejseditor.storage.ProjectStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

public final class CompatSlotScreen extends Screen {
    private final Screen parent;private final RecipePresentation.Binding binding;private final Consumer<JsonObject> accept;private JsonObject value;private EditBox identity,quantity,chance;private String status="物品 ID / #标签；数量与概率按原生配方保存";
    public CompatSlotScreen(Screen parent,RecipePresentation.Binding binding,JsonObject value,Consumer<JsonObject> accept){super(Component.literal(binding.field().label+" · "+binding.path()));this.parent=parent;this.binding=binding;this.value=value.deepCopy();this.accept=accept;}
    private String id(){for(String k:List.of("tag","items","id","item","fluid"))if(value.has(k))return (k.equals("tag")?"#":"")+value.get(k).getAsString();return "";}
    private EditBox input(String label,String text,int y){var box=new EditBox(font,width/2-120,y,240,20,Component.literal(label));box.setMaxLength(10000);box.setValue(text);addRenderableWidget(box);return box;}
    @Override protected void init(){identity=input("物品 / 流体 / 标签",id(),62);quantity=input(binding.fluid()?"流体数量 (mB)":"数量",value.get(binding.fluid()?"amount":"count").getAsString(),108);quantity.active=!binding.field().quantity.isBlank();if(!binding.field().probability.isBlank())chance=input("概率 0～1",value.has("chance")?value.get("chance").getAsString():"1",154);
        addRenderableWidget(Button.builder(Component.literal("从注册表选择"),b->{var slot=new RecipeCanvas.Slot(0,0,"资源",binding.output(),binding.fluid(),()->value,v->{});minecraft.setScreen(new ChoiceScreen(this,"选择物品或流体",SlotChoices.resources(slot,obj->{value=CompatibilityScreen.canonical(obj,binding);minecraft.setScreen(this);})) );}).bounds(width/2+126,62,112,20).build());
        addRenderableWidget(Button.builder(Component.literal("编辑组件 / 高级 JSON"),b->minecraft.setScreen(new CompatValueScreen(this,"槽位 JSON（标签与特殊原料不会展开成示例物品）",ProjectStore.JSON.toJson(value),text->{value=JsonParser.parseString(text).getAsJsonObject();}))).bounds(width/2-120,196,240,20).build());
        if(!binding.output()&&binding.field().kind==CompatDefinition.Kind.ITEM)addRenderableWidget(Button.builder(Component.literal("任意一种（OR）"),b->{var next=value.deepCopy();next.addProperty("count",new java.math.BigDecimal(quantity.getValue()));minecraft.setScreen(new CompatAlternativesScreen(parent,binding,next,accept));}).bounds(width/2-120,222,240,20).build());
        addRenderableWidget(Button.builder(Component.literal("保存"),b->{try{var next=value.deepCopy();for(String k:List.of("id","item","fluid","tag","items"))next.remove(k);String id=identity.getValue().trim();dev.kubejseditor.model.EditorProject.requireId(id.startsWith("#")?id.substring(1):id);String key=id.startsWith("#")?"tag":value.has("items")?"items":binding.output()?"id":binding.fluid()?"fluid":"item";next.addProperty(key,id.startsWith("#")?id.substring(1):id);var count=new java.math.BigDecimal(quantity.getValue());if(count.signum()<1||count.stripTrailingZeros().scale()>0)throw new IllegalArgumentException("数量必须为正整数");next.addProperty(binding.fluid()?"amount":"count",count);if(chance!=null)next.addProperty("chance",new java.math.BigDecimal(chance.getValue()));accept.accept(next);minecraft.setScreen(parent);}catch(Exception e){status="错误："+e.getMessage();}}).bounds(width/2-120,height-34,112,20).build());addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(width/2+8,height-34,112,20).build());}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF101923);g.drawString(font,title,16,14,0xFF7DD3FC,false);g.drawString(font,"物品 / 流体 / 标签 ID",width/2-120,48,0xFFCBD5E1,false);g.drawString(font,binding.fluid()?"数量 (mB)":"数量",width/2-120,94,0xFFCBD5E1,false);if(chance!=null)g.drawString(font,"概率 0～1；输入为 0 时不消耗",width/2-120,140,0xFFCBD5E1,false);g.drawString(font,font.plainSubstrByWidth(status,width-32),16,height-54,0xFFFFCC88,false);super.render(g,x,y,partial);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
