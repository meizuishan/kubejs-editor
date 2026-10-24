package dev.kubejseditor;

import com.google.gson.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.script.KjsGenerator;
import java.nio.file.*;
import java.util.*;

final class TemplateVerification {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static int run()throws Exception{
        Path root=Files.createTempDirectory("kubejs-editor-templates-");Path directory=Files.createDirectories(root.resolve(RecipeTemplates.DIRECTORY));
        var original=RecipeAdapters.get("create:pressing");
        JsonObject definition=RecipeAdapters.parse("{schemaVersion:1,type:'sample:rolling',title:'轧制',requires:{sample:'[1,2)'},limits:{inputs:1,outputs:2,fluidInputs:0,fluidOutputs:0},defaults:{ingredients:[{item:'minecraft:iron_ingot'}],results:[{id:'minecraft:iron_nugget'}],processing_time:100},parameters:[{key:'processing_time',label:'加工时间',kind:'integer',min:1,max:1000,unit:'tick'}]}");
        Files.writeString(directory.resolve("01-valid.json"),definition.toString());
        Files.writeString(directory.resolve("02-broken.json"),"{broken");
        var duplicate=definition.deepCopy();duplicate.addProperty("type","create:pressing");Files.writeString(directory.resolve("03-duplicate.json"),duplicate.toString());
        var absent=definition.deepCopy();absent.addProperty("type","missing:rolling");absent.getAsJsonObject("requires").addProperty("missing","[1,2)");Files.writeString(directory.resolve("04-missing.json"),absent.toString());
        var messages=RecipeTemplates.load(root,(mod,range)->mod.equals("sample")?"":"缺少："+mod);
        check(messages.size()==4,"Every template has a diagnostic");
        check(RecipeAdapters.get("create:pressing")==original,"Built-in adapters cannot be overridden");
        check(RecipeAdapters.get("missing:rolling")==null,"Missing requirements isolate a template");
        check(RecipeTemplates.get("sample:rolling")!=null,"Valid neighbor loads despite bad files");
        var data=RecipeAdapters.get("sample:rolling").newRecipe();
        check(RecipeAdapters.validateData(data).isEmpty(),"Defaults validate");
        data.add("opaque_metadata",RecipeAdapters.parse("{vendor:'unchanged',nested:{anything:[1,2,3]}}"));var unknown=data.get("opaque_metadata").deepCopy();
        var slots=RecipeCanvas.slots(data);check(slots.size()==2,"Template creates editable input/output slots");
        slots.getFirst().set(RecipeAdapters.parse("{tag:'c:ingots/iron'}"));
        check(data.get("opaque_metadata").equals(unknown),"Slot edits preserve unknown JSON");
        check(RecipeLimits.forType("sample:rolling").outputs()==2,"Template limits reach shared add controls");
        data.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:gold_nugget'}"));
        check(!RecipeLimits.forType("sample:rolling").canAdd(data,true,false),"Declared slot limit disables additions");
        data.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:copper_ingot'}"));
        check(RecipeAdapters.validateData(data).stream().anyMatch(v->v.startsWith("/results")),"Over-limit imported outputs are preserved and diagnosed");data.getAsJsonArray("results").remove(2);
        data.addProperty("processing_time",0);check(RecipeAdapters.validateData(data).stream().anyMatch(v->v.startsWith("/processing_time")),"Parameter range error has a field pointer");
        data.addProperty("processing_time",1.5);check(!RecipeAdapters.validateData(data).isEmpty(),"Integer parameter rejects fractions");
        data.addProperty("processing_time",100);var p=new EditorProject();p.recipes.put("sample:test",new EditorProject.RecipeDraft("sample:test",data));
        var generated=KjsGenerator.generate(p);check(generated.files().containsKey("kubejs/server_scripts/kubejs_editor/recipes.js"),"Templates use the standard generator");
        RecipeCanvas.slots(data).getFirst().set(JsonNull.INSTANCE);check(data.getAsJsonArray("ingredients").isEmpty(),"Removing template input changes its JSON array");
        check(!RecipeAdapters.validateData(data).isEmpty(),"Empty required input blocks application");
        RecipeTemplates.load(root.resolve("empty"),(mod,range)->"");check(RecipeAdapters.get("sample:rolling")==null,"Reload removes adapters for deleted files");
        check(p.recipes.get("sample:test").data.has("opaque_metadata"),"Reload leaves existing draft data intact");
        return checks;
    }
}
