package dev.kubejseditor;

import dev.kubejseditor.recipe.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.storage.ProjectStore;
import java.nio.file.*;
import java.util.*;

/** User mapping choices and authoring templates must survive discovery/cache replacement. */
public final class MappingPreferencesVerification {
    private static int checks;
    private static void check(boolean value,String note){checks++;if(!value)throw new AssertionError(note);}
    public static int run()throws Exception{
        var root=Files.createTempDirectory("kubejs-editor-discovery-");var legacy=root.resolve(SimpleRecipes.FILE);Files.createDirectories(legacy.getParent());Files.writeString(legacy,"{\"enabled\":false}");var old=Files.readString(legacy);SimpleRecipes.load(root);check(SimpleRecipes.enabled(),"Legacy off preference cannot disable the default compatibility path");SimpleRecipes.setEnabled(root,false);check(SimpleRecipes.enabled()&&Files.readString(legacy).equals(old),"Legacy API cannot turn discovery off or rewrite the obsolete user file");
        var broken=Files.createTempDirectory("kubejs-editor-legacy-setting-");Files.createDirectories(broken.resolve(SimpleRecipes.FILE).getParent());Files.writeString(broken.resolve(SimpleRecipes.FILE),"retired setting");SimpleRecipes.load(broken);check(SimpleRecipes.enabled(),"An obsolete settings file cannot prevent workspace loading");
        var empty=new CompatDefinition("forbidden_arcanus:ritual","confirmed");empty.autoDiscover=false;var data=RecipeAdapters.parse("{type:'forbidden_arcanus:ritual',inputs:[{ingredient:{item:'minecraft:iron_ingot'},amount:1}],main_ingredient:{item:'minecraft:diamond'},result:{type:'forbidden_arcanus:create_item',result_item:{id:'minecraft:gold_ingot'}}}");
        var roundTrip=ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(empty),CompatDefinition.class);check(!roundTrip.automatic()&&roundTrip.fields.isEmpty(),"Persisted empty mapping remains distinct from automatic discovery");check(new RecipePresentation(data,roundTrip).validate().isEmpty(),"Empty mapping does not trigger unrelated pedestal validation");check(MappingConfirmation.prepare(empty,data).fields.isEmpty(),"Field confirmation accepts removal of the final field");check(CompatDefinitions.current(roundTrip,data).fields.isEmpty(),"Semantic refresh cannot repopulate a manually cleared map");
        var legacyDefinition=ProjectStore.JSON.fromJson("{\"type\":\"example:recipe\",\"fields\":[]}",CompatDefinition.class);check(legacyDefinition.automatic(),"Old mapping metadata keeps its former automatic augmentation behavior");
        var automatic=CompatDefinitions.ritual(data);automatic.type="forbidden_arcanus:hephaestus_forge/ritual";automatic.autoDiscover=true;check(CompatDefinitions.current(automatic,data).type.equals(automatic.type),"Dynamic ritual semantics preserve the native registry type");check(CompatDefinitions.current(automatic,data).automatic(),"Dynamic semantic regeneration retains the automatic policy");
        var base=RecipeAdapters.get("minecraft:smelting");check(base.canCreate(),"Existing declared workstations remain authoring templates");var definition=new CompatDefinition(base.type(),"confirmed");definition.autoDiscover=false;var imported=new CompatibilityAdapter(definition,base.newRecipe());RecipeAdapters.register(imported);
        check(RecipeAdapters.get(base.type())==imported&&RecipeAdapters.creation(base.type())==base,"Imported mapping may edit an existing vanilla recipe without replacing its new-recipe template");check(RecipeAdapters.creation().stream().noneMatch(a->a instanceof CompatibilityAdapter||a instanceof SimpleRecipeAdapter),"New-recipe catalogue never exposes discovered generic/simple adapter entries");RecipeAdapters.unregister(imported.type(),imported);check(RecipeAdapters.creation(base.type())==base,"Unregistering a transient import adapter retains the original authoring entry");RecipeAdapters.register(base);
        var sample=RecipeAdapters.parse("{type:'example:temporary_import',ingredients:[{item:'minecraft:iron_ingot'}],results:[{id:'minecraft:gold_ingot'}]}");var simple=new SimpleRecipeAdapter(sample);RecipeAdapters.register(simple);check(RecipeAdapters.creation(simple.type())==null&&!simple.canCreate(),"A newly discovered simple serializer is not a creation menu item");RecipeAdapters.unregister(simple.type(),simple);
        return checks;
    }
    private MappingPreferencesVerification(){}
}
