package dev.kubejseditor;

import com.google.gson.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.script.*;
import dev.kubejseditor.storage.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Destination isolation and restart transactions are tested without a mod-specific machine. */
public final class RegistryDataVerification {
    private static int checks;
    private static void check(boolean condition,String note){checks++;if(!condition)throw new AssertionError(note);}
    private static void fails(Runnable call,String note){checks++;try{call.run();}catch(RuntimeException expected){return;}throw new AssertionError(note);}
    public static int run()throws Exception{
        var target=new RegistryEntryTarget("example:processing/program","pack:part/recipe");target.syntheticType=true;target.codecClass="example.Program";target.codecField="DIRECT_CODEC";
        check(RegistryEntryTarget.parse(target.id()).registry.equals(target.registry)&&RegistryEntryTarget.parse(target.id()).entry.equals(target.entry),"Registry identity round-trips namespaces and nested paths");
        check(!target.id().equals(target.entry),"Registry entry cannot alias an ordinary recipe ID");
        check(!target.id().equals(new RegistryEntryTarget("example:processing","program:pack/part/recipe").id()),"Registry and entry separators cannot collide");
        check(target.path().equals(RegistryEntryTarget.DATA+"pack/example/processing/program/part/recipe.json"),"Native custom registry namespace determines the physical data path");
        fails(()->new RegistryEntryTarget("example:../program","pack:recipe"),"Registry path traversal rejected");fails(()->new RegistryEntryTarget("example:program","pack:../recipe"),"Entry path traversal rejected");fails(()->RegistryEntryTarget.parse("kubejs_editor_data:not_hex/pack/recipe"),"Malformed virtual source key rejected");
        var data=RecipeAdapters.parse("{type:'example:processing/program',input:{item:'minecraft:iron_ingot',count:20480},result:{id:'minecraft:gold_ingot',components:{'minecraft:custom_data':{keep:true}}},unknown:{nested:[1,2,3]},'neoforge:conditions':[{type:'neoforge:false'}]}");
        var draft=new EditorProject.RecipeDraft(target.id(),data);draft.registryTarget=target;draft.operation="replace";draft.origin="existing";draft.source=data.deepCopy();draft.forceNative=true;draft.loadConditionPolicy=LoadConditions.IGNORE;
        var project=new EditorProject();project.recipes.put(draft.id,draft);project.validateStructure();
        var output=KjsGenerator.generate(project);var nativeData=JsonParser.parseString(new String(output.files().get(target.path()),StandardCharsets.UTF_8)).getAsJsonObject();
        check(output.restartRequired(),"Registry edits declare restart requirements");
        check(!output.files().containsKey(WorkspaceState.GENERATED.getFirst()),"Registry-only edits never generate fake event.custom recipes");
        check(!nativeData.has("type")&&!nativeData.has(LoadConditions.KEY),"Synthetic editor type and ignored loading predicates do not enter native output");
        check(nativeData.getAsJsonObject("unknown").equals(data.getAsJsonObject("unknown"))&&nativeData.getAsJsonObject("result").equals(data.getAsJsonObject("result")),"Unknown data and full components survive generation");
        check(nativeData.getAsJsonObject("input").get("count").getAsInt()==20480,"Independent large quantities remain exact");
        check(data.has("type")&&data.has(LoadConditions.KEY),"Generating native data never mutates the source document");
        var restored=WorkspaceState.copy(project,EditorProject.class);check(restored.recipes.get(draft.id).registryTarget.codecClass.equals(target.codecClass)&&restored.recipes.get(draft.id).source.equals(data),"Schema-2 persistence retains native codec and original data metadata");
        var clone=draft.copy("another:copied");check(clone.registryTarget.entry.equals("another:copied")&&!clone.id.equals(draft.id)&&target.entry.equals("pack:part/recipe"),"Copying creates a new destination without mutating the original");
        draft.loadConditionPolicy=LoadConditions.PRESERVE;check(draft.registryTarget.generated(draft).has(LoadConditions.KEY),"Preserve policy keeps native registry loading conditions");
        draft.operation="remove";check(draft.registryTarget.generated(draft).getAsJsonArray(LoadConditions.KEY).get(0).getAsJsonObject().get("type").getAsString().equals("neoforge:false"),"Disabling uses the native conditional registry loader");
        draft.operation="replace";draft.loadConditionPolicy=LoadConditions.IGNORE;target.syntheticType=false;check(target.generated(draft).get("type").equals(data.get("type")),"Actual native dispatch type is preserved");target.syntheticType=true;
        var normal=new EditorProject.RecipeDraft("example:normal",RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'minecraft:iron_ingot'}],result:{id:'minecraft:gold_ingot'}}"));normal.forceNative=true;project.recipes.put(normal.id,normal);
        var mixed=KjsGenerator.generate(project);var script=new String(mixed.files().get(WorkspaceState.GENERATED.getFirst()),StandardCharsets.UTF_8);check(script.contains(normal.id)&&!script.contains(target.id())&&!script.contains(target.registry),"Mixed generation keeps native records out of ordinary KJS recipe callbacks");
        check(CodePreview.pages(project,new EditorProject()).stream().filter(p->p.key().equals("recipe:"+draft.id)).findFirst().orElseThrow().content().contains(target.path()),"Code preview exposes the actual native JSON destination");
        var state=new WorkspaceState();state.applied=WorkspaceState.copy(project,EditorProject.class);state.frozen.put(WorkspaceState.GENERATED.getFirst(),new LinkedHashSet<>(project.recipes.keySet()));check(state.canRevokeRecipe(draft.id)&&!state.canRevokeRecipe(normal.id),"Script ownership freezing never traps independent native data entries");
        var desired=state.withoutRules(Set.of(draft.id),Set.of());check(!desired.recipes.containsKey(draft.id)&&desired.recipes.containsKey(normal.id),"Whole-batch revocation removes only selected native ownership");
        var root=Files.createTempDirectory("kubejs-editor-registry-");var files=new LinkedHashMap<String,byte[]>();files.put(target.path(),mixed.files().get(target.path()));var review=FileTransaction.review(root,files,true,false);var backup=FileTransaction.apply(review);check(Files.exists(root.resolve(target.path())),"Native overlay writes through the existing transaction boundary");FileTransaction.rollback(root,backup);check(!Files.exists(root.resolve(target.path())),"Rollback removes a newly created native overlay exactly");
        var mismatch=WorkspaceState.copy(project,EditorProject.class);mismatch.recipes.get(draft.id).registryTarget.entry="example:wrong";fails(mismatch::validateStructure,"Persisted target/key mismatch cannot redirect data writes");
        return checks;
    }
    private RegistryDataVerification(){}
}
