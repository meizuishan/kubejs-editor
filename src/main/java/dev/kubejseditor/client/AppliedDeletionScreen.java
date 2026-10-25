package dev.kubejseditor.client;

import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.WorkspaceState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** A shared selection is committed once; opening this page never submits or scans the pack. */
public final class AppliedDeletionScreen extends Screen {
    private final Screen parent;
    private final Set<String> recipes=new LinkedHashSet<>(),items=new LinkedHashSet<>();
    private record Rule(String id,String label,String action,boolean item,boolean eligible){}
    private List<Rule> visible=List.of();
    private String filter="",status="勾选编辑器修改后统一删除；其他待办保留";
    private int mode,offset,left,work,rows;
    private boolean selectedOnly;
    private boolean refreshParent;
    private WorkspaceState.Activation seen;
    public AppliedDeletionScreen(Screen parent){this(parent,null,false);}
    public AppliedDeletionScreen(Screen parent,String selected,boolean item){super(Component.literal("已有修改 · 批量删除"));this.parent=parent;if(selected!=null)(item?items:recipes).add(selected);}
    private Button button(String label,int x,int y,int width,Runnable action){return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,width,20).build());}
    private String displayId(Rule rule){var draft=EditorRuntime.state().applied.recipes.get(rule.id);return !rule.item&&draft!=null&&draft.registryTarget!=null?draft.registryTarget.entry+" · "+draft.registryTarget.registry:rule.id;}
    private boolean selected(Rule rule){return (rule.item?items:recipes).contains(rule.id);}
    private void toggle(Rule rule){var selected=rule.item?items:recipes;if(!selected.remove(rule.id))selected.add(rule.id);rebuildWidgets();}
    private List<Rule> rules(){
        var state=EditorRuntime.state();var all=new ArrayList<Rule>();
        state.applied.recipes.forEach((id,r)->all.add(new Rule(id,RecipeNames.main(r.data),r.actionLabel()+(r.registryTarget==null?"":"（注册表／重启）"),false,state.canRevokeRecipe(id))));
        state.applied.items.forEach((id,i)->all.add(new Rule(id,i.name.isBlank()?id:i.name,i.register?"注册删除（重启）":"属性还原（重启）",true,state.canRevokeItem(id))));
        all.sort(Comparator.comparing(Rule::item).thenComparing(Rule::id));return all;
    }
    @Override protected void init(){
        work=Math.max(270,Math.min(1000,width-124));left=10;rows=Math.max(1,(height-180)/34);seen=EditorRuntime.state().activation;
        var all=rules();recipes.retainAll(all.stream().filter(r->!r.item&&r.eligible).map(Rule::id).toList());items.retainAll(all.stream().filter(r->r.item&&r.eligible).map(Rule::id).toList());
        String query=filter.toLowerCase(Locale.ROOT);visible=all.stream().filter(r->(mode==0||mode==1&&!r.item||mode==2&&r.item)&&(!selectedOnly||selected(r))&&(displayId(r)+" "+r.label+" "+r.action).toLowerCase(Locale.ROOT).contains(query)).toList();offset=Math.max(0,Math.min(offset,Math.max(0,visible.size()-rows)));
        var search=new EditBox(font,left,34,work,18,Component.literal("筛选名称或 ID"));search.setHint(Component.literal("筛选名称或 ID"));search.setValue(filter);search.setResponder(value->{filter=value;offset=0;rebuildWidgets();});addRenderableWidget(search);if(!filter.isEmpty()){setFocused(search);search.setCursorPosition(filter.length());}
        int x=left;for(int n=0;n<3;n++){int value=n;button((mode==n?"● ":"")+new String[]{"全部","配方","物品"}[n],x,58,54,()->{mode=value;offset=0;rebuildWidgets();});x+=58;}
        button(selectedOnly?"显示全部":"仅看已选",x,58,74,()->{selectedOnly=!selectedOnly;offset=0;rebuildWidgets();});
        button("全选筛选结果",left+work-104,10,104,()->{for(var r:visible)if(r.eligible)(r.item?items:recipes).add(r.id);rebuildWidgets();}).active=!EditorRuntime.locked()&&EditorRuntime.canApply();
        for(int n=offset;n<Math.min(visible.size(),offset+rows);n++){
            var rule=visible.get(n);int y=86+(n-offset)*34;boolean enabled=rule.eligible&&!EditorRuntime.locked()&&EditorRuntime.canApply();
            var check=button(selected(rule)?"[x]":"[ ]",left,y+4,26,()->toggle(rule));check.active=enabled;
            var row=button("",left+30,y+1,work-30,()->toggle(rule));row.setHeight(30);row.active=enabled;row.setTooltip(Tooltip.create(Component.literal(rule.label+"\n"+displayId(rule)+"\n"+rule.action+(rule.eligible?"":"\n已转为手写，不能从此页删除"))));
        }
        int count=recipes.size()+items.size();
        button("确认删除 "+count+" 项",left,height-30,112,this::confirm).active=count>0&&!EditorRuntime.locked()&&EditorRuntime.canApply();
        if(work<350&&EditorRuntime.state().activation!=null)button("回滚本批",left+118,height-30,76,this::rollback).active=!EditorRuntime.busy();
        else button("清空选择",left+118,height-30,76,()->{recipes.clear();items.clear();rebuildWidgets();});
        if(work>=350){if(EditorRuntime.state().activation!=null)button("回滚本批",left+200,height-30,76,this::rollback).active=!EditorRuntime.busy();else button("最近耗时",left+200,height-30,76,()->minecraft.setScreen(new TextScreen(this,"最近重载耗时",performance())));}
        button("返回",left+work-58,height-30,58,this::onClose);
    }
    private void confirm(){
        var selectedRecipes=Set.copyOf(recipes);var selectedItems=Set.copyOf(items);
        boolean registryRestart=selectedRecipes.stream().anyMatch(id->EditorRuntime.state().applied.recipes.get(id).registryTarget!=null);boolean unregister=selectedItems.stream().anyMatch(id->EditorRuntime.state().applied.items.get(id).register);String text="删除 "+selectedRecipes.size()+" 条配方修改、"+selectedItems.size()+" 条物品规则。\n新增配方会移除；替换及禁用恢复原来源。\n"+(selectedItems.isEmpty()?(registryRestart?"包含原生注册表条目，统一重启后生效。":"所有选中配方统一重载一次。"):"物品规则与配方统一保存，重启后生效。"+(unregister?"对应注册物品、方块可能消失。":"")+"选中物品的同 ID 待办一并移除。")+"\n其他待办与手写脚本保留，可整批回滚。";
        minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(!yes)return;refreshParent=true;try{status="正在统一提交…";EditorRuntime.revokeChanges(selectedRecipes,selectedItems,result->{status=result.message();if(parent instanceof WorkspaceScreen workspace)workspace.batchResult(result);if(result.success()){recipes.removeAll(selectedRecipes);items.removeAll(selectedItems);}rebuildWidgets();});rebuildWidgets();}catch(Exception error){status="删除未完成："+error.getMessage();minecraft.setScreen(new TextScreen(this,"批量删除详情",status));}},Component.literal("确认批量删除修改"),Component.literal(text),Component.literal("确认删除"),Component.literal("取消")));
    }
    private void rollback(){minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(!yes)return;refreshParent=true;try{EditorRuntime.rollback();if(EditorRuntime.requiresRestart()){status="已回滚，统一等待重启";rebuildWidgets();}else EditorRuntime.reloadResult(false,result->{status=result.message();rebuildWidgets();});}catch(Exception error){status="回滚失败："+error.getMessage();rebuildWidgets();}},Component.literal("回滚本批修改"),Component.literal("恢复本批事务前的文件及待办。"),Component.literal("确认回滚"),Component.literal("取消")));}
    public static String performance(){try{return java.nio.file.Files.readString(EditorRuntime.root().resolve("kubejs/config/kubejs_editor/last-performance.json"))+"\n\n最近一次 JEI 停用至重新就绪："+WorkspaceJeiPlugin.lastRebuildMs+" ms\n\n单位：毫秒。serverReloadMs 为服务端重载；clientQueueWaitMs 为等待客户端处理（可能包含 JEI 重建）；verificationCommitMs 为生效核对与提交；lastPreparationMs 为最近文件准备。";}catch(Exception e){return "尚无本版本的重载耗时记录。";}}
    @Override public void tick(){super.tick();if(seen!=EditorRuntime.state().activation)rebuildWidgets();}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.fill(0,0,width,height,0xFF0B121A);g.drawString(font,title,left,14,0xFF7DD3FC,false);super.render(g,mouseX,mouseY,partial);
        for(int n=offset;n<Math.min(visible.size(),offset+rows);n++){var r=visible.get(n);int y=86+(n-offset)*34;g.drawString(font,font.plainSubstrByWidth(r.action+" · "+r.label,work-42),left+36,y+5,r.eligible?0xFFCBD5E1:0xFF667080,false);g.drawString(font,font.plainSubstrByWidth(displayId(r),work-42),left+36,y+17,0xFF94A3B8,false);}
        if(visible.isEmpty())g.drawString(font,"没有匹配的编辑器修改",left,92,0xFF94A3B8,false);
        var lines=font.split(Component.literal(EditorRuntime.activationError.isBlank()?status:EditorRuntime.activationError),work);for(int n=0;n<Math.min(2,lines.size());n++)g.drawString(font,lines.get(n),left,height-79+n*11,0xFFFDE68A,false);
        g.drawString(font,"已选配方 "+recipes.size()+" / 物品 "+items.size()+" · 筛选结果 "+visible.size(),left,height-48,0xFFCBD5E1,false);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){offset=Math.max(0,offset-(int)dy*3);rebuildWidgets();return true;}
    @Override public void onClose(){if(refreshParent&&parent instanceof WorkspaceScreen workspace)workspace.refreshAfterDeletion();minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
}
