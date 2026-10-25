package dev.kubejseditor.client;

import com.google.gson.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.storage.ProjectStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;
import java.util.function.Consumer;

/** Raw typed fields are independent of ingredient mappings. Commit once into the existing transaction. */
public final class FieldPatchScreen extends Screen {
    private final Screen parent;private final JsonObject original;private JsonObject preview;private JsonArray operations=new JsonArray();private final Consumer<JsonObject> accepted;private String query="",errorDetails="",status="选择字段直接替换；物品／流体 ID 可从 JEI 拖入。保存加入待办后统一应用。";private int offset,workWidth;private List<JsonPatches.Field> visible=List.of();private final Map<String,Rect2i> areas=new LinkedHashMap<>();
    public FieldPatchScreen(Screen parent,JsonObject source,Consumer<JsonObject> accepted){super(Component.literal("通用字段补丁"));this.parent=parent;this.original=source.deepCopy();this.preview=source.deepCopy();this.accepted=accepted;}
    private void button(String title,int x,int y,int width,Runnable action){addRenderableWidget(Button.builder(Component.literal(title),b->{try{action.run();}catch(Exception error){status="错误："+error.getMessage()+"；预览差异可复制完整原因";var trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));errorDetails=parent.getTitle().getString()+"\n"+trace;}}).bounds(x,y,width,20).build());}
    @Override protected void init(){workWidth=Math.max(280,width-112);areas.clear();var search=new EditBox(font,16,36,workWidth-32,20,Component.literal("搜索字段或原值"));search.setValue(query);search.setResponder(s->{query=s;offset=0;rebuildWidgets();});addRenderableWidget(search);if(!query.isEmpty())setFocused(search);
        button("补丁 JSON",16,62,90,()->minecraft.setScreen(new CompatValueScreen(this,"补丁操作数组 · test / replace / add / remove / copy / move",ProjectStore.JSON.toJson(operations),text->{var next=JsonParser.parseString(text).getAsJsonArray();preview=JsonPatches.apply(original,next);operations=next.deepCopy();offset=0;})));
        button("撤销一步",110,62,80,()->{if(!operations.isEmpty()){operations.remove(operations.size()-1);preview=JsonPatches.apply(original,operations);rebuildWidgets();}});button("预览差异",194,62,80,()->minecraft.setScreen(new TextScreen(this,"补丁预览 · "+operations.size()+" 项",(errorDetails.isEmpty()?"":"最近错误：\n"+errorDetails+"\n\n")+"操作：\n"+ProjectStore.JSON.toJson(operations)+"\n\n原始：\n"+ProjectStore.JSON.toJson(original)+"\n\n结果：\n"+ProjectStore.JSON.toJson(preview))));
        visible=JsonPatches.fields(preview).stream().filter(f->!f.path().equals("/type")&&(f.path()+" "+f.value()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();int rows=Math.max(1,(height-174)/38);offset=Math.min(offset,Math.max(0,visible.size()-rows));for(int i=offset;i<Math.min(visible.size(),offset+rows);i++){var field=visible.get(i);int y=94+(i-offset)*38;button(font.plainSubstrByWidth(field.path(),workWidth-58),16,y,workWidth-32,()->edit(field));areas.put(field.path(),new Rect2i(16,y,workWidth-32,34));}
        button("检查并保存补丁",16,height-30,130,()->{accepted.accept(preview.deepCopy());minecraft.setScreen(parent);});button("取消",workWidth-80,height-30,64,this::onClose);
    }
    private void edit(JsonPatches.Field field){minecraft.setScreen(new ChoiceScreen(this,field.path(),List.of(new ChoiceScreen.Choice("替换字段值",()->minecraft.setScreen(new CompatValueScreen(this,field.path(),ProjectStore.JSON.toJson(field.value()),text->append(JsonPatches.operation("replace",field.path(),JsonParser.parseString(text)))))),new ChoiceScreen.Choice("批量替换相同值",()->batch(field)),new ChoiceScreen.Choice("删除字段或列表条目",()->append(JsonPatches.operation("remove",field.path(),null))),new ChoiceScreen.Choice("增加子字段 / 列表条目",()->minecraft.setScreen(new CompatValueScreen(this,"新增操作：path 指定子字段；数组用 /- 追加","{\"op\":\"add\",\"path\":"+ProjectStore.JSON.toJson(field.path()+(field.value().isJsonArray()?"/-":"/new_field"))+",\"value\":null}",text->append(JsonParser.parseString(text).getAsJsonObject())))))));}
    public void append(JsonObject operation){var next=operations.deepCopy();next.add(operation);var updated=JsonPatches.apply(original,next);operations=next;preview=updated;errorDetails="";status="已暂存 "+operations.size()+" 项字段补丁，检查并保存后加入配方待办";}
    private void batch(JsonPatches.Field field){var request=new JsonObject();request.addProperty("scope",field.path().substring(0,field.path().lastIndexOf('/')));request.add("from",field.value());request.add("to",field.value());minecraft.setScreen(new CompatValueScreen(this,"按原值批量替换；scope 为空表示整条配方",ProjectStore.JSON.toJson(request),text->{var value=JsonParser.parseString(text).getAsJsonObject();var more=JsonPatches.replaceValues(preview,value.get("scope").getAsString(),value.get("from"),value.get("to"));var next=operations.deepCopy();next.addAll(more);var updated=JsonPatches.apply(original,next);operations=next;preview=updated;status="已加入 "+more.size()+" 项精确值替换，可预览或撤销";}));}
    public List<WorkspaceScreen.GhostTarget> ghostTargets(Object value){var result=new ArrayList<WorkspaceScreen.GhostTarget>();boolean fluid=value instanceof FluidStack;if(!fluid&&!(value instanceof ItemStack))return result;for(var entry:areas.entrySet()){var old=JsonPatches.read(preview,entry.getKey());if(!old.isJsonPrimitive()||!old.getAsJsonPrimitive().isString())continue;var id=ResourceLocation.tryParse(old.getAsString());if(id==null||!(fluid?BuiltInRegistries.FLUID.containsKey(id):BuiltInRegistries.ITEM.containsKey(id)))continue;result.add(new WorkspaceScreen.GhostTarget(entry.getValue(),fluid,dropped->{String replacement=fluid?BuiltInRegistries.FLUID.getKey(((FluidStack)dropped).getFluid()).toString():BuiltInRegistries.ITEM.getKey(((ItemStack)dropped).getItem()).toString();append(JsonPatches.operation("replace",entry.getKey(),new JsonPrimitive(replacement)));rebuildWidgets();}));}return result;}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF101923);g.drawString(font,title,16,14,0xFF7DD3FC,false);super.render(g,x,y,partial);int rows=Math.max(1,(height-174)/38);for(int i=offset;i<Math.min(visible.size(),offset+rows);i++)g.drawString(font,font.plainSubstrByWidth(visible.get(i).value().toString(),workWidth-40),20,117+(i-offset)*38,0xFF94A3B8,false);g.drawString(font,font.plainSubstrByWidth(status,workWidth-32),16,height-52,0xFFFFCC88,false);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){offset=Math.max(0,offset-(int)dy*3);rebuildWidgets();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
