package dev.kubejseditor.client;

import com.google.gson.*;
import dev.kubejseditor.config.CreateConfigs;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.model.ItemKinds;
import dev.kubejseditor.model.BlockKinds;
import dev.kubejseditor.model.PendingOperation;
import dev.kubejseditor.runtime.ValidationIssue;
import dev.kubejseditor.model.EditorProject.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.EditorRuntime;
import dev.kubejseditor.runtime.EditorCompatibility;
import dev.kubejseditor.runtime.EditorResources;
import dev.kubejseditor.storage.*;
import dev.kubejseditor.runtime.EditorScripts;
import dev.kubejseditor.runtime.ScriptIndex;
import dev.kubejseditor.script.CodePreview;
import dev.kubejseditor.module.EditorModules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Native three-pane authoring screen. No inventory slots or recipe manager mutation. */
public final class WorkspaceScreen extends Screen {
    public record GhostTarget(Rect2i area,boolean fluid,Consumer<Object> accept) {}
    private record Row(String label,String detail,Runnable select,PendingOperation operation) {
        Row(String label,Runnable select){this(label,label,select,null);}
        Row(String label,String detail,Runnable select){this(label,detail,select,null);}
    }
    private record Form(String label,String value,Consumer<String> set,List<String> choices,Runnable action) {
        Form(String label,String value,Consumer<String> set,List<String> choices){this(label,value,set,choices,null);}
    }
    private final EditorProject project;
    private final PanelPreferences panels;
    private int draggingPanel;
    private List<EditorRuntime.Loaded> loaded=List.of();
    private boolean catalogueLoaded;
    private List<EditorRuntime.Loaded> labelledRecipes;
    private Map<String,String> loadedNames=Map.of();
    private int tab,createTab,listOffset,propertyOffset,canvasScroll,canvasScrollX,selectedStep=-1;
    private boolean browseLoaded,dirty,readOnly,compact;
    private String filter="",status="未应用内容自动保存 · 一键应用提交全部操作";
    private RecipeDraft recipe;
    private ItemDraft item;
    private RecipeCanvas.Slot selectedSlot;
    private CreateConfigs.Entry selectedConfig;
    private List<CreateConfigs.Entry> configs=List.of();
    private boolean configsLoaded;
    private List<Row> rows=List.of();
    private final List<Form> form=new ArrayList<>();
    private final List<GhostTarget> ghosts=new ArrayList<>();
    private final Map<String,String> invalidFields=new LinkedHashMap<>();
    private List<RecipeCanvas.Slot> slots=List.of();
    private String recipeIdText="",itemIdText="";private String configText="";
    private String appliedRecipeSelection;
    private FileTransaction.Review review;
    private int reviewIndex;
    private int reviewMode,codeIndex;
    private List<CodePreview.Page> codePages=List.of();
    private List<ValidationIssue> issues=List.of();
    private int autosaveTicks;
    private WorkspaceState.Activation activationSeen;
    private CanvasFit canvasFit=new CanvasFit(0,0,1);
    private boolean draggingSequence;
    private int sequenceScrollMax,sequenceScrollYMax;
    private int leftPos,topPos,imageWidth,imageHeight,existingMode;
    private String stationFilter="";
    private Screen returnTo;
    private int listX,listW,canvasX,canvasW,propX,propW,bodyY,bottom,formTop;
    public WorkspaceScreen(){
        super(Component.literal("KubeJS Editor"));
        project=EditorRuntime.project();panels=PanelPreferences.read(EditorRuntime.root());refreshRuntime();
    }
    public static void openRecipe(String id,Screen previous){
        WorkspaceScreen screen=new WorkspaceScreen();screen.returnTo=previous;
        var match=EditorRuntime.recipe(id);if(match==null)throw new IllegalArgumentException("此配方已变更，请重新打开 JEI");screen.loaded=List.of(match);
        screen.tab=RecipeAdapters.get(match.type())==null||RecipeAdapters.get(match.type()) instanceof SimpleRecipeAdapter?5:1;screen.browseLoaded=true;
        Minecraft.getInstance().setScreen(screen);screen.selectLoaded(match);
    }
    public Rect2i guiBounds(){return new Rect2i(leftPos,topPos,imageWidth,imageHeight);}
    private void refreshRuntime(){loaded=List.of();catalogueLoaded=false;configs=List.of();configsLoaded=false;}
    private void ensureConfigs(){if(!configsLoaded){try{configs=CreateConfigs.list();}catch(Exception e){status=e.getMessage();}configsLoaded=true;}}
    private void ensureCatalogue(){if(!catalogueLoaded){loaded=EditorRuntime.recipes();catalogueLoaded=true;}}
    public void refreshAfterDeletion(){refreshRuntime();review=null;recipe=null;item=null;dirty=false;selectedSlot=null;appliedRecipeSelection=null;tab=4;}
    public void batchResult(EditorRuntime.ReloadResult result){status=result.message();if(Minecraft.getInstance().screen==this){refreshAfterDeletion();rebuildWidgets();}}
    private void run(Checked action){try{if(EditorRuntime.busy())throw new IllegalStateException("正在应用，请稍候");action.run();}catch(Exception e){status="错误："+e.getMessage();}}
    @FunctionalInterface private interface Checked{void run()throws Exception;}
    private void changed(){EditorRuntime.requireEditable();if(recipe!=null)recipe.enabled=true;dirty=true;review=null;issues=List.of();autosaveTicks=0;if(status.startsWith("错误："))status="更改已更新，应用前会重新检查";}
    private Button button(String text,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(UiText.component(text),b->run(action::run)).bounds(x,y,w,20).build());}
    private void showText(String title,String text){minecraft.setScreen(new TextScreen(this,title,text));}
    private void switchTab(int value){stage();tab=value;if(value==5){recipe=null;item=null;dirty=false;status="强制兼容 · 选择配方或拖入机器，自动读取可编辑字段";}listOffset=propertyOffset=canvasScroll=canvasScrollX=0;selectedSlot=null;rebuildWidgets();}
    @Override protected void init(){
        activationSeen=EditorRuntime.state().activation;
        imageWidth=Math.max(300,Math.min(width-(height<360||width<724?110:124),1160));imageHeight=height-8;super.init();leftPos=4;topPos=4;
        compact=(imageWidth<600||height<360)&&tab!=3&&tab!=4;
        listX=leftPos+6;listW=compact?98:imageWidth>760?180:130;propW=compact?112:imageWidth>760?210:156;
        if(panels.left>0)listW=Math.max(90,Math.min((int)(imageWidth*panels.left),imageWidth-180));
        if(panels.right>0)propW=Math.max(110,Math.min((int)(imageWidth*panels.right),imageWidth-listW-60));
        propX=leftPos+imageWidth-propW-6;canvasX=listX+listW+8;canvasW=Math.max(40,propX-canvasX-8);bodyY=compact?106:112;bottom=height-34;
        ghosts.clear();form.clear();invalidFields.clear();String slotLabel=selectedSlot==null?null:selectedSlot.label();slots=List.of();
        if(recipe!=null&&(recipe.operation.equals("remove")||EditorCompatibility.hasRecipeType(recipe.data.get("type").getAsString())))try{slots=RecipeCanvas.slots(recipe);}catch(RuntimeException error){if(!readOnly)throw error;}
        selectedSlot=slots.stream().filter(s->s.label().equals(slotLabel)).findFirst().orElse(null);
        String[] tabs={"新增功能","修改现有","机械动力","已有修改","强制兼容"};int[] pages={0,1,2,4,5};int tw=Math.min(100,(imageWidth-12)/tabs.length);
        for(int i=0;i<tabs.length;i++){int n=pages[i];var nav=button((tab==n?"● ":"")+tabs[i],leftPos+6+i*tw,topPos+18,tw-3,()->switchTab(n));if(n==2&&!EditorCompatibility.hasCreate()){nav.active=false;nav.setTooltip(Tooltip.create(UiText.component("未安装机械动力 Create；通用配方和物品编辑仍可使用")));}}
        addRenderableWidget(Button.builder(Component.literal("×"),b->run(this::onClose)).bounds(leftPos+imageWidth-24,topPos,20,16).build());
        if(tab==3){buildReview();return;}
        if(tab==4){buildExisting();return;}
        // Full validation is performed on explicit preview/apply, not every click, resize or keystroke.
        if(tab==5){button("选择配方",listX,48,86,this::browseSimpleRecipes);button("使用说明",listX+90,48,86,()->showText("强制兼容",simpleHelp()));button("拖入机器",listX+180,48,86,()->showText("拖入机器","从 JEI 把机器拖到此按钮，按真实加工分类筛选配方"));ghosts.add(new GhostTarget(new Rect2i(listX+180,48,86,20),false,value->{if(value instanceof ItemStack machine)CompatMachines.choose(this,machine);}));}
        else if(tab==2){button("加工配方",listX,48,75,()->{stage();createTab=0;listOffset=propertyOffset=0;rebuildWidgets();});button("模组参数",listX+78,48,75,()->{stage();createTab=1;listOffset=propertyOffset=0;rebuildWidgets();});}
        else if(tab==1){button("现有配方",listX,48,82,()->browseRecipes(false));button("现有物品",listX+85,48,82,this::browseItems);}
        else {button("+配方",listX,48,80,this::newRecipe);button("+注册物品",listX+83,48,90,()->newItem(true));}
        if(tab==2&&createTab==0){button("+配方",listX+160,48,64,this::newRecipe);button("现有配方",listX+228,48,80,()->browseRecipes(true));}
        if(tab==2&&createTab==1){ensureConfigs();button("选择参数",canvasX,72,100,this::browseConfigs).active=!configs.isEmpty();}
        searchBox("搜索操作 / 名称 / ID");
        buildRows();formTop=bodyY;
        if(tab==2&&createTab==1)buildConfig();else if(item!=null)buildItem();else buildRecipe();
        buildForm();
        footer();
    }
    private boolean processingCanvas(){return recipe!=null&&recipe.grid==null&&!recipe.data.has("sequence")&&FanProcessing.createType(recipe.data.get("type").getAsString());}
    private int canvasBottom(){return bottom-30;}
    private int canvasTop(){return bodyY+(recipe!=null&&recipe.data.has("sequence")?34:16);}
    private int canvasLeft(){return recipe!=null&&recipe.data.has("sequence")?canvasX+(canvasW-SequenceLayout.viewportWidth(canvasW-12))/2:canvasX;}
    private int canvasRight(){return recipe!=null&&recipe.data.has("sequence")?canvasLeft()+SequenceLayout.viewportWidth(canvasW-12):canvasX+canvasW;}
    private void compactOperations(){stage();var choices=new ArrayList<ChoiceScreen.Choice>();for(var row:rows){if(row.operation()==null)continue;var op=row.operation();choices.add(new ChoiceScreen.Choice(op.title()+" · "+row.label(),()->selectOperation(op),row.detail()));choices.add(new ChoiceScreen.Choice("取消本条修改 · "+row.label(),()->cancelOperation(op),op.id()));}minecraft.setScreen(new ChoiceScreen(this,"操作记录",choices));}
    private void searchBox(String hint){
        EditBox search=new EditBox(font,listX,bodyY-20,listW,18,UiText.component("筛选"));search.setValue(filter);search.setHint(Component.literal(font.plainSubstrByWidth(UiText.text(hint),listW-8)));
        search.setResponder(s->{filter=s;listOffset=0;rebuildWidgets();});addRenderableWidget(search);if(!filter.isEmpty()){setFocused(search);search.setCursorPosition(filter.length());}
    }
    private void footer(){
        button("一键应用",listX,height-28,82,this::applyAll).active=!EditorRuntime.locked()&&EditorRuntime.canApply();
        button("查看代码",listX+86,height-28,80,this::openCode);
        button("更多",listX+170,height-28,52,this::more);
    }
    private void applyAll(){run(()->{
        EditorRuntime.requireEditable();stage();EditorRuntime.saveDraft();
        try{review=EditorRuntime.review();}catch(IllegalArgumentException invalid){issues=EditorRuntime.validationIssues();status="错误："+invalid.getMessage();review=null;rebuildWidgets();return;}
        if(review.changes().isEmpty()){status="没有待应用的修改";rebuildWidgets();return;}
        EditorRuntime.apply(review);recipe=null;item=null;dirty=false;selectedSlot=null;
        if(review.restartRequired()){status="已保存，等待重启；请在已有修改中查看";tab=4;existingMode=0;}
        else {status="正在应用全部操作并重载…";EditorRuntime.reloadResult(review.resourcesChanged(),r->{status=r.message();review=null;refreshRuntime();rebuildWidgets();});}
        review=null;rebuildWidgets();
    });}
    private void more(){stage();minecraft.setScreen(new ChoiceScreen(this,"更多",List.of(
        new ChoiceScreen.Choice("脚本管理",this::browseScripts),
        new ChoiceScreen.Choice("导出",()->run(()->status="已导出："+EditorRuntime.export())),
        new ChoiceScreen.Choice("整批回滚",()->run(()->{EditorRuntime.rollback();recipe=null;item=null;dirty=false;review=null;status="已恢复文件，正在重载";if(EditorRuntime.requiresRestart())status="回滚已保存，请重启游戏";else EditorRuntime.reloadResult(false,r->{status=r.success()?"已整批回滚并重载，待处理操作保留":r.message();refreshRuntime();rebuildWidgets();});})),
        new ChoiceScreen.Choice("KJS 日志",()->showText("KubeJS 日志",EditorRuntime.logs())),
        new ChoiceScreen.Choice("最近耗时",()->showText("最近重载耗时",AppliedDeletionScreen.performance())),
        new ChoiceScreen.Choice("最近诊断",()->showText("最近诊断",dev.kubejseditor.runtime.EditorDiagnostics.read(EditorRuntime.root()))),
        new ChoiceScreen.Choice("复制最近诊断",()->{String text=dev.kubejseditor.runtime.EditorDiagnostics.read(EditorRuntime.root());minecraft.keyboardHandler.setClipboard(text);status="已复制最近诊断";rebuildWidgets();}),
        new ChoiceScreen.Choice("兼容信息",()->showText("兼容信息",EditorCompatibility.report())),
        new ChoiceScreen.Choice("重载配方模板",()->{stage();RecipeTemplates.load(EditorRuntime.root(),EditorCompatibility::requirement);refreshRuntime();showText("配方模板",RecipeTemplates.DIRECTORY+"\n\n"+String.join("\n",RecipeTemplates.diagnostics())+"\n\n模板只定义表单和校验，不执行代码；添加、替换或取消模板后请检查待办。未适配的原始配方数据仍保留。");}),
        new ChoiceScreen.Choice("刷新运行数据",()->{EditorRuntime.recipesChanged();refreshRuntime();review=null;})
        ,new ChoiceScreen.Choice("布局恢复默认",()->{panels.left=panels.right=-1;run(()->panels.save(EditorRuntime.root()));rebuildWidgets();})
    )));}
    private static String simpleHelp(){return "强制兼容默认开启。\n\n选择配方或从 JEI 打开来源，自动读取原生 JSON、公开字段与槽位。可直接拖放、修改数值，必要时确认字段映射或编辑原编码。\n\n映射可以清空保存，也可重置为自动识别；材料、概率、组件和未知字段保留。原生数据包注册表条目需要重启，其余配方按实际来源重载。缺少权威写入来源的展示会说明原因。\n\n每次应用保留原生校验、事务备份与加载结果核对。";}
    private void browseSimpleRecipes(){stage();ensureCatalogue();var names=labels();var choices=new ArrayList<ChoiceScreen.Choice>();
        for(var value:loaded){var adapter=RecipeAdapters.get(value.type());if(adapter!=null&&!(adapter instanceof SimpleRecipeAdapter)&&!(adapter instanceof dev.kubejseditor.recipe.compat.CompatibilityAdapter))continue;
            String state=value.capabilityLabel();choices.add(new ChoiceScreen.Choice("["+state+"] "+names.get(value.id())+" · "+value.id(),()->{tab=5;selectLoaded(value);},value.type()+"\n"+(value.error()==null?"已读取原生字段；修改后经过原生校验":value.error())));
        }minecraft.setScreen(new ChoiceScreen(this,"强制兼容 · 选择配方",choices));
    }
    private void browseRecipes(boolean create){stage();ensureCatalogue();List<ChoiceScreen.Choice> choices=new ArrayList<>();var names=labels();
        for(var r:loaded)if(!create||FanProcessing.createType(r.type()))choices.add(new ChoiceScreen.Choice(names.get(r.id())+" · "+RecipeNames.station(r.type())+" · "+r.id(),()->{tab=create?2:1;createTab=0;selectLoaded(r);},r.type(),VintageProcessing.dynamic(r.type())?"加工机制":"禁用配方",()->run(()->{stage();if(VintageProcessing.dynamic(r.type()))vintageDynamic(r.type());else queueRecipeRemoval(r.id(),r.data());}),()->!EditorRuntime.locked()&&!(project.recipes.containsKey(r.id())&&project.recipes.get(r.id()).operation.equals("remove"))));
        minecraft.setScreen(new ChoiceScreen(this,"选择现有配方",choices));
    }
    private void browseItems(){stage();List<ChoiceScreen.Choice> choices=new ArrayList<>();
        for(var value:BuiltInRegistries.ITEM){String id=BuiltInRegistries.ITEM.getKey(value).toString();if(id.equals("minecraft:air"))continue;
            choices.add(new ChoiceScreen.Choice(value.getDescription().getString()+" · "+id,()->{tab=1;var pending=project.items.get(id);if(pending!=null){selectItem(pending);return;}var old=EditorRuntime.state().applied.items.get(id);if(old!=null){selectItem(old);return;}ItemDraft d=new ItemDraft();d.id=id;d.register=false;d.name="";d.texture="";selectItem(d);}));}
        minecraft.setScreen(new ChoiceScreen(this,"选择现有物品",choices));
    }
    private void browseConfigs(){stage();ensureConfigs();List<ChoiceScreen.Choice> choices=new ArrayList<>();for(var c:configs)choices.add(new ChoiceScreen.Choice(ConfigLabels.choice(c),()->{selectedConfig=c;configText=c.text(c.current());recipe=null;item=null;propertyOffset=0;},ConfigLabels.category(c)+"\n"+c.key()));minecraft.setScreen(new ChoiceScreen(this,"选择机械动力参数",choices));}
    private void browseScripts(){run(()->{List<ChoiceScreen.Choice> choices=new ArrayList<>();for(var s:EditorScripts.list())choices.add(new ChoiceScreen.Choice(s.path()+" · "+s.status(),()->run(()->minecraft.setScreen(new ScriptEditorScreen(this,s.path())))));minecraft.setScreen(new ChoiceScreen(this,"脚本管理",choices));});}
    private Map<String,String> labels(){
        if(labelledRecipes!=loaded){var base=new LinkedHashMap<String,String>();for(var r:loaded)base.put(r.id(),RecipeNames.main(r.data()));loadedNames=base;labelledRecipes=loaded;}
        Map<String,String> names=new LinkedHashMap<>(loadedNames);
        EditorRuntime.state().applied.recipes.forEach((id,r)->names.put(id,RecipeNames.main(r.data)));
        project.recipes.forEach((id,r)->names.put(id,RecipeNames.main(r.data)));return RecipeNames.numbered(names);
    }
    private void buildRows(){
        List<Row> next=new ArrayList<>();String q=filter.toLowerCase(Locale.ROOT);var names=labels();
        for(var op:EditorRuntime.operations()){
            String name=switch(op.kind()){
                case "recipe" -> names.getOrDefault(op.id(),op.id());
                case "item" -> {var i=project.items.get(op.id());var id=ResourceLocation.tryParse(op.id());yield !i.name.isBlank()?i.name:id!=null&&BuiltInRegistries.ITEM.containsKey(id)?BuiltInRegistries.ITEM.get(id).getDescription().getString():op.id();}
                case "config" -> configs.stream().filter(c->op.id().endsWith(String.join(".",c.path()))&&op.id().startsWith(c.config().getModId()+"/")).findFirst().map(ConfigLabels::name).orElseGet(()->dev.kubejseditor.config.ConfigNames.words(op.id().substring(op.id().lastIndexOf('/')+1)));
                default -> op.id().substring(op.id().lastIndexOf('/')+1);
            };
            String errors=issues.stream().filter(i->i.key().equals(op.key())).map(ValidationIssue::message).collect(java.util.stream.Collectors.joining("\n"));
            String detail=op.title()+" · "+name+"\n"+op.id()+(errors.isEmpty()?"":"\n"+errors);
            if(matches(detail,q))next.add(new Row(name,detail,()->{selectOperation(op);issues.stream().filter(i->i.key().equals(op.key())).findFirst().ifPresent(this::focusIssue);},op));
        }
        rows=next;listOffset=Math.min(listOffset,Math.max(0,rows.size()-visibleRows()));
        for(int i=listOffset;i<Math.min(rows.size(),listOffset+visibleRows());i++){
            var op=rows.get(i).operation();var cancel=button("×",listX+listW-20,bodyY+(i-listOffset)*30+3,18,()->cancelOperation(op));cancel.active=!EditorRuntime.locked();cancel.setTooltip(Tooltip.create(UiText.component("取消本条修改")));
        }
    }
    private void selectOperation(PendingOperation op){
        switch(op.kind()){
            case "recipe" -> {var r=project.recipes.get(op.id());tab=r.simple?5:r.data!=null&&FanProcessing.createType(r.data.get("type").getAsString())?2:r.operation.equals("add")?0:1;createTab=0;selectRecipe(r,false);}
            case "item" -> {var i=project.items.get(op.id());tab=i.register?0:1;selectItem(i);}
            case "script" -> run(()->minecraft.setScreen(new ScriptEditorScreen(this,op.id())));
            case "config" -> {stage();ensureConfigs();var d=project.configs.stream().filter(c->PendingOperation.configKey(c).equals(op.id())).findFirst().orElseThrow();tab=2;createTab=1;selectedConfig=configs.stream().filter(c->c.config().getModId().equals(d.modId)&&c.config().getFileName().equals(d.file)&&c.path().equals(d.path)&&(d.target==null||c.file().equals(EditorRuntime.root().resolve(d.target).normalize()))).findFirst().orElse(null);configText=d.value;recipe=null;item=null;rebuildWidgets();}
        }
    }
    private void cancelOperation(PendingOperation op){run(()->{
        EditorRuntime.requireEditable();
        boolean selected=op.kind().equals("recipe")&&recipe!=null&&recipe.id.equals(op.id())||op.kind().equals("item")&&item!=null&&item.id.equals(op.id());
        if(selected){recipe=null;item=null;dirty=false;selectedSlot=null;invalidFields.clear();}else stage();
        EditorRuntime.cancel(op);review=null;
        if(selected&&op.kind().equals("recipe"))loaded.stream().filter(r->r.id().equals(op.id())).findFirst().ifPresent(this::selectLoaded);
        if(selected&&op.kind().equals("item")&&BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(op.id()))){var original=EditorRuntime.state().applied.items.get(op.id());if(original!=null)selectItem(original);else{var originalItem=new ItemDraft();originalItem.id=op.id();originalItem.register=false;originalItem.name="";originalItem.texture="";selectItem(originalItem);}}
        if(op.kind().equals("config")){selectedConfig=null;configText="";}
        status="已取消本条修改";rebuildWidgets();
    });}
    private void buildExisting(){
        button("配方修改",listX,48,88,()->{existingMode=0;listOffset=0;rebuildWidgets();});button("物品注册",listX+92,48,88,()->{existingMode=1;listOffset=0;rebuildWidgets();});button("批量删除",listX+184,48,88,()->{stage();minecraft.setScreen(new AppliedDeletionScreen(this));});
        searchBox("名称 / ID");
        List<Row> list=new ArrayList<>();String q=filter.toLowerCase(Locale.ROOT);var names=labels();
        var applied=EditorRuntime.state().applied;var activation=EditorRuntime.state().activation;
        Map<String,RecipeDraft> recipes=new LinkedHashMap<>(applied.recipes);Map<String,ItemDraft> items=new LinkedHashMap<>(applied.items);
        if(activation!=null){recipes.putAll(activation.submitted.recipes);items.putAll(activation.submitted.items);}
        if(existingMode==0){
            for(var r:recipes.values()){
                boolean revoking=activation!=null&&activation.revokedRecipes!=null&&activation.revokedRecipes.contains(r.id);
                String state=revoking?(!EditorRuntime.activationError.isBlank()?"撤销失败，须回滚":"正在撤销"):activation!=null&&activation.submitted.recipes.containsKey(r.id)?(!EditorRuntime.activationError.isBlank()?"加载失败，须回滚":activation.restart?"等待重启":"等待加载验证"):"已生效";
                String label=r.actionLabel()+" · "+names.getOrDefault(r.id,RecipeNames.main(r.data));
                if(matches(label+" "+r.id,q))list.add(new Row(label,state+" · "+r.actionLabel()+"\n"+r.id+"\n× 加入批量删除：可选择多条，统一提交",()->selectAppliedRecipe(r),new PendingOperation("applied_recipe",r.id,r.actionLabel())));
            }
            for(var e:ScriptIndex.recipeSources().entrySet())if(!recipes.containsKey(e.getKey())&&matches(e.getKey()+" "+names.getOrDefault(e.getKey(),"")+" "+e.getValue(),q)){
                String name=names.getOrDefault(e.getKey(),e.getKey());
                list.add(new Row("来源脚本 · "+name,e.getKey()+"\n"+e.getValue(),()->{var r=EditorRuntime.recipe(e.getKey());if(r!=null){tab=1;selectLoaded(r);}else showText("KJS 禁用记录",e.getKey()+"\n"+e.getValue()+"\n请从更多 → 脚本管理编辑源规则");}));
            }
        }else for(var i:items.values()){
            boolean deleting=activation!=null&&activation.deletedItems!=null&&activation.deletedItems.contains(i.id);
            String state=deleting?(!EditorRuntime.activationError.isBlank()?"删除失败，须回滚":"删除已保存 · 等待重启"):activation!=null&&activation.submitted.items.containsKey(i.id)?(!EditorRuntime.activationError.isBlank()?"加载失败，须回滚":activation.restart?"等待重启":"等待加载验证"):"已生效";
            String name=i.name.isBlank()?i.id:i.name;
            if(matches(name+" "+i.id,q))list.add(new Row(name,(i.register?"物品注册":"物品修改")+" · "+state+"\n"+i.id,()->{tab=1;selectItem(i);},i.register?new PendingOperation("applied_item",i.id,"物品注册"):null));
        }
        if(activation!=null&&existingMode==0)for(var c:activation.submitted.configs)list.add(new Row("参数 · "+dev.kubejseditor.config.ConfigNames.words(c.path.getLast()),c.modId+" · 等待重启",()->showText("待重启参数",PendingOperation.configKey(c)+"\n目标值："+c.value)));
        rows=list;listOffset=Math.min(listOffset,Math.max(0,list.size()-visibleRows()));
        for(int i=listOffset;i<Math.min(rows.size(),listOffset+visibleRows());i++){
            var row=rows.get(i);if(row.operation()!=null&&row.operation().kind().equals("applied_recipe")){
                String id=row.operation().id();var undo=button("×",listX+listW-20,bodyY+(i-listOffset)*30+3,18,()->revokeAppliedRecipe(id));
                undo.active=!EditorRuntime.locked()&&EditorRuntime.canApply()&&EditorRuntime.state().canRevokeRecipe(id);
                undo.setTooltip(Tooltip.create(Component.literal(EditorRuntime.state().canRevokeRecipe(id)?"确认后撤销此条已应用修改并立即重载；其他待办保留":"手写来源请从更多 → 脚本管理编辑")));
            }else if(row.operation()!=null&&row.operation().kind().equals("applied_item")){
                String id=row.operation().id();var remove=button("×",listX+listW-20,bodyY+(i-listOffset)*30+3,18,()->confirmDeleteItem(id));
                remove.active=!EditorRuntime.locked()&&EditorRuntime.canApply()&&EditorRuntime.state().canDeleteRegisteredItem(id);
                remove.setTooltip(Tooltip.create(UiText.component("删除注册物品（确认后保存，重启生效）")));
            }
        }footer();
    }
    private void selectAppliedRecipe(RecipeDraft r){tab=1;createTab=0;selectRecipe(r,EditorRuntime.state().frozen.getOrDefault(WorkspaceState.GENERATED.getFirst(),Set.of()).contains(r.id));appliedRecipeSelection=r.id;rebuildWidgets();}
    private void revokeAppliedRecipe(String id){run(()->{EditorRuntime.requireEditable();stage();minecraft.setScreen(new AppliedDeletionScreen(this,id,false));});}
    private void deleteRecipe(){
        if(recipe!=null)queueRecipeRemoval(recipe.id,recipe.data);
    }
    private void queueRecipeRemoval(String id,JsonObject data){
        EditorRuntime.requireEditable();
        boolean exists=EditorRuntime.state().applied.recipes.containsKey(id)||loaded.stream().anyMatch(r->r.id().equals(id));
        if(exists){RecipeDraft removal=new RecipeDraft(id,data==null?new JsonObject():data);removal.operation="remove";var prior=EditorRuntime.state().applied.recipes.get(id);if(prior!=null&&prior.source!=null)removal.source=prior.source.deepCopy();else if(prior==null&&data!=null)removal.source=data.deepCopy();project.recipes.put(id,removal);}else project.recipes.remove(id);
        if(recipe!=null&&recipe.id.equals(id)){recipe=null;selectedSlot=null;dirty=false;readOnly=false;appliedRecipeSelection=null;}
        review=null;status=exists?"已加入禁用待办，应用后不能使用此配方":"已移除未应用草稿";try{EditorRuntime.saveDraft();}catch(Exception e){throw new IllegalStateException(e);}rebuildWidgets();
    }
    private boolean matches(String s,String q){return s.toLowerCase(Locale.ROOT).contains(q);}
    private int visibleRows(){return Math.max(1,(bottom-bodyY-6)/30);}
    private void selectRecipe(RecipeDraft r,boolean ro){stage();if(r.compatibility!=null){minecraft.setScreen(new CompatibilityScreen(this,r));return;}try{SimpleRecipes.restore(r);}catch(Exception error){ro=true;status=error.getMessage();}appliedRecipeSelection=null;recipe=ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(r),RecipeDraft.class);if(recipe.data!=null&&RecipeAdapters.get(recipe.data.get("type").getAsString()) instanceof SimpleRecipeAdapter)recipe.simple=true;if(!project.recipes.containsKey(r.id)&&!recipe.operation.equals("remove")&&loaded.stream().anyMatch(v->v.id().equals(r.id)))recipe.operation="replace";if(recipe.origin==null){var prior=EditorRuntime.state().applied.recipes.get(recipe.id);recipe.origin=prior!=null?(prior.origin!=null?prior.origin:prior.operation.equals("add")?"add":"existing"):r.operation.equals("add")?"add":"existing";}recipeIdText=recipe.id;item=null;readOnly=ro||EditorRuntime.locked();dirty=false;selectedSlot=null;propertyOffset=canvasScroll=canvasScrollX=0;rebuildWidgets();}
    private void selectLoaded(EditorRuntime.Loaded r){
        if(r.registryTarget()!=null||dev.kubejseditor.runtime.CompatibilityRecipes.available(r.data())||tab==5&&SimpleRecipes.enabled()){stage();CompatibilityScreenOpen.open(this,r.id());return;}
        if(project.recipes.containsKey(r.id())){selectRecipe(project.recipes.get(r.id()),false);return;}RecipeDraft d=new RecipeDraft(r.id(),r.data()==null?RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[],result:{id:'minecraft:air'}}"):r.data());d.operation="replace";var prior=EditorRuntime.state().applied.recipes.get(r.id());if(prior!=null&&prior.grid!=null&&!EditorRuntime.state().frozen.getOrDefault(WorkspaceState.GENERATED.getFirst(),Set.of()).contains(r.id()))d.grid=prior.grid.copy();
        // Keep the first external snapshot for revocation verification; legacy applied rules
        // without a snapshot stay presence-only rather than storing their edited data as source.
        if(prior!=null){if(prior.source!=null)d.source=prior.source.deepCopy();}else if(d.data!=null)d.source=d.data.deepCopy();
        selectRecipe(d,r.error()!=null);status=r.error()==null?(recipe.simple?"已导入原生字段，可查看字段信息":"已导入当前配方，修改后暂存为覆盖"):r.error();
    }
    private void selectItem(ItemDraft i){stage();appliedRecipeSelection=null;item=ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(i),ItemDraft.class);itemIdText=item.id;recipe=null;readOnly=EditorRuntime.locked()||EditorRuntime.state().frozen.entrySet().stream().anyMatch(e->!e.getKey().equals(WorkspaceState.GENERATED.getFirst())&&e.getValue().contains(i.id));dirty=false;propertyOffset=canvasScroll=canvasScrollX=0;rebuildWidgets();}
    private void newRecipe(){
        EditorRuntime.requireEditable();stage();recipePicker(tab==2,this);
    }
    private boolean createCategory(String type){return FanProcessing.createType(type)||EditorModules.ownsCreateConfig(ResourceLocation.parse(type).getNamespace());}
    private void recipePicker(boolean create,Screen parent){
        ensureCatalogue();
        List<ChoiceScreen.Choice> choices=new ArrayList<>();
        Set<String> stations=new HashSet<>();
        for(RecipeAdapter a:RecipeAdapters.creation())if(!FanProcessing.supports(a.type())&&createCategory(a.type())==create&&BuiltInRegistries.RECIPE_SERIALIZER.containsKey(ResourceLocation.parse(a.type()))&&stations.add(VintageProcessing.workstationKey(a.type()))){
            var station=VintageProcessing.workstation(a.type());String search=station==null?a.type():station.types().stream().map(type->VintageProcessing.get(type).title()+" "+type).collect(java.util.stream.Collectors.joining("\n"));
            choices.add(new ChoiceScreen.Choice(VintageProcessing.workstationTitle(a.type(),a.title()),()->{createRecipe(a);if(!(a instanceof dev.kubejseditor.recipe.compat.CompatibilityAdapter))minecraft.setScreen(this);},search));
        }
        if(create){
            if(!availableFanModes().isEmpty())choices.add(new ChoiceScreen.Choice("鼓风机加工",()->chooseFanMode(true)));
            for(String type:VintageProcessing.dynamicTypes())if(EditorCompatibility.hasRecipeType(type))choices.add(new ChoiceScreen.Choice(VintageProcessing.dynamicTitle(type),()->vintageDynamic(type),VintageProcessing.explanation(type)));
        }else if(EditorCompatibility.hasCreate())choices.add(new ChoiceScreen.Choice("机械动力",()->recipePicker(true,minecraft.screen),"Create · 机械动力及附属加工配方"));
        minecraft.setScreen(new ChoiceScreen(parent,create?"机械动力 · 选择配方工作站":"选择配方工作站",choices));
    }
    private List<FanProcessing.Mode> availableFanModes(){return FanProcessing.all().stream().filter(m->RecipeAdapters.get(m.type())!=null&&BuiltInRegistries.RECIPE_SERIALIZER.containsKey(ResourceLocation.parse(m.type()))).toList();}
    private void vintageDynamic(String type){
        stage();if(type.endsWith("leaves_vibrating"))minecraft.setScreen(new ChoiceScreen(this,VintageProcessing.dynamicTitle(type),List.of(
            new ChoiceScreen.Choice("查看树叶掉落机制",()->showText(VintageProcessing.dynamicTitle(type),VintageProcessing.explanation(type))),
            new ChoiceScreen.Choice("修改树叶振动开关",()->{ensureConfigs();tab=2;createTab=1;recipe=null;item=null;selectedConfig=configs.stream().filter(c->c.config().getModId().equals(VintageProcessing.MOD)&&c.path().getLast().equals("allowVibratingLeaves")).findFirst().orElse(null);if(selectedConfig!=null)configText=selectedConfig.text(selectedConfig.current());status=VintageProcessing.explanation(type);})
        )));
        else {
            var choices=new ArrayList<ChoiceScreen.Choice>();for(var value:loaded)if(value.type().startsWith("minecraft:smithing_"))choices.add(new ChoiceScreen.Choice(RecipeNames.main(value.data())+" · "+value.id(),()->{tab=1;selectLoaded(value);},value.type()));
            choices.addFirst(new ChoiceScreen.Choice("查看自动锻造机制",()->showText(VintageProcessing.dynamicTitle(type),VintageProcessing.explanation(type))));
            minecraft.setScreen(new ChoiceScreen(this,"选择真实锻造配方",choices));
        }
    }
    private void chooseFanMode(boolean create){
        var choices=new ArrayList<ChoiceScreen.Choice>();for(var mode:availableFanModes())choices.add(new ChoiceScreen.Choice(mode.name(),()->run(()->{
            if(create)createRecipe(RecipeAdapters.get(mode.type()));else{FanProcessing.switchMode(recipe.data,mode.type());selectedSlot=null;changed();stage();}
            minecraft.setScreen(this);
        }),mode.condition()+"\n"+mode.type()));
        minecraft.setScreen(new ChoiceScreen(this,"鼓风机加工方式",choices));
    }
    private void createRecipe(RecipeAdapter a){
        if(a instanceof dev.kubejseditor.recipe.compat.CompatibilityAdapter){var d=new RecipeDraft(uniqueRecipeId(),a.newRecipe());d.origin="add";dev.kubejseditor.runtime.CompatibilityRecipes.annotate(d);minecraft.setScreen(new CompatibilityScreen(this,d));return;}
            appliedRecipeSelection=null;recipe=new RecipeDraft(uniqueRecipeId(),a.newRecipe());recipe.origin="add";recipe.simple=a instanceof SimpleRecipeAdapter;if((a instanceof RecipeTemplate||FanProcessing.createType(a.type()))&&!recipe.data.has("sequence")&&recipe.data.has("ingredients")){
                JsonArray in=recipe.data.getAsJsonArray("ingredients");for(int i=0;i<in.size();i++)in.set(i,RecipeLimits.fluid(in.get(i))?RecipeAdapters.parse("{amount:250}"):JsonNull.INSTANCE);
            }if(!(a instanceof SimpleRecipeAdapter)&&recipe.data.has("result"))recipe.data.add("result",JsonNull.INSTANCE);else if(!(a instanceof SimpleRecipeAdapter)&&recipe.data.has("results")){JsonArray outputs=new JsonArray();outputs.add(JsonNull.INSTANCE);if(a.type().equals("create:emptying"))outputs.add(RecipeAdapters.parse("{amount:250}"));recipe.data.add("results",outputs);}recipeIdText=recipe.id;item=null;readOnly=false;dirty=true;selectedSlot=null;propertyOffset=canvasScroll=canvasScrollX=0;stage();
    }
    private String uniqueRecipeId(){int n=1;while(contentHasId("kubejs_editor:recipe_"+n))n++;return "kubejs_editor:recipe_"+n;}
    private boolean contentHasId(String id){return project.recipes.containsKey(id)||EditorRuntime.state().applied.recipes.containsKey(id)||loaded.stream().anyMatch(r->r.id().equals(id));}
    private void newItem(boolean register){
        EditorRuntime.requireEditable();stage();if(!register){createItem("basic",false);return;}
        var choices=new ArrayList<ChoiceScreen.Choice>();for(String category:List.of("普通材料","食物","工具","武器","防具","方块"))choices.add(new ChoiceScreen.Choice(category,()->{
            if(category.equals("方块")){var sub=new ArrayList<ChoiceScreen.Choice>();for(var type:BlockKinds.NAMES.entrySet())sub.add(new ChoiceScreen.Choice(type.getValue(),()->{createItem("block",true);BlockKinds.initialize(item,type.getKey());changed();stage();minecraft.setScreen(this);}));minecraft.setScreen(new ChoiceScreen(this,"选择方块类型",sub));}
            else if(category.equals("工具")||category.equals("防具")){var types=category.equals("工具")?List.of("pickaxe","axe","shovel","hoe"):List.of("helmet","chestplate","leggings","boots");var sub=new ArrayList<ChoiceScreen.Choice>();for(String k:types)sub.add(new ChoiceScreen.Choice(ItemKinds.NAMES.get(k),()->{createItem(k,true);minecraft.setScreen(this);}));minecraft.setScreen(new ChoiceScreen(this,"选择"+category+"类型",sub));}
            else{String k=switch(category){case "食物"->"food";case "武器"->"sword";case "方块"->"block";default->"basic";};createItem(k,true);minecraft.setScreen(this);}
        }));minecraft.setScreen(new ChoiceScreen(this,"先选择注册分类",choices));
    }
    private void createItem(String kind,boolean register){
        appliedRecipeSelection=null;readOnly=false;item=new ItemDraft();item.register=register;ItemKinds.initialize(item,kind);int n=1;while(project.items.containsKey("kubejs:new_item_"+n)||EditorRuntime.state().applied.items.containsKey("kubejs:new_item_"+n)||BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("kubejs:new_item_"+n)))n++;item.id="kubejs:new_item_"+n;if(!register){item.id="minecraft:iron_ingot";item.name="";item.texture="";item.properties=new JsonObject();}itemIdText=item.id;recipe=null;dirty=true;propertyOffset=canvasScroll=canvasScrollX=0;stage();rebuildWidgets();
    }
    private void stage(){
        if(!invalidFields.isEmpty())throw new IllegalArgumentException("请先修复无效字段："+String.join("、",invalidFields.keySet()));
        if(!dirty)return;
        EditorRuntime.requireEditable();
        if(recipe!=null&&!readOnly){String id=recipeIdText;EditorProject.requireId(id);String old=recipe.id;if(!old.equals(id)&&project.recipes.containsKey(id))throw new IllegalArgumentException("配方 ID 已存在："+id);recipe.id=id;project.recipes.remove(old);project.recipes.put(id,ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(recipe),RecipeDraft.class));}
        if(item!=null){String id=itemIdText;EditorProject.requireId(id);String old=item.id;if(!old.equals(id)&&project.items.containsKey(id))throw new IllegalArgumentException("物品草稿 ID 已存在："+id);item.id=id;project.items.remove(old);project.items.put(id,ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(item),ItemDraft.class));}
        dirty=false;review=null;try{EditorRuntime.saveDraft();}catch(Exception e){throw new IllegalStateException("草稿自动保存失败："+e.getMessage(),e);}
    }
    private void field(String label,String value,Consumer<String> write){form.add(new Form(label,value,write,List.of()));}
    private void choice(String label,String value,List<String> choices,Consumer<String> write){form.add(new Form(label,value,write,choices));}
    private void actionField(String label,String text,Runnable action){form.add(new Form(label,text,null,List.of(),action));}
    private String caption(String key){return switch(key){
        case "processing_time" -> "加工时间（tick）";case "cookingtime" -> "烹饪时间（tick）";case "experience" -> "经验值";
        case "heat_requirement" -> "工作盆燃烧条件";case "loops" -> "循环次数";case "accept_mirrored" -> "允许镜像图案";
        case "headDamage" -> "每次弯曲的压头损耗";case "requiredBonks" -> "需要压制次数";case "hammer_blows","hammerBlows" -> "需要锤击次数";case "anvil_block","anvilBlock" -> "砧座方块 ID（air 为默认）";case "speedLimits" -> "研磨转速档位";case "fragile" -> "转速不符时材料易损";
        case "keep_held_item" -> "保留机械手手持物";case "maxStackSize" -> "最大堆叠数量";case "maxDamage" -> "最大耐久";
        case "damage"->"基础攻击伤害（含玩家基础值）";case "attackSpeed"->"每秒攻击次数";case "miningSpeed"->"挖掘速度";case "armor"->"护甲值";case "toughness"->"护甲韧性";case "knockbackResistance"->"击退抗性（0～1）";case "hardness"->"方块硬度";case "resistance"->"爆炸抗性";case "lightLevel"->"亮度（0～15）";case "burnTime" -> "燃烧时间（tick）";case "nutrition" -> "饥饿值";case "saturation" -> "饱和度系数";default -> key;};}
    private String optionName(String key){return switch(key){case "add"->"新增";case "replace"->"替换";case "remove"->"删除";case "true"->"是";case "false"->"否";case "none"->"无燃烧（默认）";case "heated"->"普通燃烧";case "superheated"->"超级燃烧";default->key;};}
    private void simpleParameter(JsonObject owner,String key,String label){
        JsonPrimitive old=owner.get(key).getAsJsonPrimitive();
        if(old.isBoolean())choice(label,old.getAsString(),List.of("true","false"),value->{owner.addProperty(key,Boolean.parseBoolean(value));changed();});
        else field(label,old.getAsString(),value->{JsonElement parsed=JsonParser.parseString(value);if(!parsed.isJsonPrimitive()||!parsed.getAsJsonPrimitive().isNumber()||!Double.isFinite(parsed.getAsDouble()))throw new IllegalArgumentException("请输入有限数值");if(old.getAsString().matches("-?[0-9]+"))parsed.getAsBigDecimal().toBigIntegerExact();owner.add(key,parsed);changed();});
    }
    private void rootField(JsonObject o,String key,String label){
        if(!o.has(key)||!o.get(key).isJsonPrimitive())return;JsonPrimitive old=o.getAsJsonPrimitive(key);Consumer<String> writer=text->{
            if(old.isBoolean())o.addProperty(key,Boolean.parseBoolean(text));else if(old.isNumber())o.addProperty(key,Double.valueOf(text));else o.addProperty(key,text);changed();};
        if(old.isBoolean())choice(label,old.getAsString(),List.of("false","true"),writer);
        else if(key.equals("speedLimits")){List<String> values=List.of("0 · 不限转速","1 · 低速 1～32 RPM","2 · 中速 >32～128 RPM","3 · 高速 >128 RPM");int n=old.getAsInt();choice(label,n>=0&&n<4?values.get(n):old.getAsString(),values,v->writer.accept(v.substring(0,1)));}
        else if(key.equals("heat_requirement"))choice(label,old.getAsString(),o.has("type")&&o.get("type").getAsString().equals(VintageProcessing.PREFIX+"centrifugation")?List.of("heated","superheated"):List.of("none","heated","superheated"),writer);
        else field(label,old.getAsString(),writer);
    }
    private void buildRecipe(){
        if(recipe==null)return;
        if(recipe.operation.equals("remove")){
            if(appliedRecipeSelection!=null||!project.recipes.containsKey(recipe.id))appliedUndoButton(canvasX,bodyY+24);
            else button("取消本条修改",canvasX,bodyY+24,110,()->cancelOperation(new PendingOperation("recipe",recipe.id,"配方禁用"))).active=!EditorRuntime.locked();
            return;
        }
        if(!EditorCompatibility.hasRecipeType(recipe.data.get("type").getAsString())){button("兼容信息",canvasX,bodyY+24,90,()->showText("兼容信息",EditorCompatibility.unavailable(recipe.data.get("type").getAsString())+"\n\n"+EditorCompatibility.report()));return;}
        if(VintageProcessing.dynamic(recipe.data.get("type").getAsString())){button("管理实际加工机制",canvasX,bodyY+24,130,()->vintageDynamic(recipe.data.get("type").getAsString()));return;}
        if(readOnly){if(!EditorRuntime.locked()&&recipe.data!=null&&RecipeAdapters.get(recipe.data.get("type").getAsString())==null)button("打开强制兼容",canvasX,bodyY+72,110,()->run(()->{String id=recipe.id;SimpleRecipes.setEnabled(EditorRuntime.root(),true);EditorRuntime.invalidateRecipes();refreshRuntime();selectLoaded(Objects.requireNonNull(EditorRuntime.recipe(id)));}));if(EditorRuntime.state().frozen.getOrDefault(WorkspaceState.GENERATED.getFirst(),Set.of()).contains(recipe.id))button("创建覆盖修改",canvasX,bodyY+24,100,()->{var current=Objects.requireNonNull(EditorRuntime.recipe(recipe.id));selectLoaded(current);});button("查看数据",propX,bodyY,propW,()->showText(recipe.id,ProjectStore.JSON.toJson(recipe.data)));if(appliedRecipeSelection!=null)appliedUndoButton(canvasX,bodyY+48);else button("按 ID 禁用",propX,bodyY+24,propW,this::deleteRecipe);return;}
        field("配方 ID",recipeIdText,s->{recipeIdText=s;changed();});
        choice("配方操作",recipe.operation,List.of("add","replace"),s->{if(!Set.of("add","replace").contains(s))throw new IllegalArgumentException("操作须为 add/replace/remove");recipe.operation=s;changed();});
        JsonObject data=recipe.data;
        if(Set.of("create:mixing","create:compacting").contains(data.get("type").getAsString())&&!data.has("heat_requirement"))data.addProperty("heat_requirement","none");
        var fan=FanProcessing.get(data.get("type").getAsString());
        if(fan!=null)choice("鼓风机加工方式",fan.name(),availableFanModes().stream().map(FanProcessing.Mode::name).toList(),v->{var mode=availableFanModes().stream().filter(m->m.name().equals(v)).findFirst().orElseThrow();FanProcessing.switchMode(data,mode.type());selectedSlot=null;changed();stage();rebuildWidgets();});
        var station=VintageProcessing.workstation(data.get("type").getAsString());
        if(station!=null)choice(station.selector(),VintageProcessing.stateLabel(data.get("type").getAsString()),station.types().stream().filter(EditorCompatibility::hasRecipeType).map(VintageProcessing::stateLabel).toList(),value->switchVintageState(station.types().stream().filter(EditorCompatibility::hasRecipeType).filter(type->VintageProcessing.stateLabel(type).equals(value)).findFirst().orElseThrow()));
        if(CraftingGrid.supports(data)){
            CraftingGrid.ensure(recipe);
            if(data.get("type").getAsString().equals("create:mechanical_crafting"))choice("输入网格",recipe.grid.size+"×"+recipe.grid.size,java.util.stream.IntStream.rangeClosed(1,9).mapToObj(n->n+"×"+n).toList(),v->{CraftingGrid.resize(recipe,Integer.parseInt(v.substring(0,v.indexOf('×'))));selectedSlot=null;canvasScroll=canvasScrollX=0;changed();stage();rebuildWidgets();});
            else choice("合成方式",data.get("type").getAsString().equals("minecraft:crafting_shaped")?"有序":"无序",List.of("有序","无序"),v->{CraftingGrid.setOrdered(recipe,v.equals("有序"));changed();stage();rebuildWidgets();});
        }
        var template=RecipeTemplates.get(data.get("type").getAsString());Set<String> templateKeys=new HashSet<>();
        if(template!=null)for(var entry:template.parameters()){
            var p=entry.getAsJsonObject();String key=p.get("key").getAsString();templateKeys.add(key);
            String label=p.has("label")?p.get("label").getAsString():key;
            if(p.has("unit"))label+="（"+p.get("unit").getAsString()+"）";
            if(p.has("min")||p.has("max"))label+=" · "+RecipeTemplate.range(p);
            if(p.has("values"))choice(label,data.has(key)?data.get(key).getAsString():"",java.util.stream.StreamSupport.stream(p.getAsJsonArray("values").spliterator(),false).map(JsonElement::getAsString).toList(),v->{data.add(key,java.util.stream.StreamSupport.stream(p.getAsJsonArray("values").spliterator(),false).filter(e->e.getAsString().equals(v)).findFirst().orElseThrow().deepCopy());changed();});
            else if(data.has(key))rootField(data,key,label);
        }
        if(recipe.simple){
            actionField("适配依据","查看原生字段与 JEI 槽位",()->showText("简单配方适配",SimpleRecipes.description(recipe.id,recipe.data)));
            for(String path:SimpleRecipeAdapter.parameters(data)){var parts=List.of(path.split("/"));JsonElement node=SimpleRecipeAdapter.get(data,parts);JsonObject parent=SimpleRecipeAdapter.get(data,parts.subList(0,parts.size()-1)).getAsJsonObject();simpleParameter(parent,parts.getLast(),"参数 /"+path);}
        }
        for(var e:data.entrySet())if(!recipe.simple&&!e.getKey().equals("type")&&!templateKeys.contains(e.getKey())&&e.getValue().isJsonPrimitive())rootField(data,e.getKey(),caption(e.getKey()));
        if(selectedSlot!=null)buildSlotFields(selectedSlot);
        {
            boolean existing=loaded.stream().anyMatch(r->r.id().equals(recipe.id));
            int w=Math.min(90,(canvasW-(existing?8:4))/(existing?3:2));
            if(data.has("results"))button("+ 产物",canvasX,bottom-26,w,()->addEntry(true,false)).active=canAdd(true,false);
            else button("代码",canvasX,bottom-26,w,this::recipeCode);
            button("配方操作",canvasX+w+4,bottom-26,w,this::processingActions);
            if(existing)button("禁用配方",canvasX+(w+4)*2,bottom-26,w,this::deleteRecipe).setTooltip(Tooltip.create(UiText.component("禁用当前配方，加入操作记录；一键应用后生效")));
        }
        if(data.has("sequence"))buildSequenceControls(data);
    }
    private void recipeCode(){openCode();}
    private void openCode(){
        stage();String key=recipe!=null?"recipe:"+recipe.id:item!=null?"item:"+item.id:"";
        codePages=CodePreview.pages(inspectionContent(),project);codeIndex=0;
        for(int i=0;i<codePages.size();i++)if(codePages.get(i).key().equals(key)){codeIndex=i;break;}
        tab=3;reviewMode=0;canvasScroll=canvasScrollX=listOffset=0;review=null;rebuildWidgets();
    }
    private EditorProject inspectionContent(){
        var content=EditorRuntime.content();
        if(recipe!=null)content.recipes.putIfAbsent(recipe.id,WorkspaceState.copy(recipe,RecipeDraft.class));
        if(item!=null)content.items.putIfAbsent(item.id,WorkspaceState.copy(item,ItemDraft.class));
        return content;
    }
    private void switchVintageState(String type){EditorRuntime.requireEditable();VintageProcessing.switchState(recipe.data,type);selectedSlot=null;changed();stage();rebuildWidgets();}
    private void processingActions(){
        stage();if(recipe!=null&&recipe.simple){showText("简单配方适配",SimpleRecipes.description(recipe.id,recipe.data));return;}var choices=new ArrayList<ChoiceScreen.Choice>();
        if(recipe.data.has("sequence"))choices.add(new ChoiceScreen.Choice("+ 步骤",this::addSequenceStep));
        if(recipe.data.has("ingredients")&&!CraftingGrid.supports(recipe.data)){
            if(canAdd(false,false))choices.add(new ChoiceScreen.Choice("+ 输入",()->addEntry(false,false)));
            if(canAdd(false,true))choices.add(new ChoiceScreen.Choice("+ 流体",()->addEntry(false,true)));
        }
        if(recipe.data.has("results")&&canAdd(true,true))choices.add(new ChoiceScreen.Choice("+ 流体产物",()->addEntry(true,true)));
        choices.add(new ChoiceScreen.Choice("查看代码",this::recipeCode));
        if(appliedRecipeSelection!=null)choices.add(new ChoiceScreen.Choice("撤销此条已应用修改",()->revokeAppliedRecipe(recipe.id)));
        else choices.add(new ChoiceScreen.Choice(project.recipes.containsKey(recipe.id)&&!EditorRuntime.state().applied.recipes.containsKey(recipe.id)&&loaded.stream().noneMatch(r->r.id().equals(recipe.id))?"移除未应用配方":"禁用此配方",this::deleteRecipe));
        minecraft.setScreen(new ChoiceScreen(this,"配方操作",choices));
    }
    private void appliedUndoButton(int x,int y){String id=appliedRecipeSelection==null?recipe.id:appliedRecipeSelection;var button=button("撤销此条修改",x,y,114,()->revokeAppliedRecipe(id));button.active=!EditorRuntime.locked()&&EditorRuntime.canApply()&&EditorRuntime.state().canRevokeRecipe(id);button.setTooltip(Tooltip.create(UiText.component("进入批量删除页，可选择多条修改统一提交；其他待办保留")));}
    private boolean canAdd(boolean output,boolean fluid){if(recipe!=null&&recipe.simple)return false;return !EditorRuntime.locked()&&RecipeLimits.forType(recipe.data.get("type").getAsString()).canAdd(recipe.data,output,fluid);}
    private void addEntry(boolean output,boolean fluid){EditorRuntime.requireEditable();if(!canAdd(output,fluid))throw new IllegalArgumentException("此配方的槽位数量已达上限");recipe.data.getAsJsonArray(output?"results":"ingredients").add(fluid?RecipeAdapters.parse("{amount:250}"):JsonNull.INSTANCE);changed();stage();rebuildWidgets();}
    private void buildSlotFields(RecipeCanvas.Slot slot){
        JsonElement element=slot.get();JsonObject object=element.isJsonObject()?element.getAsJsonObject():new JsonObject();
        String key=object.has("items")?"items":object.has("tag")?"tag":object.has("fluid")?"fluid":slot.output()?"id":"item";
        String id=object.has(key)?(key.equals("tag")?"#":"")+object.get(key).getAsString():"";
        field("选中槽 · "+slot.label(),id,s->{
            boolean tag=s.startsWith("#");String raw=tag?s.substring(1):s;EditorProject.requireId(raw);if(slot.output()&&tag)throw new IllegalArgumentException("输出不能是标签");
            JsonObject value=slot.get().isJsonObject()?slot.get().getAsJsonObject().deepCopy():new JsonObject();value.remove("item");value.remove("id");value.remove("tag");value.remove("fluid");value.remove("items");
            value.addProperty(tag?"tag":slot.fluid()?slot.output()?"id":"fluid":slot.output()?"id":"item",raw);
            if(slot.fluid()&&!slot.output())value.addProperty("type",tag?"neoforge:tag":"neoforge:single");if(value.has("components")&&!slot.output()&&!slot.fluid())value=RecipeCanvas.withComponents(value,value.getAsJsonObject("components"),false);slot.set(value);changed();
        });
        actionField("选择材料",slot.fluid()?"搜索流体":"搜索物品",()->minecraft.setScreen(new ChoiceScreen(this,slot.fluid()?"搜索流体":"搜索物品",SlotChoices.resources(slot,obj->accept(slot,obj)))));
        if(!slot.output())actionField("标签候选","选择当前材料的标签",()->{
            var candidates=SlotChoices.tags(slot,tag->{
                var value=slot.get().isJsonObject()?slot.get().getAsJsonObject().deepCopy():new JsonObject();
                value.remove("item");value.remove("fluid");value.remove("items");value.addProperty("tag",tag);
                if(slot.fluid())value.addProperty("type","neoforge:tag");
                if(!slot.fluid()&&value.has("components"))value=RecipeCanvas.withComponents(value,value.getAsJsonObject("components"),false);
                slot.set(value);changed();stage();
            });
            if(candidates.isEmpty())showText("标签候选","当前材料没有标签。请先选入物品或流体，也可在槽位 ID 中直接输入 #命名空间:标签。");
            else minecraft.setScreen(new ChoiceScreen(this,"选择当前材料的标签",candidates));
        });
        if(slot.fluid())field("流体量 mB",object.has("amount")?object.get("amount").getAsString():"250",s->{setSlotNumber(slot,"amount",s);});
        else if(!slot.output()&&recipe.simple&&object.has("count"))field("原料数量",object.get("count").getAsString(),v->setSlotNumber(slot,"count",v));
        else if(slot.output())field("产物数量",object.has("count")?object.get("count").getAsString():"1",s->setSlotNumber(slot,"count",s));
        if(slot.output()&&(!recipe.simple&&recipe.data.has("results")||recipe.simple&&object.has("chance"))){
            boolean weighted=recipe.data.has("sequence");field(weighted?"产物权重（非百分比）":"产出概率 0～1",object.has("chance")?object.get("chance").getAsString():"1",s->setSlotNumber(slot,"chance",s));
        }
        field("数据组件 JSON",object.has("components")?object.get("components").toString():"{}",s->{JsonObject value=slot.get().isJsonObject()?slot.get().getAsJsonObject().deepCopy():new JsonObject();JsonObject components=JsonParser.parseString(s).getAsJsonObject();slot.set(RecipeCanvas.withComponents(value,components,slot.output()));changed();});
    }
    private void setSlotNumber(RecipeCanvas.Slot slot,String key,String text){JsonObject v=slot.get().getAsJsonObject().deepCopy();double n=Double.parseDouble(text);if(!Double.isFinite(n))throw new IllegalArgumentException("无效数值");v.addProperty(key,n);slot.set(v);changed();}
    private void buildSequenceControls(JsonObject data){
        int count=data.getAsJsonArray("sequence").size();
        if(selectedStep>=count)selectedStep=-1;
        if(selectedStep>=0){
            formTop=bodyY+26;int w=(propW-9)/4;
            button("←",propX,bodyY,w,()->moveStep(-1));button("→",propX+w+3,bodyY,w,()->moveStep(1));
            button("删除",propX+(w+3)*2,bodyY,w,()->{data.getAsJsonArray("sequence").remove(selectedStep);selectedStep=-1;selectedSlot=null;changed();rebuildWidgets();});
            button("类型",propX+(w+3)*3,bodyY,w,()->{
                List<ChoiceScreen.Choice> choices=new ArrayList<>();for(String type:assemblyTypes())choices.add(new ChoiceScreen.Choice(RecipeAdapters.get(type).title(),()->replaceStep(type)));
                minecraft.setScreen(new ChoiceScreen(this,"更换步骤类型",choices));
            });
            JsonObject step=data.getAsJsonArray("sequence").get(selectedStep).getAsJsonObject();
            if(step.get("type").getAsString().equals("create:deploying")&&!step.has("keep_held_item"))step.addProperty("keep_held_item",false);
            for(String key:List.of("processing_time","keep_held_item"))rootField(step,key,"步骤 "+(selectedStep+1)+" · "+caption(key));
            if(VintageProcessing.supports(step.get("type").getAsString()))for(String key:List.of("speedLimits","fragile","hammer_blows","anvil_block"))rootField(step,key,"步骤 "+(selectedStep+1)+" · "+caption(key));
        }
    }
    private List<String> assemblyTypes(){var types=new ArrayList<>(List.of("create:deploying","create:pressing","create:cutting","create:filling"));types.addAll(VintageProcessing.assemblyTypes().stream().filter(EditorCompatibility::hasRecipeType).toList());return types;}
    private void addSequenceStep(){var choices=new ArrayList<ChoiceScreen.Choice>();for(String type:assemblyTypes())choices.add(new ChoiceScreen.Choice(RecipeAdapters.get(type).title(),()->{RecipeCanvas.addStep(recipe.data,type);changed();}));minecraft.setScreen(new ChoiceScreen(this,"选择加工步骤",choices));}
    private void moveStep(int direction){JsonArray array=recipe.data.getAsJsonArray("sequence");int to=selectedStep+direction;if(to<0||to>=array.size())return;JsonElement old=array.get(selectedStep);array.set(selectedStep,array.get(to));array.set(to,old);selectedStep=to;selectedSlot=null;changed();rebuildWidgets();}
    private void replaceStep(String type){JsonArray array=recipe.data.getAsJsonArray("sequence");RecipeCanvas.addStep(recipe.data,type);JsonElement added=array.remove(array.size()-1);array.set(selectedStep,added);selectedSlot=null;changed();}
    private void buildItem(){
        if(item==null)return;
        if(EditorRuntime.locked()){button("查看物品数据",canvasX,bodyY+82,110,()->showText("等待事务完成",ProjectStore.JSON.toJson(item)));return;}
        if(readOnly){button("查看手写来源",propX,bodyY,propW,()->showText("此物品已转为手写",item.id+"\n请从更多 → 脚本管理编辑源文件"));button("创建覆盖修改",canvasX,bodyY+24,100,()->{String id=item.id;item=new ItemDraft();item.id=id;item.register=false;item.texture="";item.name="";itemIdText=id;readOnly=false;dirty=false;rebuildWidgets();});return;}
        field("物品 ID（可从 JEI 拖入已有物品）",itemIdText,s->{itemIdText=s;changed();});

        field("显示名称",item.name,s->{item.name=s;changed();});
        if(!dev.kubejseditor.model.RegistrationMembers.crop(item))field("贴图资源位置",item.texture,s->{item.texture=s;changed();});
        field("描述（使用 | 分隔多行）",String.join("|",item.tooltip),s->{item.tooltip=s.isBlank()?new ArrayList<>():new ArrayList<>(Arrays.asList(s.split("\\|",-1)));changed();});
        String kind=ItemKinds.kind(item);var keys=new ArrayList<String>();
        if(item.register&&kind.equals("block"))blockTypeField();
        if(!item.register)keys.addAll(List.of("maxStackSize","maxDamage","burnTime","nutrition","saturation"));
        else{if(ItemKinds.tool(kind)||ItemKinds.armor(kind))keys.add("maxDamage");else keys.addAll(List.of("maxStackSize","burnTime"));if(kind.equals("food"))keys.addAll(List.of("nutrition","saturation"));}
        if(item.properties.has("maxDamage")&&!keys.contains("maxDamage"))keys.add("maxDamage");
        for(String key:keys)field(caption(key)+"（留空保留默认）",item.properties.has(key)?item.properties.get(key).getAsString():"",s->{if(s.isBlank())item.properties.remove(key);else item.properties.addProperty(key,Double.valueOf(s));changed();});
        if(!item.register)choice("作为食物",String.valueOf(item.food),List.of("false","true"),s->{item.food=Boolean.parseBoolean(s);changed();});
        if(item.register)for(var entry:item.settings.entrySet()){String key=entry.getKey();if(kind.equals("sword")&&key.equals("miningSpeed"))continue;field(caption(key),entry.getValue().getAsString(),s->{double n=Double.parseDouble(s);if(!Double.isFinite(n))throw new IllegalArgumentException("请输入有限数值");item.settings.addProperty(key,n);changed();});}
        if(item.register&&kind.equals("block"))blockFields();
        if(!item.register){
        field("属性修饰符 JSON 列表",ProjectStore.JSON.toJson(item.attributes),s->{var type=new com.google.gson.reflect.TypeToken<List<AttributeDraft>>(){}.getType();item.attributes=ProjectStore.JSON.fromJson(s,type);item.replaceAttributes=true;changed();});
        for(int i=0;i<item.attributes.size();i++){
            int index=i;AttributeDraft attribute=item.attributes.get(i);String label="属性 "+(i+1)+" · ";
            field(label+"资源 ID",attribute.attribute,s->{EditorProject.requireId(s);attribute.attribute=s;item.replaceAttributes=true;changed();});
            field(label+"数值",String.valueOf(attribute.amount),s->{double amount=Double.parseDouble(s);if(!Double.isFinite(amount))throw new IllegalArgumentException("属性数值必须有限");attribute.amount=amount;item.replaceAttributes=true;changed();});
            choice(label+"运算",attribute.operation,List.of("add_value","add_multiplied_base","add_multiplied_total"),s->{attribute.operation=s;item.replaceAttributes=true;changed();});
            choice(label+"装备位置",attribute.slot,List.of("any","mainhand","offhand","hand","head","chest","legs","feet","armor","body"),s->{attribute.slot=s;item.replaceAttributes=true;changed();});
            choice(label+"移除修饰符","false",List.of("false","true"),s->{if(s.equals("true")){item.attributes.remove(index);item.replaceAttributes=true;changed();}});
        }
        button("添加攻击",canvasX,bodyY+82,76,()->{var a=new AttributeDraft();a.id="kubejs_editor:attack_"+item.attributes.size();item.attributes.add(a);changed();rebuildWidgets();});
        button("添加护甲",canvasX+80,bodyY+82,76,()->{var a=new AttributeDraft();a.attribute="minecraft:generic.armor";a.slot="chest";a.id="kubejs_editor:armor_"+item.attributes.size();item.attributes.add(a);changed();rebuildWidgets();});
        }
        button("取消本条修改",canvasX,bottom-24,96,()->cancelOperation(new PendingOperation("item",item.id,"物品修改")));
        button("导入 PNG",canvasX+100,bottom-24,76,this::importTexture);
        if(item.register)button("删除注册物品",canvasX,bodyY+82,110,()->{stage();confirmDeleteItem(item.id);}).active=EditorRuntime.canApply();
        ghosts.add(new GhostTarget(new Rect2i(canvasX+24,bodyY+20,36,36),false,obj->run(()->{if(obj instanceof ItemStack stack){if(item.register)throw new IllegalArgumentException("请从“修改现有 → 现有物品”选择要修改的对象");item.id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();itemIdText=item.id;item.name="";loadAttributes(stack);changed();rebuildWidgets();}})));
    }
    private void blockTypeField(){
        if(item.block==null)item.block=new dev.kubejseditor.model.BlockOptions();var b=item.block;
        actionField("方块类型",BlockKinds.NAMES.get(b.type),()->{
            var choices=new ArrayList<ChoiceScreen.Choice>();for(var type:BlockKinds.NAMES.entrySet())choices.add(new ChoiceScreen.Choice(type.getValue(),()->{BlockKinds.initialize(item,type.getKey());changed();rebuildWidgets();}));
            minecraft.setScreen(new ChoiceScreen(this,"选择方块类型",choices));
        });
    }
    private void blockFields(){
        var b=item.block;
        if(b.type.equals("crop")){cropFields();return;}
        if(b.type.equals("storage")){
            field("库存行数（1～9）",String.valueOf(b.storage.rows),v->{b.storage.rows=boundedInteger(v,1,9);changed();});
            field("库存列数（1～12）",String.valueOf(b.storage.columns),v->{b.storage.columns=boundedInteger(v,1,12);changed();});
            field("单格容量（1～4096）",String.valueOf(b.storage.slotCapacity),v->{b.storage.slotCapacity=boundedInteger(v,1,4096);changed();});
        }
        if(b.type.equals("door")||b.type.equals("trapdoor"))choice("门打开方式",b.behaviour,List.of("oak","iron"),s->{b.behaviour=s;changed();});
        if(b.type.equals("button"))choice("按钮触发方式",b.behaviour,List.of("oak","stone"),s->{b.behaviour=s;changed();});
        if(b.type.equals("pressure_plate"))choice("压力板检测",b.behaviour,List.of("oak","stone"),s->{b.behaviour=s;changed();});
        choice("方块声音",b.sound,BlockKinds.SOUNDS,s->{b.sound=s;changed();});
        choice("渲染方式",b.render,List.of("solid","cutout","cutout_mipped","translucent"),s->{b.render=s;b.opaque=s.equals("solid");changed();});
        choice("阻挡光线",String.valueOf(b.opaque),List.of("false","true"),s->{b.opaque=Boolean.parseBoolean(s);changed();});
        if(!Set.of("button","pressure_plate").contains(b.type))choice("碰撞",String.valueOf(b.collision),List.of("false","true"),s->{b.collision=Boolean.parseBoolean(s);changed();});
        choice("掉落自身",String.valueOf(b.dropSelf),List.of("false","true"),s->{b.dropSelf=Boolean.parseBoolean(s);changed();});
        choice("不可破坏",String.valueOf(b.unbreakable),List.of("false","true"),s->{b.unbreakable=Boolean.parseBoolean(s);changed();});
        choice("需要正确工具",String.valueOf(b.requiresTool),List.of("false","true"),s->{b.requiresTool=Boolean.parseBoolean(s);changed();});
        choice("挖掘工具",b.miningTool,List.of("none","pickaxe","axe","shovel","hoe"),s->{b.miningTool=s;changed();});
        choice("最低挖掘等级",b.miningLevel,List.of("none","stone","iron","diamond"),s->{b.miningLevel=s;changed();});
        field("滑度（0～1）",String.valueOf(b.slipperiness),s->{b.slipperiness=finiteNumber(s);changed();});
        field("移动速度倍率",String.valueOf(b.speedFactor),s->{b.speedFactor=finiteNumber(s);changed();});
        field("跳跃倍率",String.valueOf(b.jumpFactor),s->{b.jumpFactor=finiteNumber(s);changed();});
        if(b.type.equals("button")||b.type.equals("pressure_plate"))field("保持时间（tick）",String.valueOf(b.pressedTicks),s->{b.pressedTicks=Integer.parseInt(s);changed();});
        if(b.type.equals("door")){field("门上半贴图",b.topTexture,s->{b.topTexture=s;changed();});field("门背包图标",b.itemTexture,s->{b.itemTexture=s;changed();});}
    }
    private static int boundedInteger(String text,int min,int max){int v=Integer.parseInt(text);if(v<min||v>max)throw new IllegalArgumentException("请输入 "+min+"～"+max+" 的整数");return v;}
    private void cropFields(){
        var c=item.block.crop;
        actionField("种子 ID",item.id+"_seeds",()->{});
        field("种子名称",c.seedName,v->{c.seedName=v;changed();});field("种子图标",c.seedTexture,v->{c.seedTexture=v;changed();});
        choice("生长阶段数（2～16）",String.valueOf(c.stages),java.util.stream.IntStream.rangeClosed(2,16).mapToObj(String::valueOf).toList(),v->{int n=boundedInteger(v,2,16);while(c.textures.size()<n)c.textures.add(c.textures.getLast());while(c.textures.size()>n)c.textures.removeLast();c.stages=n;changed();rebuildWidgets();});
        for(int i=0;i<c.stages;i++){final int stage=i;field("阶段 "+i+" 材质",c.textures.get(i),v->{c.textures.set(stage,v);changed();});}
        field("成熟产物 ID",c.output,v->{c.output=v;changed();});field("产物数量（1～64）",String.valueOf(c.outputCount),v->{c.outputCount=boundedInteger(v,1,64);changed();});
        choice("掉落种子",String.valueOf(c.seedDrops),List.of("true","false"),v->{c.seedDrops=Boolean.parseBoolean(v);changed();});
        choice("原版自然生长",String.valueOf(c.vanillaGrowth),List.of("true","false"),v->{c.vanillaGrowth=Boolean.parseBoolean(v);changed();rebuildWidgets();});
        if(!c.vanillaGrowth)field("生长速度（0 停止，越大越快）",String.valueOf(c.growthSpeed),v->{double speed=finiteNumber(v);if(speed<0)throw new IllegalArgumentException("速度不能为负数");c.growthSpeed=speed;changed();});
        choice("原版骨粉增长",String.valueOf(c.vanillaBonemeal),List.of("true","false"),v->{c.vanillaBonemeal=Boolean.parseBoolean(v);changed();rebuildWidgets();});
        if(!c.vanillaBonemeal)field("骨粉增长阶段（1～15）",String.valueOf(c.bonemealStages),v->{c.bonemealStages=boundedInteger(v,1,15);changed();});
    }
    private static double finiteNumber(String text){double value=Double.parseDouble(text);if(!Double.isFinite(value))throw new IllegalArgumentException("请输入有限数值");return value;}
    private void confirmDeleteItem(String id){run(()->{
        EditorRuntime.requireEditable();stage();
        boolean applied=EditorRuntime.state().applied.items.containsKey(id);
        var current=applied?EditorRuntime.state().applied.items.get(id):project.items.get(id);
        if(current==null||!current.register||applied&&!EditorRuntime.state().canDeleteRegisteredItem(id))throw new IllegalArgumentException("只能删除编辑器拥有的注册物品；手写注册请编辑来源脚本");
        if(applied){minecraft.setScreen(new AppliedDeletionScreen(this,id,true));return;}var blockers=applied?EditorRuntime.itemDeletionBlockers(id):List.<String>of();
        if(!blockers.isEmpty()){showText("无法删除注册物品","请先处理并应用以下引用，再删除物品。不会自动删除其他记录。\n\n"+id+"\n\n"+String.join("\n",blockers));return;}
        String name=current.name.isBlank()?id:current.name;
        String message=applied?"将移除编辑器对此物品的注册、名称、描述和贴图记录，保存备份后等待重启生效。背包或世界中已有的此物品／方块可能消失。其他待办不会提交，可从更多整批回滚。":"将删除这条未应用的物品注册。其他待办保留；引用它的配方会标记缺失物品。";
        minecraft.setScreen(new ConfirmScreen(confirmed->{minecraft.setScreen(this);if(confirmed)run(()->{
            if(applied)EditorRuntime.deleteRegisteredItem(id);else EditorRuntime.cancel(new PendingOperation("item",id,"物品注册"));
            if(item!=null&&item.id.equals(id)){item=null;dirty=false;selectedSlot=null;invalidFields.clear();}
            recipe=null;review=null;tab=applied?4:0;existingMode=1;
            status=applied?"删除已保存，等待重启；其他待办保留":"已删除未应用的物品注册";rebuildWidgets();
        });},UiText.component("确认删除注册物品"),Component.literal(name+"\n"+id+"\n\n"+message),UiText.component("确认删除"),UiText.component("取消")));
    });}
    private void importTexture(){
        if(item!=null&&dev.kubejseditor.model.RegistrationMembers.crop(item)){
            var choices=new ArrayList<ChoiceScreen.Choice>();choices.add(new ChoiceScreen.Choice("种子图标",()->importTexture("seed")));
            for(int i=0;i<item.block.crop.stages;i++){final int stage=i;choices.add(new ChoiceScreen.Choice("生长阶段 "+i,()->importTexture("stage_"+stage)));}
            minecraft.setScreen(new ChoiceScreen(this,"选择作物材质用途",choices));return;
        }
        if(item!=null&&item.register&&ItemKinds.kind(item).equals("block")&&BlockKinds.options(item).type.equals("door")){
            minecraft.setScreen(new ChoiceScreen(this,"选择门贴图用途",List.of(new ChoiceScreen.Choice("门下半贴图",()->importTexture("main")),new ChoiceScreen.Choice("门上半贴图",()->importTexture("top")),new ChoiceScreen.Choice("门背包图标",()->importTexture("inventory")))));return;
        }importTexture("main");
    }
    private void importTexture(String role){
        run(()->{
            if(item==null)throw new IllegalArgumentException("请选择物品");
            try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
                var patterns=stack.mallocPointer(1);patterns.put(stack.UTF8("*.png")).flip();
                String chosen=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("选择物品 PNG 材质","",patterns,"PNG",false);
                if(chosen==null)return;byte[] bytes=Files.readAllBytes(Path.of(chosen));
                try(var image=com.mojang.blaze3d.platform.NativeImage.read(bytes)){if(image.getWidth()>4096||image.getHeight()>4096)throw new IllegalArgumentException("PNG 尺寸超过 4096");}
                EditorProject.requireId(item.id);String[] id=item.id.split(":",2);
                
                EditorResources.stageTexture(EditorRuntime.root(),project,item,bytes,role);changed();status="材质已加入本次更改";rebuildWidgets();
            }
        });
    }
    private void loadAttributes(ItemStack stack){
        item.attributes.clear();item.replaceAttributes=true;
        var modifiers=stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        for(var entry:modifiers.modifiers()){
            AttributeDraft a=new AttributeDraft();a.attribute=entry.attribute().unwrapKey().orElseThrow().location().toString();a.id=entry.modifier().id().toString();a.amount=entry.modifier().amount();a.operation=entry.modifier().operation().getSerializedName();a.slot=entry.slot().getSerializedName();item.attributes.add(a);
        }
    }
    private void buildConfig(){
        if(selectedConfig==null&&!configs.isEmpty()){selectedConfig=configs.getFirst();configText=selectedConfig.text(selectedConfig.current());}
        if(selectedConfig==null)return;
        if(EditorRuntime.locked())return;
        var e=selectedConfig;
        if(!e.editable()){button("此参数暂不支持编辑",canvasX,bodyY+24,120,()->showText("参数类型",e.valueType().getName()+"\n"+ConfigLabels.description(e)));return;}
        if(e.valueType()==Boolean.class)choice("参数值",configText.equals("true")?"开启":"关闭",List.of("关闭","开启"),s->configText=String.valueOf(s.equals("开启")));
        else field("参数值",configText,s->configText=s);
        button("加入操作记录",canvasX,bodyY+24,90,()->{EditorRuntime.requireEditable();CreateConfigs.queue(e,configText);try{EditorRuntime.saveDraft();}catch(Exception ex){throw new IllegalStateException(ex);}review=null;status="参数已加入操作记录，需重启生效";rebuildWidgets();});
        button("默认值",canvasX+94,bodyY+24,72,()->{configText=e.text(e.spec().getDefault());rebuildWidgets();});
        button("清空参数更改",canvasX,bottom-24,100,()->{CreateConfigs.clear();project.configs.clear();review=null;status="已清空待应用参数";});
        button("参数说明",canvasX,bodyY+52,90,()->showText(ConfigLabels.name(e),ConfigLabels.description(e)));
    }
    private void buildForm(){
        int rows=Math.max(1,(bottom-formTop-16)/38);propertyOffset=Math.min(propertyOffset,Math.max(0,form.size()-rows));
        for(int i=propertyOffset;i<Math.min(form.size(),propertyOffset+rows);i++){
            Form f=form.get(i);int y=formTop+(i-propertyOffset)*38+13;
            if(f.action!=null){button(f.value,propX,y,propW,f.action).active=!EditorRuntime.locked();continue;}
            if(!f.choices.isEmpty()){
                var choiceButton=button(formOptionName(f),propX,y,propW,()->{int index=f.choices.indexOf(f.value);f.set.accept(f.choices.get((index+1)%f.choices.size()));rebuildWidgets();});
                if(f.label.equals("鼓风机加工方式"))choiceButton.setTooltip(Tooltip.create(Component.literal(FanProcessing.get(recipe.data.get("type").getAsString()).condition())));
                continue;
            }
            EditBox box=new EditBox(font,propX,y,propW,19,Component.literal(f.label));box.setMaxLength(32767);box.setValue(f.value);
            box.setEditable(!EditorRuntime.locked());
            box.setResponder(s->{try{f.set.accept(s);invalidFields.remove(f.label);box.setTextColor(0xFFE2E8F0);}catch(Exception e){invalidFields.put(f.label,e.getMessage());box.setTextColor(0xFFFCA5A5);status=e.getMessage();}});addRenderableWidget(box);
        }
    }
    private String formOptionName(Form f){
        if(f.label.equals("门打开方式"))return f.value.equals("iron")?"仅红石":"手动与红石";
        if(f.label.equals("按钮触发方式"))return f.value.equals("stone")?"仅手动":"手动与箭矢";
        if(f.label.equals("压力板检测"))return f.value.equals("stone")?"生物":"所有实体";
        if(f.label.equals("方块声音"))return switch(f.value){case "stone"->"石材";case "wood"->"木材";case "glass"->"玻璃";case "metal"->"金属";case "gravel"->"沙砾";case "grass"->"草地";case "sand"->"沙子";case "wool"->"羊毛";default->"无声";};
        if(f.label.equals("渲染方式"))return switch(f.value){case "solid"->"不透明";case "cutout"->"镂空";case "cutout_mipped"->"镂空（远处平滑）";default->"半透明";};
        if(f.label.equals("挖掘工具"))return switch(f.value){case "pickaxe"->"镐";case "axe"->"斧";case "shovel"->"锹";case "hoe"->"锄";default->"未指定";};
        if(f.label.equals("最低挖掘等级"))return switch(f.value){case "stone"->"石及以上";case "iron"->"铁及以上";case "diamond"->"钻石及以上";default->"不限定";};
        return optionName(f.value);
    }
    private void buildReview(){
        issues=EditorRuntime.validationIssues();
        if(review==null&&issues.isEmpty()&&!EditorRuntime.locked())try{review=EditorRuntime.review();}catch(Exception e){issues=List.of(new ValidationIssue("project","应用检查",e.getMessage()));}
        codePages=CodePreview.pages(inspectionContent(),project);codeIndex=Math.min(codeIndex,Math.max(0,codePages.size()-1));
        List<Row> list=new ArrayList<>();
        if(reviewMode==0)for(int i=0;i<codePages.size();i++){int n=i;var page=codePages.get(i);list.add(new Row(page.title(),page.key(),()->{codeIndex=n;canvasScroll=canvasScrollX=0;rebuildWidgets();}));}
        if(reviewMode==1)for(var issue:issues)list.add(new Row("错误 · "+issue.key().substring(issue.key().indexOf(':')+1),issue.text(),()->{
            var op=EditorRuntime.operations().stream().filter(v->v.key().equals(issue.key())).findFirst();if(op.isPresent()){selectOperation(op.get());focusIssue(issue);}else showText("检查详情",issue.text());
        }));
        if(reviewMode==2&&review!=null)for(int i=0;i<review.changes().size();i++){int n=i;var c=review.changes().get(i);list.add(new Row(c.path().getFileName().toString(),c.path().toString(),()->{reviewIndex=n;canvasScroll=canvasScrollX=0;rebuildWidgets();}));}rows=list;
        listOffset=Math.min(listOffset,Math.max(0,rows.size()-visibleRows()));
        String[] modes={"代码","检查结果","文件差异"};for(int i=0;i<modes.length;i++){int mode=i;button((reviewMode==i?"● ":"")+modes[i],listX+i*77,48,74,()->{reviewMode=mode;listOffset=canvasScroll=canvasScrollX=0;rebuildWidgets();});}
        button("重新检查",listX+234,48,80,()->{review=null;rebuildWidgets();});
        button("复制当前内容",listX,74,100,()->minecraft.keyboardHandler.setClipboard(reviewText()));
        if(!EditorRuntime.activationError.isBlank())button("错误详情",listX+104,74,80,()->showText("加载结果",EditorRuntime.activationError));
        footer();
    }
    private String reviewText(){
        if(reviewMode==0)return codePages.isEmpty()?"暂无表单记录或源码待办。手写文件可从更多 → 脚本管理查看。":codePages.get(codeIndex).content();
        if(reviewMode==1){
            String requirements=review!=null?(review.restartRequired()?"生效要求：需要重启游戏。":"生效要求：应用后自动按需重载。"):
                EditorRuntime.state().activation!=null?(EditorRuntime.state().activation.restart?"生效要求：已保存，等待重启验证。":"生效要求：等待本轮加载验证。"):"尚无可应用的文件更改。";
            return requirements+"\n\n"+(issues.isEmpty()?"检查通过。":issues.stream().map(ValidationIssue::text).collect(java.util.stream.Collectors.joining("\n\n")))+(EditorRuntime.activationError.isBlank()?"":"\n\n加载错误：\n"+EditorRuntime.activationError);
        }
        if(review==null)return issues.isEmpty()?"暂无可用文件差异；等待中的事务请完成验证或回滚。":"存在检查错误，修正后才能生成完整文件差异。原始数据仍可在代码页查看。";
        return review.changes().isEmpty()?"文件内容没有变化。":diff(review.changes().get(Math.min(reviewIndex,review.changes().size()-1)));
    }
    private void focusIssue(ValidationIssue issue){
        status="错误："+issue.message();if(recipe==null||!issue.key().equals("recipe:"+recipe.id))return;
        slots=RecipeCanvas.slots(recipe);
        selectedSlot=RecipeIssueTarget.find(recipe.data,slots,issue.pointer());
        if(selectedSlot!=null&&recipe.data.has("sequence")){canvasScrollX=Math.max(0,(int)(selectedSlot.x()*SequenceLayout.scale(canvasW-12))-20);canvasScroll=Math.max(0,selectedSlot.y()-100);}
        propertyOffset=0;rebuildWidgets();
        for(int i=0;i<form.size();i++)if(selectedSlot!=null&&form.get(i).label.startsWith("选中槽")||selectedSlot==null&&!issue.pointer().isEmpty()&&form.get(i).label.equals(caption(issue.pointer().substring(1)))){propertyOffset=i;rebuildWidgets();break;}
    }
    private String diff(FileTransaction.Change c){
        String[] a=c.beforeText().split("\n",-1),b=c.afterText().split("\n",-1);int start=0;while(start<a.length&&start<b.length&&a[start].equals(b[start]))start++;
        int end=0;while(end<a.length-start&&end<b.length-start&&a[a.length-1-end].equals(b[b.length-1-end]))end++;
        StringBuilder s=new StringBuilder(c.path()+"\n");for(int i=Math.max(0,start-2);i<start;i++)s.append("  ").append(a[i]).append('\n');for(int i=start;i<a.length-end;i++)s.append("- ").append(a[i]).append('\n');for(int i=start;i<b.length-end;i++)s.append("+ ").append(b[i]).append('\n');return s.toString();
    }
    private void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFA101923);g.fill(listX-2,bodyY-24,listX+listW+2,bottom,0xFF1B2735);g.fill(canvasX-2,bodyY-24,canvasX+canvasW+2,bottom,0xFF16212E);g.fill(propX-2,bodyY-24,propX+propW+2,bottom,0xFF1B2735);
        if(tab==3)g.fill(canvasX-2,bodyY-24,propX+propW+2,bottom,0xFF16212E);
        g.drawString(font,"KubeJS Editor",listX,topPos+4,0xFF7DD3FC,false);
        if(tab!=3&&tab!=4)for(int divider:new int[]{canvasX-5,propX-5})g.fill(divider,bodyY-22,divider+2,bottom,mouseX>=divider-3&&mouseX<=divider+4?0xFF7DD3FC:0xFF263749);
        if(tab!=3&&tab!=4)g.drawString(font,UiText.format("操作记录 · %s 条",EditorRuntime.operations().size()),listX,bodyY-36,0xFF94A3B8,false);
        for(int i=listOffset;i<Math.min(rows.size(),listOffset+visibleRows());i++){int y=bodyY+(i-listOffset)*30;if(mouseX>=listX&&mouseX<listX+listW&&mouseY>=y&&mouseY<y+29)g.fill(listX,y,listX+listW,y+29,0xFF33465C);var row=rows.get(i);boolean error=row.operation()!=null&&issues.stream().anyMatch(v->v.key().equals(row.operation().key()));
            if(tab==4){String badge=row.detail.contains("删除失败")?"删除失败，须回滚":row.detail.contains("删除已保存")?"删除等待重启":row.detail.contains("撤销失败")?"撤销失败，须回滚":row.detail.contains("正在撤销")?"正在撤销":row.detail.contains("加载失败")?"加载失败，须回滚":row.detail.contains("等待重启")?"等待重启":row.detail.contains("等待加载")?"等待加载验证":"已生效";g.drawString(font,badge,listX+3,y+1,badge.equals("已生效")?0xFF86EFAC:0xFFFDE68A,false);g.drawString(font,font.plainSubstrByWidth(row.label,listW-(row.operation()==null?5:26)),listX+3,y+15,0xFFCBD5E1,false);}
            else if(row.operation()!=null){g.drawString(font,row.operation().title()+(error?" · 有错误":""),listX+3,y+1,error?0xFFFCA5A5:0xFF7DD3FC,false);g.drawString(font,font.plainSubstrByWidth(row.label,listW-26),listX+3,y+15,error?0xFFFCA5A5:0xFFCBD5E1,false);}
            else g.drawString(font,font.plainSubstrByWidth(row.label,listW-5),listX+3,y+8,0xFFCBD5E1,false);}

        if(tab==3){renderReview(g);return;}
        if(tab==4){
            int y=bodyY+12;for(String line:List.of("已有修改",existingMode==0?"选择已应用的配方；源码见更多 → 脚本管理":"统一管理物品注册与属性修改",EditorRuntime.state().activation==null?"没有等待生效的事务":EditorRuntime.state().activation.restart?"存在等待重启的修改":"存在等待成功重载的修改")){g.drawString(font,font.plainSubstrByWidth(line,canvasW-16),canvasX+8,y,0xFFCBD5E1,false);y+=20;}if(!EditorRuntime.activationError.isBlank())g.drawString(font,font.plainSubstrByWidth(EditorRuntime.activationError,canvasW-16),canvasX+8,y+10,0xFFFCA5A5,false);return;
        }
        for(int i=propertyOffset;i<Math.min(form.size(),propertyOffset+Math.max(1,(bottom-formTop-16)/38));i++)g.drawString(font,font.plainSubstrByWidth(UiText.text(form.get(i).label),propW),propX,formTop+(i-propertyOffset)*38+2,0xFF94A3B8,false);
        if(tab==2&&createTab==1){renderConfig(g);return;}
        if(item!=null){if(item!=null){var id=ResourceLocation.tryParse(item.id);ItemStack stack=id!=null&&BuiltInRegistries.ITEM.containsKey(id)?new ItemStack(BuiltInRegistries.ITEM.get(id)):new ItemStack(net.minecraft.world.item.Items.PAPER);g.fill(canvasX+24,bodyY+20,canvasX+60,bodyY+56,0xFF334155);g.renderItem(stack,canvasX+34,bodyY+30);g.drawString(font,item.register?ItemKinds.NAMES.get(ItemKinds.kind(item))+(id!=null&&BuiltInRegistries.ITEM.containsKey(id)?" · 已注册，修改需重启":" · 重启后注册"):"已有物品 · 拖入选择",canvasX+6,bodyY+64,0xFFCBD5E1,false);}return;}
        if(recipe==null){canvasMessage(g,tab==5?"选择配方或从 JEI 打开实际来源，也可拖入机器；自动读取可编辑字段。":UiText.text("通过上方入口选择或新增内容"),bodyY+18,0xFF94A3B8);return;}
        if(recipe.operation.equals("remove")){g.drawString(font,appliedRecipeSelection==null?"配方禁用 · 一键应用后生效":"已禁用此配方 · 撤销修改可恢复原来源",canvasX+8,bodyY+4,0xFFFCA5A5,false);return;}
        if(!EditorCompatibility.hasRecipeType(recipe.data.get("type").getAsString())){canvasMessage(g,EditorCompatibility.unavailable(recipe.data.get("type").getAsString())+"；可取消此条待办，安装对应模组后继续编辑。",bodyY+52);return;}
        if(VintageProcessing.dynamic(recipe.data.get("type").getAsString())){canvasMessage(g,VintageProcessing.explanation(recipe.data.get("type").getAsString()),bodyY+56);return;}
        if(readOnly&&EditorRuntime.locked()){g.drawString(font,"批次已提交，编辑已锁定；可查看或整批回滚",canvasX+8,bodyY+4,0xFFFDE68A,false);return;}
        if(readOnly&&slots.isEmpty()){canvasMessage(g,"只读 · "+status+"。可查看原始数据和适配说明。",bodyY+36);return;}
        renderRecipeCanvas(g,partial);
    }
    private Rect2i slotBounds(RecipeCanvas.Slot slot){return new Rect2i(canvasFit.px(slot.x()),canvasFit.py(slot.y()),canvasFit.size(22),canvasFit.size(22));}
    private Rect2i visibleSlotBounds(RecipeCanvas.Slot slot){var b=slotBounds(slot);int x=Math.max(canvasLeft()+1,b.getX()),y=Math.max(canvasTop(),b.getY()),right=Math.min(canvasRight()-1,b.getX()+b.getWidth()),end=Math.min(canvasBottom(),b.getY()+b.getHeight());return right>x&&end>y?new Rect2i(x,y,right-x,end-y):null;}
    private void renderRecipeCanvas(GuiGraphics g,float partial){
        int contentW=slots.stream().mapToInt(s->s.x()+30).max().orElse(100),contentH=slots.stream().mapToInt(s->s.y()+40).max().orElse(100);
        boolean sequence=recipe.data.has("sequence");String type=recipe.data.get("type").getAsString();
        if(sequence){contentW=Math.max(contentW,SequenceLayout.columns(recipe.data)*(SequenceLayout.CARD_WIDTH+8));contentH=Math.max(contentH,SequenceLayout.summaryY(recipe.data)+60);}
        else if(type.equals("create:mechanical_crafting")){contentW=Math.max(contentW,recipe.grid.size*24+152);}
        else if(FanProcessing.createType(type)&&recipe.grid==null){contentW=Math.max(contentW,212);contentH=Math.max(contentH,136);}
        int availableHeight=Math.max(12,canvasBottom()-canvasTop()-4);
        if(sequence){
            // About 2.5 cards per viewport, independent of the number of steps.
            double scale=SequenceLayout.scale(canvasW-12);sequenceScrollMax=Math.max(0,(int)Math.ceil(contentW*scale)-(canvasRight()-canvasLeft()));sequenceScrollYMax=Math.max(0,(int)Math.ceil(contentH*scale)-availableHeight);
            canvasScrollX=Math.min(canvasScrollX,sequenceScrollMax);canvasScroll=Math.min(canvasScroll,sequenceScrollYMax);
            canvasFit=new CanvasFit(canvasLeft()-canvasScrollX,canvasTop()+2-canvasScroll,scale);renderSequenceScrollbar(g);
        }else canvasFit=CanvasFit.centered(canvasX+6,canvasTop()+2,canvasW-12,availableHeight,contentW,contentH,2);
        var fan=FanProcessing.get(type);String heading=fan==null?VintageProcessing.workstationTitle(type,RecipeNames.station(type)):"鼓风机加工 · "+fan.name();
        g.drawString(font,font.plainSubstrByWidth(heading,canvasW-8),canvasX+4,bodyY+3,0xFF7DD3FC,false);
        g.enableScissor(canvasLeft(),canvasTop(),canvasRight(),canvasBottom());ghosts.clear();g.pose().pushPose();g.pose().translate(canvasFit.x(),canvasFit.y(),0);g.pose().scale((float)canvasFit.scale(),(float)canvasFit.scale(),1);
        if(sequence){
            var steps=recipe.data.getAsJsonArray("sequence");for(int i=0;i<steps.size();i++){
                int x=SequenceLayout.cardX(recipe.data,i),y=SequenceLayout.cardY(recipe.data,i);var step=steps.get(i).getAsJsonObject();String stepType=step.get("type").getAsString();
                g.fill(x,y,x+SequenceLayout.CARD_WIDTH,y+SequenceLayout.CARD_HEIGHT,i==selectedStep?0xFF315B75:0xFF223345);g.drawString(font,(i+1)+" · "+RecipeNames.station(stepType),x+4,y+4,0xFF7DD3FC,false);
                g.pose().pushPose();g.pose().translate(x+4,y+26,0);g.pose().scale(.66f,.66f,1);CreateSceneRenderer.draw(g,stepType,0,0,stepFluids(step));g.pose().popPose();
                g.drawString(font,"中间件",x+5,y+135,0xFF94A3B8,false);g.drawString(font,"→",x+63,y+115,0xFF7DD3FC,false);
            }
            int y=SequenceLayout.summaryY(recipe.data);g.drawString(font,"原料",8,y-12,0xFF94A3B8,false);g.drawString(font,"中间件",78,y-12,0xFF94A3B8,false);g.drawString(font,"最终产物",156,y-12,0xFF94A3B8,false);g.drawString(font,"循环 × "+recipe.data.get("loops").getAsString(),8,y+32,0xFF7DD3FC,false);
            double total=0;for(var e:recipe.data.getAsJsonArray("results"))if(e.isJsonObject())total+=e.getAsJsonObject().has("chance")?e.getAsJsonObject().get("chance").getAsDouble():1;
            int n=0;for(var e:recipe.data.getAsJsonArray("results")){int i=n++;if(!e.isJsonObject())continue;double w=e.getAsJsonObject().has("chance")?e.getAsJsonObject().get("chance").getAsDouble():1;g.drawString(font,String.format(Locale.ROOT,"%.1f%%",total>0?w/total*100:0),156+i%6*30,y+i/6*40+26,0xFF86EFAC,false);}
        }else{
            if(FanProcessing.createType(type)&&recipe.grid==null){if(!CreateSceneRenderer.draw(g,type,0,12,slots.stream().filter(RecipeCanvas.Slot::fluid).map(this::slotFluid).filter(f->!f.isEmpty()).toList()))renderCreateFallback(g,type,88,38);}
            if(type.equals("create:mechanical_crafting"))CreateSceneRenderer.draw(g,type,recipe.grid.size*24,Math.max(0,recipe.grid.size/2*24-20));
            boolean basin=type.equals("create:mixing")||type.equals("create:compacting")||VintageProcessing.basin(type);
            int arrow=recipe.grid!=null?recipe.grid.size*24+20:FanProcessing.createType(type)?basin?148:146:72;
            g.drawString(font,"→",arrow,recipe.grid!=null?recipe.grid.size/2*24+8:FanProcessing.createType(type)?basin?54:100:8,0xFF7DD3FC,false);
            if(processingCanvas()&&!basin){g.drawString(font,"原料",8,78,0xFF94A3B8,false);g.drawString(font,"产物",180,78,0xFF94A3B8,false);}
        }
        for(var slot:slots){int x=slot.x(),y=slot.y();g.fill(x-1,y-1,x+23,y+23,slot==selectedSlot?0xFF38BDF8:slot.fluid()?0xFF2563EB:0xFF475569);g.fill(x,y,x+22,y+22,0xFF0F172A);
            if(slot.fluid()&&WorkspaceJeiPlugin.runtime!=null){var fluid=slotFluid(slot);if(!fluid.isEmpty())WorkspaceJeiPlugin.runtime.getIngredientManager().getIngredientRenderer(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK).render(g,fluid,x+3,y+3);}else{var stack=slotStack(slot);if(!stack.isEmpty())g.renderItem(stack,x+3,y+3);}
            JsonElement e=slot.get();if(slot.fluid())g.drawString(font,"~",x+14,y+13,0xFF67E8F9,false);
            if(e.isJsonObject()){var o=e.getAsJsonObject();if(o.has("tag"))g.drawString(font,"#",x+1,y+1,0xFFFDE68A,false);if(o.has("count"))g.drawString(font,o.get("count").getAsString(),x+13,y+14,0xFFE2E8F0,false);if(o.has("amount"))g.drawString(font,o.get("amount").getAsString()+"mB",x,y+25,0xFF67E8F9,false);if(o.has("chance")&&!sequence)g.drawString(font,String.format(Locale.ROOT,"%.1f%%",o.get("chance").getAsDouble()*100),x,y+25,0xFF86EFAC,false);}
            var bounds=visibleSlotBounds(slot);if(bounds!=null)ghosts.add(new GhostTarget(bounds,slot.fluid(),obj->accept(slot,obj)));
        }g.pose().popPose();g.disableScissor();
    }
    private void renderSequenceScrollbar(GuiGraphics g){
        int x=canvasLeft(),y=bodyY+18,w=canvasRight()-canvasLeft();g.fill(x,y,x+w,y+9,0xFF263749);
        int thumb=sequenceScrollMax==0?w:Math.max(20,(int)((double)w*w/(w+sequenceScrollMax)));
        int offset=sequenceScrollMax==0?0:(int)((double)canvasScrollX/sequenceScrollMax*(w-thumb));
        g.fill(x+offset,y+1,x+offset+thumb,y+8,sequenceScrollMax==0?0xFF475569:0xFF7DD3FC);
    }
    private void scrollSequenceTo(double mouseX){int w=canvasRight()-canvasLeft(),thumb=sequenceScrollMax==0?w:Math.max(20,(int)((double)w*w/(w+sequenceScrollMax)));canvasScrollX=sequenceScrollMax==0?0:(int)Math.round(Math.clamp((mouseX-canvasLeft()-thumb/2.0)/Math.max(1,w-thumb),0,1)*sequenceScrollMax);}
    private List<FluidStack> stepFluids(JsonObject step){var all=RecipeCanvas.slots(step);return all.stream().filter(RecipeCanvas.Slot::fluid).map(this::slotFluid).filter(f->!f.isEmpty()).toList();}
    private void renderCreateFallback(GuiGraphics g,String type,int x,int y){String id=switch(type){case "create:splashing","create:haunting"->"create:encased_fan";case "create:sandpaper_polishing"->"create:sand_paper";case "create:item_application"->"create:super_glue";default->"minecraft:barrier";};g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(2,2,2);g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))),0,0);g.pose().popPose();}
    private ItemStack slotStack(RecipeCanvas.Slot slot){
        JsonElement e=slot.get();if(!e.isJsonObject())return ItemStack.EMPTY;JsonObject o=e.getAsJsonObject();String id=o.has("items")&&o.get("items").isJsonPrimitive()&&!o.get("items").getAsString().startsWith("#")?o.get("items").getAsString():o.has("id")?o.get("id").getAsString():o.has("item")?o.get("item").getAsString():null;
        if(o.has("tag")&&!slot.fluid()){
            var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,ResourceLocation.parse(o.get("tag").getAsString()));var values=BuiltInRegistries.ITEM.getTag(tag);if(values.isPresent()&&values.get().size()>0)return new ItemStack(values.get().get((int)(System.currentTimeMillis()/900%values.get().size())));
        }
        if(slot.fluid()){String fluid=o.has("fluid")?o.get("fluid").getAsString():id;if(fluid!=null){var value=BuiltInRegistries.FLUID.get(ResourceLocation.parse(fluid));return new ItemStack(value.getBucket());}return new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);}
        return id==null?ItemStack.EMPTY:new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }
    private FluidStack slotFluid(RecipeCanvas.Slot slot){
        JsonElement value=slot.get();if(!value.isJsonObject())return FluidStack.EMPTY;JsonObject o=value.getAsJsonObject();String id=o.has("fluid")?o.get("fluid").getAsString():o.has("id")?o.get("id").getAsString():null;
        if(o.has("tag")){
            var values=BuiltInRegistries.FLUID.getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID,ResourceLocation.parse(o.get("tag").getAsString())));
            if(values.isPresent()&&values.get().size()>0)id=BuiltInRegistries.FLUID.getKey(values.get().get((int)(System.currentTimeMillis()/900%values.get().size())).value()).toString();
        }
        if(id==null)return FluidStack.EMPTY;JsonObject encoded=new JsonObject();encoded.addProperty("id",id);encoded.addProperty("amount",o.has("amount")?o.get("amount").getAsInt():1000);if(o.has("components"))encoded.add("components",o.get("components"));
        return FluidStack.CODEC.parse(minecraft.level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),encoded).result().orElse(FluidStack.EMPTY);
    }
    private void renderReview(GuiGraphics g){
        int right=propX+propW;g.enableScissor(canvasX,74,right,96);
        g.drawString(font,"查看代码 · "+issues.size()+" 项检查问题"+(review==null?"":" · "+review.changes().size()+" 个文件 · "+(review.restartRequired()?"需要重启":"可重载")),canvasX+4,80,issues.isEmpty()?0xFF7DD3FC:0xFFFCA5A5,false);g.disableScissor();
        g.enableScissor(canvasX,bodyY,right,bottom);int y=bodyY+4-canvasScroll;
        for(String line:reviewText().split("\n",-1)){
            if(reviewMode==1){for(var wrapped:font.split(Component.literal(line),right-canvasX-12)){g.drawString(font,wrapped,canvasX+4,y,issues.isEmpty()?0xFFCBD5E1:0xFFFCA5A5,false);y+=12;}}
            else {g.drawString(font,line,canvasX+4-canvasScrollX,y,line.startsWith("+")?0xFF86EFAC:line.startsWith("-")?0xFFFCA5A5:0xFFCBD5E1,false);y+=11;}
        }g.disableScissor();
    }
    private void canvasMessage(GuiGraphics g,String message,int y){canvasMessage(g,message,y,0xFFFDE68A);}
    private void canvasMessage(GuiGraphics g,String message,int y,int color){g.enableScissor(canvasX,bodyY,canvasX+canvasW,bottom-30);for(var line:font.split(Component.literal(message),Math.max(20,canvasW-16))){g.drawString(font,line,canvasX+8,y,color,false);y+=12;}g.disableScissor();}
    private void renderConfig(GuiGraphics g){if(selectedConfig==null){canvasMessage(g,"当前没有可编辑的机械动力配置。请安装对应模组并进入世界后刷新；若当前版本不支持配置读取，可查看兼容信息。",bodyY+20);return;}g.enableScissor(canvasX,bodyY+82,canvasX+canvasW,bottom-28);int y=bodyY+92-canvasScroll;for(String line:List.of(ConfigLabels.name(selectedConfig),"分类："+ConfigLabels.category(selectedConfig),"当前："+ConfigLabels.value(selectedConfig.current()),"默认："+ConfigLabels.value(selectedConfig.spec().getDefault()),"范围："+(selectedConfig.spec().getRange()==null?"由模组校验":selectedConfig.spec().getRange()),"作用域："+dev.kubejseditor.config.ConfigNames.scope(selectedConfig.config().getType().name()),"生效：重启游戏")){g.drawString(font,font.plainSubstrByWidth(line,canvasW-8),canvasX+4,y,0xFFCBD5E1,false);y+=16;}g.disableScissor();}
    @Override public void tick(){super.tick();if(activationSeen!=EditorRuntime.state().activation){refreshRuntime();rebuildWidgets();}if(dirty&&!EditorRuntime.locked()&&++autosaveTicks>=20){autosaveTicks=0;try{
        String focus=getFocused() instanceof EditBox box?box.getMessage().getString():null;int cursor=getFocused() instanceof EditBox box?box.getCursorPosition():0;
        stage();rebuildWidgets();if(focus!=null)for(var child:children())if(child instanceof EditBox box&&box.getMessage().getString().equals(focus)){setFocused(box);box.setCursorPosition(Math.min(cursor,box.getValue().length()));break;}
    }catch(Exception e){status="错误："+e.getMessage();}}}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF0B121A);renderBg(g,partial,x,y);}
    @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);g.drawString(font,font.plainSubstrByWidth(UiText.text(status),imageWidth-12),listX,height-39,status.startsWith("错误")?0xFFFCA5A5:0xFF94A3B8,false);
        if(EditorRuntime.busy())g.drawString(font,"正在应用，请稍候…",propX,height-39,0xFFFDE68A,false);
        if(x>=listX&&x<listX+listW&&y>=bodyY&&y<bottom){int i=listOffset+(y-bodyY)/30;if(i<rows.size()&&(rows.get(i).operation()==null||x<listX+listW-22))g.renderTooltip(font,Arrays.stream(rows.get(i).detail.split("\n")).<Component>map(Component::literal).toList(),Optional.empty(),x,y);}
        if(recipe!=null&&!recipe.operation.equals("remove"))for(var slot:slots){var r=visibleSlotBounds(slot);if(r!=null&&r.contains(x,y)){g.renderTooltip(font,List.of(Component.literal(slot.label()+(recipe.simple?" · 简单适配仅替换槽位":" · 点击编辑，右键清空")),Component.literal(ProjectStore.JSON.toJson(slot.get()))),Optional.empty(),x,y);break;}}
    }
    private void accept(RecipeCanvas.Slot slot,Object obj){run(()->{
        EditorRuntime.requireEditable();if(readOnly)throw new IllegalArgumentException("该配方仅可查看或按 ID 删除");JsonObject value=new JsonObject();
        if(obj instanceof ItemStack stack){if(slot.fluid())throw new IllegalArgumentException("该槽需要流体");value.addProperty(slot.output()?"id":"item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if(slot.output())value.addProperty("count",Math.max(1,stack.getCount()));
            var encoded=ItemStack.CODEC.encodeStart(minecraft.level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),stack).getOrThrow().getAsJsonObject();if(encoded.has("components"))value.add("components",encoded.get("components"));
        }else if(obj instanceof FluidStack stack){if(!slot.fluid()&&!recipe.data.get("type").getAsString().matches("create:(mixing|compacting|filling|emptying)"))throw new IllegalArgumentException("该配方槽不接受流体");
            value.addProperty(slot.output()?"id":"fluid",BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());value.addProperty("amount",Math.max(1,stack.getAmount()));if(!slot.output())value.addProperty("type","neoforge:single");
            JsonObject encoded=FluidStack.CODEC.encodeStart(minecraft.level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),stack).getOrThrow().getAsJsonObject();
            if(encoded.has("components")){if(!slot.output())throw new IllegalArgumentException("含数据组件的流体原料尚未适配，不能简化后写入");value.add("components",encoded.get("components"));}
        }else return;
        if(!slot.output()&&!slot.fluid()&&value.has("components"))value=RecipeCanvas.withComponents(value,value.getAsJsonObject("components"),false);
        if(slot.get().isJsonObject()&&slot.get().getAsJsonObject().has("chance"))value.add("chance",slot.get().getAsJsonObject().get("chance"));slot.set(value);RecipeCanvas.normalizeTransition(recipe.data);selectedSlot=slot;changed();propertyOffset=0;rebuildWidgets();
    });}
    public List<GhostTarget> ghostTargets(){return List.copyOf(ghosts);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(EditorRuntime.busy())return true;
        if(tab!=3&&tab!=4&&button==0&&y>=bodyY-24&&y<bottom){if(Math.abs(x-(canvasX-4))<=4){draggingPanel=1;return true;}if(Math.abs(x-(propX-4))<=4){draggingPanel=2;return true;}}
        if(tab!=3&&tab!=4&&recipe!=null&&recipe.data.has("sequence")&&button==0&&x>=canvasLeft()&&x<canvasRight()&&y>=bodyY+18&&y<bodyY+28){draggingSequence=true;scrollSequenceTo(x);return true;}
        // AbstractContainerScreen consumes empty-area clicks and can send inventory packets.
        // Dispatch native widgets directly, then handle our virtual rows and slots.
        for(var child:children())if(child.mouseClicked(x,y,button)){setFocused(child);if(button==0)setDragging(true);return true;}
        setFocused(null);
        if(x>=listX&&x<listX+listW&&y>=bodyY&&y<bottom){int index=listOffset+(int)(y-bodyY)/30;if(index<rows.size())run(rows.get(index).select::run);return true;}
        if(tab==3||tab==4)return false;
        if(recipe!=null&&!readOnly)for(var slot:slots){var b=visibleSlotBounds(slot);if(b!=null&&b.contains((int)x,(int)y)){
            selectedSlot=slot;selectedStep=slot.label().startsWith("步骤 ")?Integer.parseInt(slot.label().substring(3))-1:-1;if(button==1){if(recipe.simple)status="简单适配保留现有槽位，请替换物品";else {slot.set(JsonNull.INSTANCE);changed();}}propertyOffset=0;rebuildWidgets();return true;
        }}
        if(recipe!=null&&!readOnly&&recipe.data.has("sequence")&&x>=canvasLeft()&&x<canvasRight()&&y>=canvasTop()&&y<canvasBottom()){
            double lx=canvasFit.localX(x),ly=canvasFit.localY(y);var steps=recipe.data.getAsJsonArray("sequence");for(int i=0;i<steps.size();i++){int sx=SequenceLayout.cardX(recipe.data,i),sy=SequenceLayout.cardY(recipe.data,i);if(lx>=sx&&lx<sx+SequenceLayout.CARD_WIDTH&&ly>=sy&&ly<sy+SequenceLayout.CARD_HEIGHT){selectedStep=i;selectedSlot=slots.stream().filter(v->v.label().equals("步骤 "+(selectedStep+1))).findFirst().orElse(null);propertyOffset=0;rebuildWidgets();return true;}}
        }return false;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(draggingPanel!=0){if(draggingPanel==1)panels.left=Math.max(.1,Math.min(.4,(x-listX-4)/imageWidth));else panels.right=Math.max(.1,Math.min(.4,(leftPos+imageWidth-x-10)/imageWidth));rebuildWidgets();return true;}if(draggingSequence){scrollSequenceTo(x);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(draggingPanel!=0){draggingPanel=0;run(()->panels.save(EditorRuntime.root()));return true;}if(draggingSequence){draggingSequence=false;return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(x<leftPos||x>=leftPos+imageWidth)return false;
        if(x>=propX&&tab!=3){propertyOffset=Math.max(0,propertyOffset-(int)dy);rebuildWidgets();return true;}
        if(x<canvasX){listOffset=Math.max(0,Math.min(Math.max(0,rows.size()-visibleRows()),listOffset-(int)dy*3));rebuildWidgets();return true;}
        if(hasShiftDown()||dx!=0||tab!=3&&recipe!=null&&recipe.data.has("sequence")&&y<bodyY+30)canvasScrollX=Math.max(0,Math.min(tab!=3&&recipe!=null&&recipe.data.has("sequence")?sequenceScrollMax:Integer.MAX_VALUE,canvasScrollX-(int)(dx!=0?dx:dy)*40));else canvasScroll=Math.max(0,canvasScroll-(int)dy*24);return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(EditorRuntime.busy())return true;if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}if(getFocused() instanceof EditBox box)return box.keyPressed(key,scan,modifiers);return super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){try{if(!EditorRuntime.locked()){stage();EditorRuntime.saveDraft();}minecraft.setScreen(returnTo);}catch(Exception e){status="错误："+e.getMessage();}}
    @Override public boolean isPauseScreen(){return false;}
}
