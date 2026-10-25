package dev.kubejseditor;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.model.EditorProject.RecipeDraft;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.script.KjsGenerator;
import dev.kubejseditor.storage.*;
import dev.latvian.mods.rhino.*;
import java.nio.charset.StandardCharsets;

public final class LoadConditionVerification {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static String script(EditorProject p){return new String(KjsGenerator.generate(p).files().get(WorkspaceState.GENERATED.getFirst()),StandardCharsets.UTF_8);}
    public static int run(){
        var data=RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'minecraft:stick'}],result:{id:'minecraft:paper'},'neoforge:conditions':[{type:'neoforge:false'}],conditions:{temperature:200,enabled:true},metadata:{'neoforge:conditions':['retained']}}");
        var draft=new RecipeDraft("kubejs_editor:conditional",data);draft.source=data.deepCopy();
        var project=new EditorProject();project.recipes.put(draft.id,draft);
        check(LoadConditions.present(CraftingGrid.generated(draft)),"Legacy schema-2 drafts preserve conditions by default");
        String kept=script(project);check(kept.contains("conditionsMatched(event.ops.json()"),"Preserving conditions uses native KJS reload context");
        check(kept.indexOf("if (editorConditions")<kept.indexOf("event.custom("),"Condition evaluated before custom recipe decoding");
        check(kept.contains("neoforge:false"),"Original condition encoding is preserved in exported script");
        new Parser(new ContextFactory().enter()).parse(kept,"conditions.js",1);checks++;
        draft.loadConditionPolicy=LoadConditions.IGNORE;draft.forceNative=true;
        var emitted=CraftingGrid.generated(draft);
        check(!LoadConditions.present(emitted),"Ignore mode strips native root condition envelope");
        check(emitted.getAsJsonObject("conditions").get("temperature").getAsInt()==200,"Mod processing field named conditions remains intact");
        check(emitted.getAsJsonObject("metadata").has(LoadConditions.KEY),"Nested component and opaque data are not recursively stripped");
        check(LoadConditions.present(draft.data)&&LoadConditions.present(draft.source),"Raw draft and rollback source keep the original conditions");
        String ignored=script(project);check(!ignored.contains("editorConditions")&&!ignored.contains("neoforge:false"),"Forced export has no condition guard or original false predicate");
        check(!ignored.contains("forceNative")&&!ignored.contains("loadConditionPolicy"),"Editor-only policy never enters native JSON");
        new Parser(new ContextFactory().enter()).parse(ignored,"ignored.js",1);checks++;
        var copied=draft.copy("kubejs_editor:copy");check(copied.forceNative&&LoadConditions.ignored(copied),"Copy retains force and load policy");
        check(copied.source!=draft.source&&copied.data!=draft.data,"Copy retains independent condition snapshots");
        var restored=ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(project),EditorProject.class);restored.validateStructure();
        check(restored.schemaVersion==2&&restored.recipes.get(draft.id).forceNative&&LoadConditions.ignored(restored.recipes.get(draft.id)),"Schema-2 reopen retains override policy");
        draft.loadConditionPolicy=LoadConditions.PRESERVE;
        check(script(project).contains("editorConditions")&&LoadConditions.present(draft.data),"Policy toggle restores original condition without reconstructing it");
        var bare=data.deepCopy();bare.remove(LoadConditions.KEY);check(!LoadConditions.present(bare),"Bare conditions key is not misclassified as NeoForge loading metadata");
        check(LoadConditions.body(bare).get("conditions").equals(bare.get("conditions")),"Native payload separation preserves custom machine conditions");
        var unrecognized=LoadConditions.body(data);LoadConditions.restoreFromSource(unrecognized,data);
        check(LoadConditions.present(unrecognized),"Unknown serializer's encoded data recovers the source envelope before discovery");
        check(unrecognized.get(LoadConditions.KEY).equals(data.get(LoadConditions.KEY)),"Custom and nested native predicates are recovered without reinterpretation");
        unrecognized.getAsJsonArray(LoadConditions.KEY).get(0).getAsJsonObject().addProperty("type","neoforge:true");
        check(data.getAsJsonArray(LoadConditions.KEY).get(0).getAsJsonObject().get("type").getAsString().equals("neoforge:false"),"Recovered metadata cannot mutate the original source");
        var encoded=RecipeAdapters.parse("{type:'example:unknown',conditions:{temperature:400}}");LoadConditions.restoreFromSource(encoded,data);
        check(encoded.getAsJsonObject("conditions").get("temperature").getAsInt()==400,"Recovering load metadata never overwrites native machine conditions");
        var simpleData=RecipeAdapters.parse("{type:'sample:conditional',ingredient:{item:'minecraft:stone'},result:{id:'minecraft:gravel'},'neoforge:conditions':[]}");
        check(new SimpleRecipeAdapter(simpleData).validate(simpleData).isEmpty(),"Legacy simple adapter allows loading metadata");
        var invalid=RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{}],result:{id:'minecraft:paper'},'neoforge:conditions':[{type:'neoforge:false'}]}");
        draft.data=invalid;draft.forceNative=false;check(script(project).contains("if (editorConditions"),"Conditionally absent recipe may retain a body that would fail when enabled");
        draft.loadConditionPolicy=LoadConditions.IGNORE;draft.forceNative=true;
        check(!script(project).contains("editorConditions"),"Force export delegates payload acceptance to native runtime Codec");
        draft.loadConditionPolicy="invalid";boolean refused=false;try{project.validateStructure();}catch(IllegalArgumentException expected){refused=true;}check(refused,"Invalid persisted policy rejected without changing schema");
        return checks;
    }
}
