package dev.kubejseditor;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.EditorDiagnostics;
import dev.kubejseditor.script.KjsGenerator;
import dev.kubejseditor.storage.*;
import dev.latvian.mods.rhino.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Executable regression scenarios; intentionally independent of a running world. */
public final class EditorVerification {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private static void fails(Throwing call,String message)throws Exception{checks++;try{call.run();}catch(Exception expected){return;}throw new AssertionError(message);}
    @FunctionalInterface interface Throwing{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        EditorProject project=fixtures();
        var output=KjsGenerator.generate(project);
        for(var e:output.files().entrySet())if(e.getKey().endsWith(".js")){
            new Parser(new ContextFactory().enter()).parse(new String(e.getValue(),StandardCharsets.UTF_8),e.getKey(),1);checks++;
        }
        String recipes=new String(output.files().get("kubejs/server_scripts/kubejs_editor/recipes.js"),StandardCharsets.UTF_8);
        check(recipes.contains(".id(\"kubejs_editor:test_"),"Generated recipes must have stable explicit IDs");
        check(Arrays.equals(output.files().get("kubejs/server_scripts/kubejs_editor/recipes.js"),KjsGenerator.generate(project).files().get("kubejs/server_scripts/kubejs_editor/recipes.js")),"Generation is deterministic");
        check(project.recipes.size()==RecipeAdapters.all().size()+2,"Every supported serializer has a fixture");
        var seq=RecipeAdapters.get("create:sequenced_assembly").newRecipe();
        check(RecipeAdapters.validateData(seq).isEmpty(),"Assembly weights can exceed one");
        var crush=RecipeAdapters.get("create:crushing").newRecipe();crush.getAsJsonArray("results").get(0).getAsJsonObject().addProperty("chance",120);
        check(!RecipeAdapters.validateData(crush).isEmpty(),"Processing probability must not accept assembly weights");
        var before=RecipeAdapters.parse("{type:'create:mixing',ingredients:[{tag:'c:ingots/iron'}],results:[{id:'minecraft:gold_ingot',count:2}],meta:{item:'minecraft:iron_ingot'}}");
        var after=RecipeTools.replace(before,"#c:ingots/iron","minecraft:copper_ingot",false);
        check(after.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:copper_ingot"),"Tag can become a concrete ingredient");
        check(before.getAsJsonArray("ingredients").get(0).getAsJsonObject().has("tag"),"Bulk changes must not mutate loaded source objects");
        check(after.getAsJsonObject("meta").get("item").getAsString().equals("minecraft:iron_ingot"),"Bulk replace must not rewrite metadata");
        check(RecipeTools.contains(before,"#c:ingots/iron",false)&&!RecipeTools.contains(before,"#c:ingots/iron",true),"Input/output filters remain separate");
        var fluid=RecipeTools.replace(RecipeAdapters.parse("{type:'create:mixing',ingredients:[{type:'neoforge:tag',tag:'c:milk',amount:250}],results:[{id:'create:tea',amount:500}]}"),"#c:milk","minecraft:water",false).getAsJsonArray("ingredients").get(0).getAsJsonObject();
        check(fluid.get("type").getAsString().equals("neoforge:single")&&fluid.get("fluid").getAsString().equals("minecraft:water")&&fluid.get("amount").getAsInt()==250,"Bulk fluid replacement preserves amount and changes ingredient codec");
        var shape=RecipeAdapters.get("minecraft:crafting_shaped").newRecipe();RecipeCanvas.slots(shape).getFirst().set(RecipeAdapters.parse("{item:'minecraft:iron_ingot'}"));
        check(RecipeAdapters.validateData(shape).isEmpty(),"Placing into a shaped slot creates matching pattern/key data");
        RecipeCanvas.slots(shape).getFirst().set(JsonNull.INSTANCE);check(!RecipeAdapters.validateData(shape).isEmpty(),"An empty grid must not export");
        var cooking=RecipeAdapters.get("minecraft:smelting").newRecipe();RecipeCanvas.slots(cooking).getFirst().set(JsonNull.INSTANCE);
        check(!RecipeAdapters.validateData(cooking).isEmpty(),"An empty required ingredient blocks generation");
        check(RecipeCanvas.slots(cooking).size()==2,"Clearing a required field keeps a refillable virtual slot");
        RecipeCanvas.slots(cooking).getFirst().set(RecipeAdapters.parse("{item:'minecraft:iron_ore'}"));check(RecipeAdapters.validateData(cooking).isEmpty(),"Required slots can be refilled after clearing");
        var components=RecipeCanvas.withComponents(RecipeAdapters.parse("{item:'minecraft:paper'}"),RecipeAdapters.parse("{'minecraft:custom_data':{sample:1}}"),false);
        check(components.get("type").getAsString().equals("neoforge:components")&&components.get("items").getAsString().equals("minecraft:paper"),"Component-sensitive ingredients use the NeoForge codec");
        var plain=RecipeCanvas.withComponents(components,new JsonObject(),false);check(plain.has("item")&&!plain.has("type"),"Removing ingredient components restores plain ingredient");
        fails(()->EditorProject.requireId("kubejs:../../escape"),"Registry IDs cannot escape resource directories");
        var sh=RecipeAdapters.get("minecraft:crafting_shapeless").newRecipe();sh.getAsJsonArray("ingredients").add(RecipeAdapters.parse("{item:'minecraft:iron_ingot'}"));RecipeCanvas.slots(sh).getFirst().set(JsonNull.INSTANCE);check(sh.getAsJsonArray("ingredients").isEmpty(),"Clearing an array slot removes it rather than leaving null");
        var item=new EditorProject.ItemDraft();item.id="kubejs:invalid";item.properties.addProperty("maxStackSize",16);item.properties.addProperty("maxDamage",32);
        fails(()->KjsGenerator.validateItem(item),"Durable stacks are invalid");
        Path root=Files.createTempDirectory("kubejs-editor-verification-");
        Files.createDirectories(root.resolve("kubejs/server_scripts"));Files.writeString(root.resolve("kubejs/server_scripts/manual.js"),"// user script");
        var review=FileTransaction.review(root,output.files(),true,true);Path backup=FileTransaction.apply(review);
        check(Files.readString(root.resolve("kubejs/server_scripts/manual.js")).equals("// user script"),"Manual scripts remain untouched");
        check(ProjectStore.read(root).recipes.size()==project.recipes.size(),"Authoring data round trips");
        check(FileTransaction.review(root,output.files(),true,true).changes().isEmpty(),"Repeated apply must be idempotent");
        FileTransaction.rollback(root,backup);check(!Files.exists(root.resolve(ProjectStore.FILE)),"Rollback removes newly created files");
        Path file=root.resolve("a.txt");Files.writeString(file,"before");var rev=FileTransaction.review(root,Map.of("a.txt","after".getBytes(StandardCharsets.UTF_8)),false,false);Files.writeString(file,"external");
        fails(()->FileTransaction.apply(rev),"An edit after preview must block overwrite");check(Files.readString(file).equals("external"),"External edit survives rejected transaction");
        fails(()->FileTransaction.review(root,Map.of("../escape.txt",new byte[]{1}),false,false),"Traversal outside instance must be rejected");
        Files.writeString(file,"original");Files.writeString(root.resolve("blocked"),"file instead of directory");
        Map<String,byte[]> failing=new LinkedHashMap<>();failing.put("a.txt","new".getBytes(StandardCharsets.UTF_8));failing.put("blocked/child.txt",new byte[]{1});
        var failure=FileTransaction.review(root,failing,false,false);fails(()->FileTransaction.apply(failure),"Failed file write must fail the transaction");check(Files.readString(file).equals("original"),"Write failure restores files already changed");
        var rollbackReview=FileTransaction.review(root,Map.of("a.txt","applied".getBytes(StandardCharsets.UTF_8)),false,false);Path rollbackBackup=FileTransaction.apply(rollbackReview);Files.writeString(file,"later manual edit");
        fails(()->FileTransaction.rollback(root,rollbackBackup),"Rollback must preserve manual edits made after apply");check(Files.readString(file).equals("later manual edit"),"Rollback rejection does not overwrite manual edits");
        Files.writeString(file,"applied");FileTransaction.rollback(root,rollbackBackup);check(Files.readString(file).equals("original"),"Rollback works after conflict is resolved");
        Path fixtures=Path.of("build/verification");for(var e:output.files().entrySet()){Path dest=fixtures.resolve(e.getKey());Files.createDirectories(dest.getParent());Files.write(dest,e.getValue());}
        Files.writeString(fixtures.resolve("expected-recipe-ids.json"),ProjectStore.JSON.toJson(project.recipes.keySet()));
        verifyWorkspace(root);
        verifyOperations(root);
        verifyDiagnostics(root);
        verifyCanvas();
        verifyRegistrationKinds();
        verifyItemRemovalAndInspection(root);
        verifyFanAndSizing();
        checks+=TemplateVerification.run();checks+=GenericPatchVerification.run();
        checks+=VintageVerification.run();
        checks+=BlockVerification.run();checks+=ContentVerification.run();checks+=SimpleRecipeVerification.run();checks+=CompatibilityVerification.run();checks+=LoadConditionVerification.run();checks+=BatchAndCacheVerification.run();checks+=RegistryDataVerification.run();checks+=MappingPreferencesVerification.run();
        System.out.println("PASS: "+checks+" authoring, templates, workspace migration, activation, ownership, text editing and rollback checks; "+RecipeAdapters.all().size()+" serializer fixtures.");
    }
    private static void verifyItemRemovalAndInspection(Path root)throws Exception{
        var state=new WorkspaceState();var first=new EditorProject.ItemDraft();first.id="kubejs:delete_me";first.texture="kubejs_editor:item/shared";
        var keep=new EditorProject.ItemDraft();keep.id="kubejs:keep_me";keep.texture=first.texture;
        state.applied.items.put(first.id,first);state.applied.items.put(keep.id,keep);
        String asset="kubejs/assets/kubejs_editor/textures/item/shared.png";state.applied.assets.put(asset,"staged.png");
        var prop=new EditorProject.ItemDraft();prop.id="minecraft:iron_ingot";prop.register=false;state.applied.items.put(prop.id,prop);
        check(state.canDeleteRegisteredItem(first.id)&&!state.canDeleteRegisteredItem(prop.id),"Only editor-owned registrations can be deleted, never native items");
        var desired=state.withoutRegisteredItem(first.id);
        check(!desired.items.containsKey(first.id)&&desired.items.containsKey(keep.id)&&desired.assets.containsKey(asset),"Removing one registration preserves other registrations and shared textures");
        check(state.applied.items.containsKey(first.id),"Preparing removal cannot mutate active state");
        state.applied.items.remove(keep.id);desired=state.withoutRegisteredItem(first.id);
        check(desired.assets.isEmpty(),"An unreferenced owned texture is removed with its registration");
        state.frozen.put(WorkspaceState.GENERATED.get(2),new HashSet<>(Set.of(first.id)));
        fails(()->state.withoutRegisteredItem(first.id),"Hand-written ownership blocks removal even if only the tooltip script is frozen");state.frozen.clear();
        var pending=new EditorProject();var unrelated=new EditorProject.RecipeDraft("test:keep",RecipeAdapters.get("minecraft:smelting").newRecipe());pending.recipes.put(unrelated.id,unrelated);
        var activation=new WorkspaceState.Activation();activation.desired=state.withoutRegisteredItem(first.id);activation.submitted=new EditorProject();activation.deletedItems.add(first.id);activation.restart=true;state.activation=activation;
        var restored=WorkspaceState.copy(state,WorkspaceState.class);restored.promote(pending);
        check(!restored.applied.items.containsKey(first.id)&&pending.recipes.containsKey(unrelated.id)&&restored.activation==null,"Successful removal leaves no replacement record and preserves unrelated pending work");
        var nested=new EditorProject.RecipeDraft("test:nested",RecipeAdapters.parse("{type:'create:sequenced_assembly',sequence:[{ingredients:[{item:'kubejs:delete_me'}]}]}"));pending.recipes.put(nested.id,nested);
        check(dev.kubejseditor.model.ItemReferences.recipes(pending,first.id).equals(List.of(nested.id)),"Nested assembly references are reported by exact ID");
        nested.operation="remove";check(dev.kubejseditor.model.ItemReferences.recipes(pending,first.id).isEmpty(),"Disabled recipes do not prevent registration removal");
        check(!dev.kubejseditor.model.ItemReferences.contains(new JsonPrimitive("kubejs:delete_me_extra"),first.id),"Similar IDs are not dependencies");
        var broken=new EditorProject.RecipeDraft("test:broken",RecipeAdapters.get("create:cutting").newRecipe());while(broken.data.getAsJsonArray("results").size()<6)broken.data.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:gold_ingot'}"));pending.recipes.put(broken.id,broken);
        var pages=dev.kubejseditor.script.CodePreview.pages(pending,pending);
        check(pages.stream().anyMatch(p->p.key().equals("recipe:test:broken")&&p.content().contains("原始数据")&&p.content().contains("gold_ingot")),"Invalid cutting data remains inspectable without truncating outputs");
        check(pages.stream().anyMatch(p->p.key().equals("recipe:test:keep")&&p.content().contains("event.custom")),"One invalid record cannot hide another record's generated code");
        ProjectStore.saveDraft(root,pending);check(ProjectStore.read(root).recipes.get(broken.id).data.getAsJsonArray("results").size()==6,"Inspection never rewrites invalid source drafts");
    }
    private static void verifyFanAndSizing()throws Exception{
        check(FanProcessing.all().size()==5&&!FanProcessing.supports("create_dragons_plus:coloring"),"Fan modes exclude coloring as requested");
        var data=RecipeAdapters.get("create:splashing").newRecipe();data.getAsJsonArray("results").get(0).getAsJsonObject().addProperty("chance",0.43);var original=data.deepCopy();
        for(var mode:FanProcessing.all()){
            FanProcessing.switchMode(data,mode.type());check(data.getAsJsonArray("ingredients").equals(original.get("ingredients"))&&data.getAsJsonArray("results").equals(original.get("results")),"Fan switching preserves ingredients and probability: "+mode.name());
            check(RecipeAdapters.validateData(data).isEmpty(),"Fan adapter validation: "+mode.name());
            while(data.getAsJsonArray("results").size()<12)data.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:iron_nugget'}"));
            check(!RecipeLimits.forType(mode.type()).canAdd(data,true,false)&&RecipeCanvas.slots(data).size()==13,"Fan has one input and twelve independent outputs");
            data.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:iron_nugget'}"));check(!RecipeAdapters.validateData(data).isEmpty()&&data.getAsJsonArray("results").size()==13,"Excess fan outputs are reported without dropping data");data=original.deepCopy();
        }
        var sequence=RecipeAdapters.get("create:sequenced_assembly").newRecipe();for(int i=0;i<19;i++)RecipeCanvas.addStep(sequence,"create:pressing");
        check(SequenceLayout.columns(sequence)==20&&SequenceLayout.rows(sequence)==1&&SequenceLayout.cardY(sequence,19)==0,"Long sequences remain one horizontal row");
        check(SequenceLayout.cardX(sequence,19)>2500&&SequenceLayout.summaryY(sequence)<200,"Sequence length does not increase card height or move summary down");
        var fit=CanvasFit.centered(10,20,400,200,140,80,2);check(fit.scale()==2&&fit.x()==70&&fit.y()==40,"Simple recipes enlarge and center in available canvas");
        fit=CanvasFit.centered(10,20,200,110,300,220,2);check(fit.scale()==.5&&fit.x()==35,"Large grids fit the available area without clipping");
    }
    private static void verifyRegistrationKinds()throws Exception{
        for(String kind:dev.kubejseditor.model.ItemKinds.NAMES.keySet()){
            var item=new EditorProject.ItemDraft();item.id="kubejs:kind_"+kind;dev.kubejseditor.model.ItemKinds.initialize(item,kind);var p=new EditorProject();p.items.put(item.id,item);var out=KjsGenerator.generate(p);String js=new String(out.files().get("kubejs/startup_scripts/kubejs_editor/items.js"),StandardCharsets.UTF_8);new Parser(new ContextFactory().enter()).parse(js,"kind_"+kind,1);
            check(js.contains(kind.equals("block")?"registry('block'":"registry('item'"),"Correct registration event for "+kind);
            if(dev.kubejseditor.model.ItemKinds.tool(kind))check(js.contains(".modifyTier(tier => tier.setUses("),"Tool durability also configures its native tier: "+kind);
            if(kind.equals("block"))check(js.contains(".texture(")&&js.contains(".item(item =>"),"Block registers its placeable item and complete model texture");
            if(dev.kubejseditor.model.ItemKinds.armor(kind))check(js.contains("armor_toughness")&&js.contains("knockback_resistance"),"Armor category emits equipment attributes");
        }
        var bad=new EditorProject.ItemDraft();dev.kubejseditor.model.ItemKinds.initialize(bad,"block");bad.settings.addProperty("lightLevel",16);fails(()->KjsGenerator.validateItem(bad),"Block brightness above fifteen is rejected");
        var tool=new EditorProject.ItemDraft();dev.kubejseditor.model.ItemKinds.initialize(tool,"pickaxe");tool.properties.addProperty("maxDamage",0);fails(()->KjsGenerator.validateItem(tool),"Zero tool durability is rejected before registration");
        var view=CanvasFit.fit(100,50,120,90,300,216);check(view.px(300)<=220&&view.py(216)<=140&&view.scale()<1,"Auto-fit includes complete 9x9 grid in a small canvas");check(Math.abs(view.localX(view.px(120))-120)<3,"Scaled mouse coordinates map back to the same slot");
        var seq=RecipeAdapters.get("create:sequenced_assembly").newRecipe();RecipeCanvas.addStep(seq,"create:pressing");RecipeCanvas.addStep(seq,"create:cutting");RecipeCanvas.addStep(seq,"create:filling");
        var slots=RecipeCanvas.slots(seq);check(slots.stream().filter(s->s.label().startsWith("步骤中间件")).count()==8,"Every assembly step has before/after intermediate slots");check(slots.stream().anyMatch(s->s.label().equals("步骤 4")&&s.fluid()),"Assembly filling always has a refillable fluid slot");
    }
    private static void verifyCanvas()throws Exception{
        var d=new EditorProject.RecipeDraft("test:grid",RecipeAdapters.parse("{type:'minecraft:crafting_shaped',pattern:['A'],key:{A:{tag:'minecraft:planks'}},result:{id:'minecraft:oak_button'}}"));
        check(RecipeCanvas.slots(d).size()==10&&d.grid.size==3,"Imported 1x1 crafting exposes nine inputs and one output");
        var slots=RecipeCanvas.slots(d);slots.get(0).set(JsonNull.INSTANCE);slots.get(4).set(RecipeAdapters.parse("{tag:'minecraft:planks'}"));slots.get(8).set(RecipeCanvas.withComponents(RecipeAdapters.parse("{item:'minecraft:paper'}"),RecipeAdapters.parse("{'minecraft:custom_data':{probe:1}}"),false));
        var cells=d.grid.cells.deepCopy();CraftingGrid.setOrdered(d,false);
        check(d.grid.cells.equals(cells)&&d.data.getAsJsonArray("ingredients").size()==2&&!d.data.has("pattern"),"Shapeless conversion keeps UI positions and exact component/tag ingredients");
        CraftingGrid.setOrdered(d,true);check(d.grid.cells.equals(cells)&&d.data.getAsJsonArray("pattern").toString().equals("[\"A \",\" B\"]"),"Ordered conversion restores spacing and trims only the outer border");
        var copy=ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(d),EditorProject.RecipeDraft.class);check(copy.grid.cells.equals(cells)&&RecipeCanvas.slots(copy).size()==10,"Authoring positions survive draft persistence");
        var m=new EditorProject.RecipeDraft("test:mechanical",RecipeAdapters.get("create:mechanical_crafting").newRecipe());CraftingGrid.resize(m,9);var ms=RecipeCanvas.slots(m);
        check(ms.size()==82,"Mechanical crafting exposes all 81 cells and output");
        for(int i:new int[]{10,12,28,30})ms.get(i).set(RecipeAdapters.parse("{item:'minecraft:iron_ingot'}"));
        check(CraftingGrid.generated(m).getAsJsonArray("pattern").toString().equals("[\"A A\",\"   \",\"A A\"]"),"Mechanical crop preserves empty internal rows and columns");
        ms.get(80).set(RecipeAdapters.parse("{item:'minecraft:gold_ingot'}"));fails(()->CraftingGrid.resize(m,8),"Shrinking occupied edge is rejected without deleting materials");check(!m.grid.cells.get(80).isJsonNull()&&m.grid.size==9,"Rejected shrink keeps every original cell");
        fails(()->CraftingGrid.resize(m,10),"Mechanical grid cannot exceed nine");ms.get(80).set(JsonNull.INSTANCE);CraftingGrid.resize(m,4);check(m.grid.size==4&&CraftingGrid.generated(m).getAsJsonArray("pattern").size()==3,"Safe resizing preserves cropped pattern");
        var deploy=RecipeAdapters.get("create:deploying").newRecipe();var ds=RecipeCanvas.slots(deploy);check(ds.get(0).y()>ds.get(1).y()&&ds.get(1).label().contains("机械手"),"Deployer held input is above depot input");ds.get(0).set(JsonNull.INSTANCE);check(RecipeCanvas.slots(deploy).size()==3&&deploy.getAsJsonArray("ingredients").size()==2,"Clearing depot preserves the held-item role and slot");
        var fill=RecipeCanvas.slots(RecipeAdapters.get("create:filling").newRecipe());check(fill.get(1).fluid()&&!fill.get(0).fluid(),"Spout has separate fluid and item roles");
        check(dev.kubejseditor.config.ConfigNames.words("maxRotationSpeed").equals("最大转速"),"Common settings have plain Chinese names");
    }
    private static void verifyOperations(Path root)throws Exception{
        var cutting=RecipeAdapters.get("create:cutting").newRecipe();var outputs=cutting.getAsJsonArray("results");
        while(outputs.size()<6)outputs.add(RecipeAdapters.parse("{id:'minecraft:iron_nugget',count:1}"));
        check(RecipeAdapters.validateData(cutting).stream().anyMatch(e->e.contains("6")&&e.contains("4")),"Six cutting outputs report the specific limit");
        check(outputs.size()==6,"Validation preserves the complete invalid draft");
        while(outputs.size()>4)outputs.remove(outputs.size()-1);
        check(RecipeAdapters.validateData(cutting).isEmpty(),"Removing excess outputs repairs the cutting draft");
        check(!RecipeLimits.forType("create:cutting").canAdd(cutting,true,false),"Full cutting output slots disable addition");
        outputs.get(0).getAsJsonObject().addProperty("count",64);
        check(RecipeAdapters.validateData(cutting).isEmpty(),"Stack size does not consume additional output entries");
        var basin=RecipeAdapters.get("create:mixing").newRecipe();while(basin.getAsJsonArray("results").size()<4)basin.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:iron_nugget'}"));
        basin.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:water',amount:250}"));
        check(RecipeAdapters.validateData(basin).isEmpty(),"Item and fluid output limits are independent");
        var p=new EditorProject();var d=new EditorProject.RecipeDraft("test:cancel",cutting);p.recipes.put(d.id,d);
        var other=new EditorProject.RecipeDraft("test:keep",cutting);p.recipes.put(other.id,other);
        var op=dev.kubejseditor.model.PendingOperation.list(p).getFirst();op.cancel(p);
        check(!p.recipes.containsKey(d.id)&&p.recipes.containsKey(other.id),"Cancel removes only the selected operation");
        other.operation="remove";check(dev.kubejseditor.model.PendingOperation.list(p).getFirst().title().equals("配方禁用"),"Disabling remains visible in the operation queue");
        ProjectStore.saveDraft(root,p);check(ProjectStore.read(root).recipes.containsKey(other.id)&&!ProjectStore.read(root).recipes.containsKey(d.id),"Unapplied operations survive restart without resurrecting cancelled records");
        var item=new EditorProject.ItemDraft();item.id="kubejs:cancel_registration";p.items.put(item.id,item);new dev.kubejseditor.model.PendingOperation("item",item.id,"物品注册").cancel(p);
        check(p.items.isEmpty()&&p.recipes.containsKey(other.id),"Cancelling registration does not cascade into recipes");
    }
    private static void verifyWorkspace(Path root)throws Exception{
        var empty=KjsGenerator.generate(new EditorProject());check(empty.files().keySet().stream().noneMatch(p->p.endsWith(".js")||p.contains("/assets/")),"Empty projects do not emit startup scripts or resource reloads");
        var s=new dev.kubejseditor.storage.WorkspaceState();var pending=new EditorProject();
        var old=new EditorProject.RecipeDraft("test:old",RecipeAdapters.get("create:pressing").newRecipe());s.applied.recipes.put(old.id,old);
        var fresh=new EditorProject.RecipeDraft("test:new",RecipeAdapters.get("create:milling").newRecipe());pending.recipes.put(fresh.id,fresh);
        var merged=s.merge(pending);check(merged.recipes.size()==2&&s.applied.recipes.size()==1&&pending.recipes.size()==1,"Merging pending rules preserves active state and drafts");
        var a=new dev.kubejseditor.storage.WorkspaceState.Activation();a.desired=merged;a.submitted=dev.kubejseditor.storage.WorkspaceState.copy(pending,EditorProject.class);s.activation=a;
        s.promote(pending);check(pending.recipes.isEmpty()&&s.applied.recipes.size()==2&&s.activation==null,"Successful activation clears submitted drafts and retains active rules");
        pending.recipes.put(fresh.id,fresh);a=new dev.kubejseditor.storage.WorkspaceState.Activation();a.desired=s.merge(pending);a.submitted=dev.kubejseditor.storage.WorkspaceState.copy(pending,EditorProject.class);s.activation=a;fresh.operation="remove";s.promote(pending);
        check(pending.recipes.get(fresh.id).operation.equals("remove"),"Activation does not discard drafts edited since submission");pending.recipes.clear();
        String base=dev.kubejseditor.storage.WorkspaceState.GENERATED.getFirst();Path generated=root.resolve(base);Files.createDirectories(generated.getParent());Files.writeString(generated,"// manual source kept intact");s.managedHashes.put(base,dev.kubejseditor.storage.WorkspaceState.hash(generated));
        s.takeOver(root,base,pending);check(Files.readString(generated).equals("// manual source kept intact")&&s.manualFiles.contains(base),"Taking ownership does not mutate the source file");
        check(!s.outputPath(base).equals(base)&&s.forScript(s.applied,pending,base).recipes.isEmpty(),"Manual source is excluded from future generated overlays");
        pending.recipes.put(old.id,new EditorProject.RecipeDraft(old.id,old.data));check(s.forScript(s.merge(pending),pending,base).recipes.size()==1,"A later visual change produces only the explicit ID override");
        fails(()->s.takeOver(root,s.outputPath(base),pending),"Ownership changes cannot swallow unapplied visual edits");
        var labels=dev.kubejseditor.client.RecipeNames.numbered(Map.of("b:r","铁锭","a:r","铁锭","c:r","铜锭"));
        check(labels.get("a:r").equals("铁锭_1")&&labels.get("b:r").equals("铁锭_2")&&labels.get("c:r").equals("铜锭"),"Duplicate display names are numbered by ID without changing IDs");
        var buffer=new dev.kubejseditor.script.ScriptBuffer("第一行\nsecond");buffer.move(3,false);buffer.replace("！");check(buffer.text.equals("第一行！\nsecond"),"Text insertion preserves Chinese characters");buffer.undo();check(buffer.text.equals("第一行\nsecond"),"Undo restores text and selection");buffer.redo();check(buffer.text.startsWith("第一行！"),"Redo reapplies the change");
        buffer.anchor=0;buffer.cursor=buffer.text.length();buffer.indent(false);check(buffer.text.startsWith("    ")&&buffer.text.contains("\n    second"),"Multiline indentation applies to every selected line");buffer.indent(true);check(buffer.text.equals("第一行！\nsecond"),"Outdent restores the selected block");buffer.move(0,false);check(buffer.find("second")&&buffer.selection().equals("second"),"Find selects the full match");buffer.replace("third");buffer.undo();check(buffer.selection().equals("second"),"Undo restores selection after replacement");
        Path legacyRoot=Files.createTempDirectory("abyss-legacy-");Path legacy=legacyRoot.resolve(ProjectStore.FILE);Files.createDirectories(legacy.getParent());Files.writeString(legacy,"{schemaVersion:1,recipes:{},items:{},configs:[],assets:{}}");
        check(ProjectStore.read(legacyRoot).schemaVersion==2&&Files.exists(legacy.resolveSibling("project.v1.backup.json")),"Legacy data is backed up and migrated without discarding entries");
        Path metaRoot=Files.createTempDirectory("abyss-metadata-");Files.writeString(metaRoot.resolve("file.txt"),"old");var rev=FileTransaction.review(metaRoot,Map.of("file.txt","new".getBytes(StandardCharsets.UTF_8)),false,false);Path b=FileTransaction.apply(rev);
        FileTransaction.commitMetadata(metaRoot,b,ProjectStore.FILE,"active metadata".getBytes(StandardCharsets.UTF_8));FileTransaction.rollback(metaRoot,b);
        check(Files.readString(metaRoot.resolve("file.txt")).equals("old")&&!Files.exists(metaRoot.resolve(ProjectStore.FILE)),"Post-reload metadata participates in the same rollback transaction");
        fails(()->dev.kubejseditor.runtime.EditorScripts.checked(root,"kubejs/data/worldgen.js"),"Text editing is confined to the three script directories");
        fails(()->dev.kubejseditor.runtime.EditorScripts.checked(root,"kubejs/server_scripts/../../escape.js"),"Script path traversal is rejected");
        Path metadataFolder=metaRoot.resolve("kubejs/config/kubejs_editor");Files.createDirectories(metadataFolder);Files.writeString(metadataFolder.resolve("blocked"),"blocks directory");
        var retry=FileTransaction.review(metaRoot,Map.of("file.txt","again".getBytes(StandardCharsets.UTF_8)),false,false);Path retryBackup=FileTransaction.apply(retry);
        Map<String,byte[]> broken=new LinkedHashMap<>();broken.put(ProjectStore.FILE,"new metadata".getBytes(StandardCharsets.UTF_8));broken.put("kubejs/config/kubejs_editor/blocked/child.json",new byte[]{1});
        fails(()->FileTransaction.commitMetadata(metaRoot,retryBackup,broken),"Metadata batch reports partial write failure");
        check(!Files.exists(metaRoot.resolve(ProjectStore.FILE)),"Failed activation metadata batch restores earlier metadata writes");
        check(new String(KjsGenerator.generate(fixtures()).files().get("kubejs/server_scripts/kubejs_editor/recipes.js"),StandardCharsets.UTF_8).contains("event.addedRecipes.forEach"),"Generated ID overrides also remove earlier KJS additions");
        check(RecipeAdapters.get("minecraft:smithing_trim")==null,"Smithing trim has no editor adapter");
        var revokeState=new WorkspaceState();var changed=new EditorProject.RecipeDraft("test:changed",RecipeAdapters.get("create:cutting").newRecipe());changed.operation="replace";revokeState.applied.recipes.put(changed.id,changed);
        var keep=new EditorProject.RecipeDraft("test:keep",changed.data);revokeState.applied.recipes.put(keep.id,keep);
        var untouched=new EditorProject();var draft=new EditorProject.RecipeDraft(changed.id,changed.data);draft.operation="remove";untouched.recipes.put(draft.id,draft);String beforeDraft=ProjectStore.JSON.toJson(untouched);
        var desired=revokeState.withoutRecipe(changed.id);check(!desired.recipes.containsKey(changed.id)&&desired.recipes.containsKey(keep.id)&&revokeState.applied.recipes.containsKey(changed.id),"Revocation prepares only the requested active-rule removal");
        var activation=new WorkspaceState.Activation();activation.desired=desired;activation.submitted=new EditorProject();activation.revokedRecipes.add(changed.id);revokeState.activation=activation;revokeState.promote(untouched);
        check(!revokeState.applied.recipes.containsKey(changed.id)&&ProjectStore.JSON.toJson(untouched).equals(beforeDraft),"Revoking an active rule preserves even a pending change for the same ID");
        check(!new String(KjsGenerator.generate(desired).files().get(WorkspaceState.GENERATED.getFirst()),StandardCharsets.UTF_8).contains(changed.id),"Revocation emits neither a replacement nor a disable placeholder");
        changed.origin="add";check(changed.actionLabel().equals("新增"),"An edited newly created ID retains its creation label");changed.origin="existing";check(changed.actionLabel().equals("已替换"),"Imported replacement label");changed.operation="remove";check(changed.actionLabel().equals("已禁用"),"Disabled recipe label");
        revokeState.frozen.computeIfAbsent(WorkspaceState.GENERATED.getFirst(),k->new LinkedHashSet<>()).add(keep.id);fails(()->revokeState.withoutRecipe(keep.id),"Revocation cannot silently rewrite handwritten source");
    }
    private static void verifyDiagnostics(Path root)throws Exception{
        var draft=new EditorProject.RecipeDraft("kubejs_editor:source_test",RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'minecraft:diamond'}],result:{id:'minecraft:emerald'}}"));
        draft.source=RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'minecraft:coal'}],result:{id:'minecraft:emerald'}}");
        var copy=draft.copy("kubejs_editor:source_copy");
        check(copy.source!=null&&copy.source.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:coal"),"Recipe source snapshots survive copy");
        check(!copy.source.has("simple")&&!copy.source.has("origin"),"Source snapshots do not acquire authoring metadata");
        Path file=root.resolve(EditorDiagnostics.FILE);
        var failure=new EditorDiagnostics.Diagnostic();failure.operation="revokeRecipe";failure.phase="reload-future";failure.outcome="failure";failure.message="第三方同步监听异常";failure.error=EditorDiagnostics.Error.of(new NullPointerException("Data resource not present: null"));failure.throwable=new NullPointerException("Data resource not present: null");
        EditorDiagnostics.record(root,failure);
        check(Files.exists(file),"Failure diagnostics are persisted");
        var parsed=ProjectStore.JSON.fromJson(Files.readString(file),EditorDiagnostics.Diagnostic.class);
        check("failure".equals(parsed.outcome)&&parsed.error!=null&&parsed.error.type.endsWith("NullPointerException")&&!parsed.error.stack.isEmpty(),"Persisted diagnostics retain the original failure");
        var success=new EditorDiagnostics.Diagnostic();success.operation="reload";success.phase="complete";success.outcome="success";success.message="重载成功";
        EditorDiagnostics.record(root,success);
        parsed=ProjectStore.JSON.fromJson(Files.readString(file),EditorDiagnostics.Diagnostic.class);
        check("failure".equals(parsed.outcome),"A later plain success preserves the last failure diagnostic");
        var compensated=new EditorDiagnostics.Diagnostic();compensated.operation="revokeRecipe";compensated.phase="compensated";compensated.outcome="compensated-success";compensated.message="已补偿客户端同步";compensated.error=EditorDiagnostics.Error.of(new RuntimeException("sync listener"));
        EditorDiagnostics.record(root,compensated);
        parsed=ProjectStore.JSON.fromJson(Files.readString(file),EditorDiagnostics.Diagnostic.class);
        check("compensated-success".equals(parsed.outcome),"Compensated success records the bypass evidence");
        check(EditorDiagnostics.read(root).contains("compensated-success")&&EditorDiagnostics.path(root).endsWith("last-diagnostic.json"),"Diagnostics can be read by the UI");
        Throwable deep=deepFailure();
        check(EditorDiagnostics.Error.of(deep).stack.size()<=40,"Persisted stack traces are bounded");
    }
    private static Throwable deepFailure(){try{deepFailure(0);}catch(Throwable error){return error;}return new IllegalStateException("unreachable");}
    private static void deepFailure(int depth){if(depth>80)throw new IllegalStateException("deep");deepFailure(depth+1);}
    public static EditorProject fixtures(){
        EditorProject project=new EditorProject();int i=0;
        for(var adapter:RecipeAdapters.all()){
            JsonObject data=adapter.newRecipe();
            if(data.has("sequence"))for(String step:List.of("create:pressing","create:cutting","create:filling")){RecipeCanvas.addStep(data,step);if(step.equals("create:filling"))data.getAsJsonArray("sequence").get(data.getAsJsonArray("sequence").size()-1).getAsJsonObject().getAsJsonArray("ingredients").set(1,RecipeAdapters.parse("{type:'neoforge:single',fluid:'minecraft:water',amount:250}"));}
            if(data.has("pattern")){data.add("pattern",JsonParser.parseString("[\"A\"]"));data.add("key",RecipeAdapters.parse("{A:{item:'minecraft:iron_ingot'}}"));}
            if(data.has("ingredients")&&data.getAsJsonArray("ingredients").isEmpty())data.getAsJsonArray("ingredients").add(RecipeAdapters.parse("{item:'minecraft:iron_ingot'}"));
            String id="kubejs_editor:test_"+(i++);project.recipes.put(id,new EditorProject.RecipeDraft(id,data));
        }
        var d=new EditorProject.RecipeDraft("kubejs_editor:test_new_item",RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'minecraft:diamond'}],result:{id:'kubejs:abyss_test_item',count:2}}"));project.recipes.put(d.id,d);
        var removed=new EditorProject.RecipeDraft("minecraft:diamond_block",new JsonObject());removed.operation="remove";project.recipes.put(removed.id,removed);
        var item=new EditorProject.ItemDraft();item.id="kubejs:abyss_test_item";item.name="深渊测试材料";item.properties.addProperty("maxStackSize",16);item.properties.addProperty("burnTime",400);item.tooltip.add("测试引号 ' \" 与换行\n下一行");var a=new EditorProject.AttributeDraft();a.amount=3;item.attributes.add(a);project.items.put(item.id,item);
        var food=new EditorProject.ItemDraft();food.id="kubejs:abyss_test_food";food.name="深渊测试食物";food.food=true;food.properties.addProperty("nutrition",6);food.properties.addProperty("saturation",0.5);project.items.put(food.id,food);
        var existing=new EditorProject.ItemDraft();existing.id="minecraft:wooden_sword";existing.register=false;existing.name="测试木剑";existing.texture="";existing.properties.addProperty("maxDamage",123);existing.properties.addProperty("maxStackSize",1);project.items.put(existing.id,existing);
        var existingFood=new EditorProject.ItemDraft();existingFood.id="minecraft:apple";existingFood.register=false;existingFood.name="测试苹果";existingFood.texture="";existingFood.food=true;existingFood.properties.addProperty("nutrition",9);existingFood.properties.addProperty("saturation",0.6);project.items.put(existingFood.id,existingFood);
        return project;
    }
}
