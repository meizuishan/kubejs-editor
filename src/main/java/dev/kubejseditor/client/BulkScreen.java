package dev.kubejseditor.client;

import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.recipe.RecipeTools;
import dev.kubejseditor.runtime.EditorRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class BulkScreen extends Screen {
    private final Screen parent;private final List<EditorRuntime.Loaded> loaded;
    private final Map<String,String> values=new LinkedHashMap<>();
    private boolean output;private List<EditorRuntime.Loaded> matches=List.of();private String status="填写筛选条件后预览匹配项";private boolean previewed;
    public BulkScreen(Screen parent,List<EditorRuntime.Loaded> loaded){super(Component.literal("批量材料替换"));this.parent=parent;this.loaded=loaded;values.put("模组命名空间（可空）","");values.put("配方类型（可空）","");values.put("包含输入（可空）","");values.put("包含输出（可空）","");values.put("替换原资源 ID / #标签","");values.put("替换目标 ID / #标签","");}
    @Override protected void init(){
        int y=44;for(var e:values.entrySet()){String key=e.getKey();EditBox box=new EditBox(font,width/2-20,y,220,20,Component.literal(key));box.setMaxLength(512);box.setValue(e.getValue());box.setResponder(s->{values.put(key,s);previewed=false;});addRenderableWidget(box);y+=30;}
        addRenderableWidget(Button.builder(Component.literal(output?"方向：输出":"方向：输入"),b->{output=!output;previewed=false;rebuildWidgets();}).bounds(width/2-200,y,126,20).build());
        addRenderableWidget(Button.builder(Component.literal("预览匹配"),b->preview()).bounds(width/2-68,y,126,20).build());
        addRenderableWidget(Button.builder(Component.literal("查看全部匹配"),b->{if(previewed)minecraft.setScreen(new TextScreen(this,"批量替换匹配项",String.join("\n",matches.stream().map(EditorRuntime.Loaded::id).toList())));}).bounds(width/2+64,y,136,20).build());
        addRenderableWidget(Button.builder(Component.literal("加入草稿"),b->{try{
            if(!previewed)throw new IllegalArgumentException("请先预览匹配项");List<String> v=new ArrayList<>(values.values());
            Map<String,EditorProject.RecipeDraft> staged=new LinkedHashMap<>();for(var r:matches){var d=new EditorProject.RecipeDraft(r.id(),RecipeTools.replace(r.data(),v.get(4),v.get(5),output));d.operation="replace";staged.put(d.id,d);}EditorRuntime.project().recipes.putAll(staged);minecraft.setScreen(parent);
        }catch(Exception e){status=e.getMessage();}}).bounds(width/2-130,height-28,126,20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(width/2+4,height-28,126,20).build());
    }
    private void preview(){try{
        List<String> v=new ArrayList<>(values.values());String from=v.get(4),to=v.get(5);
        EditorProject.requireId(from.startsWith("#")?from.substring(1):from);EditorProject.requireId(to.startsWith("#")?to.substring(1):to);
        if(output&&to.startsWith("#"))throw new IllegalArgumentException("输出不能替换为标签");
        matches=loaded.stream().filter(r->r.error()==null&&r.data()!=null)
            .filter(r->v.get(0).isBlank()||r.id().startsWith(v.get(0)+":"))
            .filter(r->v.get(1).isBlank()||r.type().equals(v.get(1)))
            .filter(r->RecipeTools.contains(r.data(),v.get(2),false)&&RecipeTools.contains(r.data(),v.get(3),true)&&RecipeTools.contains(r.data(),from,output)).toList();
        previewed=true;status="匹配 "+matches.size()+" 条配方，加入草稿后仍需通过更改预览应用";
    }catch(Exception e){status=e.getMessage();previewed=false;}}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF111827);g.drawCenteredString(font,title,width/2,14,0xFF7DD3FC);int n=0;for(String key:values.keySet())g.drawString(font,key,width/2-200,50+n++*30,0xFFCBD5E1,false);g.drawCenteredString(font,status,width/2,height-48,0xFF94A3B8);super.render(g,x,y,partial);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
