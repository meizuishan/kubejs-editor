package dev.kubejseditor.client;

import com.google.gson.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.storage.ProjectStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

public final class CompatOtherScreen extends Screen {
    private final Screen parent;private final RecipePresentation.Binding binding;private final JsonElement value;private final Consumer<JsonElement> accept;
    public CompatOtherScreen(Screen parent,RecipePresentation.Binding binding,JsonElement value,Consumer<JsonElement> accept){super(Component.literal("其他原料 · "+CompatLabels.name(binding.field())));this.parent=parent;this.binding=binding;this.value=value;this.accept=accept;}
    @Override protected void init(){addRenderableWidget(Button.builder(Component.literal("从 JEI 类型选择"),b->{var choices=new ArrayList<ChoiceScreen.Choice>();try{for(var item:CompatIngredientTypes.values(binding.field().ingredientType))choices.add(new ChoiceScreen.Choice(String.valueOf(item),()->{accept.accept(CompatIngredientTypes.encode(binding.field().ingredientType,item));minecraft.setScreen(parent);}));minecraft.setScreen(new ChoiceScreen(this,"选择其他原料",choices));}catch(Exception e){minecraft.setScreen(new TextScreen(this,"原料类型不可写",e.getMessage()));}}).bounds(width/2-120,64,240,20).build());addRenderableWidget(Button.builder(Component.literal("高级：原生编码"),b->minecraft.setScreen(new CompatValueScreen(parent,"其他原料编码 · "+binding.field().ingredientType,ProjectStore.JSON.toJson(value),text->{var raw=JsonParser.parseString(text);CompatIngredientTypes.decode(binding.field().ingredientType,raw);accept.accept(raw);}))).bounds(width/2-120,96,240,20).build());addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(width/2-60,height-30,120,20).build());}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF101923);g.drawString(font,title,16,14,0xFF7DD3FC,false);g.drawWordWrap(font,Component.literal("类型："+binding.field().ingredientType+"；缺少有效 Codec 的类型不能写入"),16,140,width-32,0xFFCBD5E1);super.render(g,x,y,partial);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
    @Override public boolean isPauseScreen(){return false;}
}
