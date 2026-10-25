package dev.kubejseditor;

import com.google.gson.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.GenerationCache;
import dev.kubejseditor.storage.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Ownership, preservation and generation boundaries exercised without a game process. */
public final class BatchAndCacheVerification {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static void fails(Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError(message);}
    private static EditorProject.RecipeDraft rule(String id,String operation){var d=new EditorProject.RecipeDraft(id,RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'minecraft:iron_ingot'}],result:{id:'minecraft:gold_ingot',count:1}}"));d.operation=operation;d.origin=operation.equals("add")?"add":"existing";d.source=d.data.deepCopy();return d;}
    public static int run(){
        checks=0;var cache=new GenerationCache<Integer,String>();Object owner=new Object();int[] calls={0};
        for(int round=0;round<20;round++)for(int key=0;key<1000;key++){int k=key;cache.get(owner,1,key,()->{calls[0]++;return "source:"+k;});}
        check(calls[0]==1000,"Repeated UI views do not encode or read 20,000 copies of the same source");
        check(cache.get(owner,2,1,()->{calls[0]++;return "new";}).equals("new"),"A reload never reuses old source conditions");
        check(cache.get(new Object(),2,1,()->"other world").equals("other world"),"Different managers do not share recipe snapshots");cache.clear();
        check(cache.get(owner,2,1,()->"cleared").equals("cleared"),"Explicit recipe sync clears cached values");
        int[] missing={0};cache.get(owner,2,2,()->{missing[0]++;return null;});cache.get(owner,2,2,()->{missing[0]++;return "bad";});check(missing[0]==1,"Missing sources can be cached without repeated disk probes");

        var state=new WorkspaceState();for(var operation:List.of("add","replace","remove")){var d=rule("test:"+operation,operation);state.applied.recipes.put(d.id,d);}
        var original=ProjectStore.JSON.toJson(state.applied);var ids=new LinkedHashSet<>(state.applied.recipes.keySet());var desired=state.withoutRules(ids,Set.of());
        check(desired.recipes.isEmpty(),"One plan can revoke new, replaced and disabled rules together");
        check(ProjectStore.JSON.toJson(state.applied).equals(original),"Building a deletion batch never mutates applied ownership");
        check(!state.output(desired,new EditorProject()).containsKey(WorkspaceState.GENERATED.getFirst()),"Deleting the final batch removes the generated recipe script");
        var one=state.withoutRules(Set.of("test:add"),Set.of());check(one.recipes.size()==2&&!one.recipes.containsKey("test:add"),"Unselected rules remain in the same transaction");
        fails(()->state.withoutRules(Set.of(),Set.of()),"An empty batch must not reload");fails(()->state.withoutRules(Set.of("test:missing"),Set.of()),"Unknown ownership is rejected");
        state.frozen.put(WorkspaceState.GENERATED.getFirst(),Set.of("test:replace"));fails(()->state.withoutRules(Set.of("test:add","test:replace"),Set.of()),"A protected member rejects the whole batch");
        check(ProjectStore.JSON.toJson(state.applied).equals(original),"Rejected batches cannot partially remove eligible entries");
        String base=WorkspaceState.GENERATED.getFirst(),target="kubejs/server_scripts/kubejs_editor/recipes_overrides_2.js";state.paths.put(base,target);
        var output=state.output(state.applied,new EditorProject());String script=new String(output.get(target),StandardCharsets.UTF_8);
        check(!output.containsKey(base)&&script.contains("// priority: -1002"),"Frozen scripts retain their override route and priority");
        check(!script.contains("test:replace")&&script.contains("test:add"),"A frozen rule is excluded while writable rules remain generated");
        state.frozen.clear();var normal=state.output(state.applied,new EditorProject());check(normal.containsKey(target),"The normal one-pass plan still uses owned output paths");
        var pending=new EditorProject();pending.recipes.put("test:pending",rule("test:pending","add"));var merged=state.merge(pending);merged.recipes.get("test:pending").data.addProperty("extra",1);
        check(!pending.recipes.get("test:pending").data.has("extra"),"The optimized merge keeps drafts isolated from validation normalization");
        check(state.withoutRules(ids,Set.of()).recipes.isEmpty()&&pending.recipes.size()==1,"Revoking applied rules does not submit or clear pending work");

        String asset="kubejs/assets/kubejs_editor/textures/item/shared.png";
        for(String id:List.of("test:item_a","test:item_b")){var i=new EditorProject.ItemDraft();i.id=id;i.register=true;i.texture="kubejs_editor:item/shared";state.applied.items.put(id,i);}state.applied.assets.put(asset,"AQID");
        check(state.withoutRules(Set.of(),Set.of("test:item_a")).assets.containsKey(asset),"Batch item deletion preserves a shared texture");
        check(!state.withoutRules(Set.of(),Set.of("test:item_a","test:item_b")).assets.containsKey(asset),"Removing all owners cleans their unused generated texture");
        var future=new EditorProject.ItemDraft();future.id="test:future";future.texture="kubejs_editor:item/shared";pending.items.put(future.id,future);
        check(state.retainedAssets(pending,Set.of("test:item_a","test:item_b")).containsKey(asset),"Unsubmitted forms keep their shared texture and staged metadata");
        check(state.retainedAssets(pending,Set.of("test:future")).isEmpty(),"A form removed in the same batch does not retain unused assets");
        var modified=new EditorProject.ItemDraft();modified.id="minecraft:stick";modified.register=false;state.applied.items.put(modified.id,modified);
        check(state.canRevokeItem(modified.id)&&!state.canDeleteRegisteredItem(modified.id),"Property restoration does not pretend to unregister vanilla items");
        check(!state.withoutRules(ids,Set.of(modified.id)).items.containsKey(modified.id),"Mixed plans can remove property rules and recipe rules");
        state.frozen.put(WorkspaceState.GENERATED.get(2),Set.of("test:item_b"));fails(()->state.withoutRules(Set.of("test:add"),Set.of("test:item_a","test:item_b")),"A manually-owned item script protects the complete mixed batch");
        var refs=ItemReferences.matching(JsonParser.parseString("{a:['test:item_a',{components:{nested:'test:item_b'}}],number:20480,other:'test:item_ab'}"),Set.of("test:item_a","test:item_b"));check(refs.equals(Set.of("test:item_a","test:item_b")),"Reference checks retain components and distinguish exact IDs in one traversal");

        var conversion=new SourceConversion();conversion.category="other:machine";conversion.sourceType="source:process";conversion.displayType="other:process";conversion.displayPrefix="compat/source/";conversion.check();
        check(conversion.candidate("other:compat/source/quantum","bbb:quantum","source:process"),"Explicit conversion hints preserve custom namespaces");
        check(conversion.candidate("other:compat/source/quantum","another:quantum","source:process"),"Namespace collisions remain candidates for full content verification");
        check(!conversion.candidate("other:compat/source/quantum","bbb:wrong","source:process"),"Similar outputs cannot bypass a provenance path mismatch");
        check(!conversion.candidate("other:compat/source/quantum","bbb:quantum","source:other"),"Different native serializers cannot match");
        check(conversion.sourcePath("other:compat/source/a/b").equals("a/b")&&conversion.sourcePath("other:a/b")==null,"Conversion prefixes are exact and support nested paths");
        var data=RecipeAdapters.parse("{type:'source:process',input:{ingredient:{tag:'c:ingots'},amount:20480,components:{a:1}},conditions:{work:5},'neoforge:conditions':[{type:'neoforge:false'}],unknown:[1,2]}");var projected=conversion.project(data);
        check(projected.get("type").getAsString().equals("other:process")&&!projected.has(LoadConditions.KEY),"Projection changes the display type without importing loading metadata");
        check(projected.get("input").equals(data.get("input"))&&projected.get("unknown").equals(data.get("unknown")),"Projection preserves quantities, tags, components and unknown payloads");
        check(projected.has("conditions")&&data.has(LoadConditions.KEY)&&data.get("type").getAsString().equals("source:process"),"Ordinary machine conditions and source JSON remain untouched");
        conversion.fields.put("/input","/feedstock");check(conversion.project(data).get("feedstock").equals(data.get("input")),"Other integrations can declare explicit field projections");
        conversion.displayPrefix="";fails(conversion::check,"Unknown provenance must not be inferred from ingredient appearance");
        return checks;
    }
    private BatchAndCacheVerification(){}
}
