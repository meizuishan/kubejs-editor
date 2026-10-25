package dev.kubejseditor.client;

import com.google.gson.*;
import dev.kubejseditor.recipe.compat.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.util.function.Consumer;

/** Edits existing condition leaves; unknown structure and discriminator fields are preserved. */
public final class CompatConditions {
    public static void open(Screen parent,JsonElement condition,Consumer<JsonElement> accept){var wrapper=new JsonObject();wrapper.add("condition",condition.deepCopy());var choices=new ArrayList<ChoiceScreen.Choice>();collect(parent,wrapper,"/condition",condition,choices,accept,0);Minecraft.getInstance().setScreen(choices.isEmpty()?new TextScreen(parent,"加工条件","此结构没有可用的基础字段，可在高级模式查看原始条件。"):new ChoiceScreen(parent,"条件修改（保留原结构）",choices));}
    private static void collect(Screen parent,JsonObject root,String path,JsonElement value,List<ChoiceScreen.Choice> out,Consumer<JsonElement> accept,int depth){if(depth>10||out.size()>256)return;if(value.isJsonPrimitive()){var p=value.getAsJsonPrimitive();String leaf=path.substring(path.lastIndexOf('/')+1);if(leaf.equals("type"))return;var f=new CompatDefinition.Field(path,leaf,p.isBoolean()?CompatDefinition.Kind.BOOLEAN:p.isNumber()?CompatDefinition.Kind.NUMBER:CompatDefinition.Kind.STRING,CompatDefinition.Role.CONDITION);out.add(new ChoiceScreen.Choice(CompatLabels.name(f)+" = "+p,()->{if(p.isBoolean()){CompatJson.set(root,path,new JsonPrimitive(!p.getAsBoolean()));accept.accept(root.get("condition"));Minecraft.getInstance().setScreen(parent);}else Minecraft.getInstance().setScreen(new CompatValueScreen(parent,CompatLabels.name(f),p.getAsString(),text->{CompatJson.set(root,path,p.isNumber()?new JsonPrimitive(new java.math.BigDecimal(text)):new JsonPrimitive(text));accept.accept(root.get("condition"));}));},path));}else if(value.isJsonObject())for(var e:value.getAsJsonObject().entrySet())collect(parent,root,path+"/"+CompatJson.escape(e.getKey()),e.getValue(),out,accept,depth+1);else if(value.isJsonArray())for(int i=0;i<value.getAsJsonArray().size();i++)collect(parent,root,path+"/"+i,value.getAsJsonArray().get(i),out,accept,depth+1);}
    private CompatConditions(){}
}
