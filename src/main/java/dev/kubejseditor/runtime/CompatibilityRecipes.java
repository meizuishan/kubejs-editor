package dev.kubejseditor.runtime;

import com.google.gson.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.model.EditorProject.RecipeDraft;
import dev.kubejseditor.storage.*;
import dev.latvian.mods.kubejs.recipe.component.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import java.nio.file.*;
import java.util.*;
import static dev.kubejseditor.recipe.compat.CompatDefinition.*;

/** Public schema inspection plus versioned user mappings; no mod classes are linked. */
public final class CompatibilityRecipes {
    private static final Map<String,CompatibilityAdapter> registered=new HashMap<>();
    private static final Map<String,RecipeAdapter> previous=new HashMap<>();
    private static final Map<String,CompatDefinition> mappings=new HashMap<>();
    private static Path root;
    private static Object schemaOwner;
    private static final Map<String,dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaType> schemaIndex=new HashMap<>();
    public static void invalidateSchemas(){schemaOwner=null;schemaIndex.clear();}
    public static String modVersion(String type){return ModList.get().getModContainerById(type.substring(0,type.indexOf(':'))).map(m->m.getModInfo().getVersion().toString()).orElse("");}
    public static void load(Path directory)throws java.io.IOException{if(directory.equals(root))return;for(var e:registered.entrySet()){RecipeAdapters.unregister(e.getKey(),e.getValue());var prior=previous.get(e.getKey());if(prior!=null)RecipeAdapters.register(prior);}registered.clear();previous.clear();mappings.clear();root=directory;Path file=directory.resolve("kubejs/config/kubejs_editor/recipe-mappings.json");if(Files.exists(file)){var array=JsonParser.parseString(Files.readString(file)).getAsJsonArray();for(var v:array){var d=ProjectStore.JSON.fromJson(v,CompatDefinition.class);d.check();mappings.put(d.type,d);}}}
    public static void register(CompatDefinition definition,JsonObject example){var a=new CompatibilityAdapter(definition,example);if(!previous.containsKey(definition.type))previous.put(definition.type,RecipeAdapters.get(definition.type));registered.put(definition.type,a);RecipeAdapters.register(a);}
    public static CompatDefinition policy(String type,JsonObject data){var d=mappings.get(type);return d!=null&&d.modVersion.equals(modVersion(type))&&d.shape.equals(CompatJson.mappingShape(data))?ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(d),CompatDefinition.class):null;}
    public static CompatDefinition discover(String type,JsonObject data,Object recipe){
        var saved=policy(type,data);if(saved!=null&&!saved.source.equals("automatic")){register(saved,data);return saved;}
        var d=automaticDefinition(type,data,recipe);if(d!=null){d.modVersion=modVersion(type);register(d,data);}return d;
    }
    /** Rebuild semantics from native/schema information, independently of saved/manual adapters. */
    public static CompatDefinition automaticDefinition(String type,JsonObject data,Object recipe){
        CompatDefinition d=knownDefinition(type,data,recipe);
        if(d==null)d=schema(type,data);return d;
    }
    /** Historical profiles are candidates; new compatibility uses public data rather than new names. */
    public static CompatDefinition knownDefinition(String type,JsonObject data,Object recipe){
        CompatDefinition d=null;
        if(recipe!=null&&recipe.getClass().getName().equals("aztech.modern_industrialization.machines.recipe.MachineRecipe"))d=CompatDefinitions.machine(type);
        else if(CompatDefinitions.isRitual(type)){d=CompatDefinitions.ritual(data);d.type=type;}
        else if(type.equals("ars_nouveau:enchanting_apparatus"))d=CompatDefinitions.apparatus(type);
        if(d==null&&recipe!=null&&recipe.getClass().getName().equals("com.jdte.common.recipes.InfusionRecipe"))d=CompatDefinitions.jdteInfusion(type);
        if(d==null&&recipe!=null&&recipe.getClass().getName().equals("com.jdte.common.recipes.BioFactoryRecipe"))d=CompatDefinitions.jdteBioFactory(type,data);
        if(d!=null)d.modVersion=modVersion(type);return d;
    }
    public static CompatDefinition schema(String type,JsonObject data){
        var storage=ScriptIndex.schemas();if(storage==null)return null;
        if(schemaOwner!=storage){schemaIndex.clear();for(var namespace:storage.namespaces.values())for(var value:namespace.values()){schemaIndex.putIfAbsent(value.id.toString(),value);if(value.serializerKey!=null)schemaIndex.putIfAbsent(value.serializerKey.location().toString(),value);}schemaOwner=storage;}
        var schema=schemaIndex.get(type);if(schema==null)return null;
        var d=new CompatDefinition(type,"kubejs-schema");
        for(var key:schema.schema.keys){String name=key.names.stream().filter(data::has).findFirst().orElse(key.name);if(key.excluded||!data.has(name))continue;String role=key.role.toString().toUpperCase(Locale.ROOT);Role r=role.contains("OUTPUT")?Role.OUTPUT:role.contains("INPUT")?Role.INPUT:Role.PARAMETER;schemaField(d,key.component,"/"+CompatJson.escape(name),data.get(name),r,!key.optional(),0);}
        addParameters(d,data);return d.fields.stream().anyMatch(Field::slot)?d:null;
    }
    private static void schemaField(CompatDefinition d,RecipeComponent<?> component,String path,JsonElement value,Role role,boolean required,int depth){if(depth>10||d.fields.size()>240)return;
        if(component instanceof EitherRecipeComponent<?,?> either){var mc=Minecraft.getInstance();var ops=mc.getSingleplayerServer().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);try{schemaField(d,either.left().codec().parse(ops,value).result().isPresent()?either.left():either.right(),path,value,role,required,depth+1);}catch(RuntimeException ignored){d.add(path,path,Kind.JSON,role);}return;}
        boolean list=component instanceof ListRecipeComponent<?>;if(list){component=((ListRecipeComponent<?>)component).component();if(component instanceof CustomObjectRecipeComponent||component instanceof MapRecipeComponent<?,?>||component instanceof ListRecipeComponent<?>){if(value.isJsonArray())for(int i=0;i<value.getAsJsonArray().size();i++)schemaField(d,component,path+"/"+i,value.getAsJsonArray().get(i),role,required,depth+1);return;}}
        if(component instanceof CustomObjectRecipeComponent object&&value.isJsonObject()){for(var child:object.keys())if(value.getAsJsonObject().has(child.name()))schemaField(d,child.component(),path+"/"+CompatJson.escape(child.name()),value.getAsJsonObject().get(child.name()),role,!child.optional(),depth+1);return;}
        if(component instanceof MapRecipeComponent<?,?> map&&value.isJsonObject()){for(var child:value.getAsJsonObject().entrySet())schemaField(d,map.component(),path+"/"+CompatJson.escape(child.getKey()),child.getValue(),role,required,depth+1);return;}
        String id=component.type().id().toString();Kind kind=switch(id){case "kubejs:input_item","kubejs:output_item","kubejs:item_stack","kubejs:sized_ingredient","kubejs:flat_sized_ingredient","kubejs:optional_sized_ingredient","kubejs:optional_flat_sized_ingredient","kubejs:ingredient"->Kind.ITEM;case "kubejs:input_fluid","kubejs:output_fluid","kubejs:fluid_stack","kubejs:sized_fluid_ingredient","kubejs:flat_sized_fluid_ingredient","kubejs:nested_sized_fluid_ingredient","kubejs:optional_flat_sized_fluid_ingredient","kubejs:optional_nested_sized_fluid_ingredient"->Kind.FLUID;default->null;};
        JsonElement sample=list&&value.isJsonArray()&&!value.getAsJsonArray().isEmpty()?value.getAsJsonArray().get(0):value;Object decoded=null;try{var ops=Minecraft.getInstance().getSingleplayerServer().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);decoded=component.codec().parse(ops,sample).result().orElse(null);}catch(RuntimeException ignored){}
        if(kind==null){if(decoded instanceof net.minecraft.world.item.ItemStack||decoded instanceof net.minecraft.world.item.crafting.Ingredient||decoded instanceof net.neoforged.neoforge.common.crafting.SizedIngredient)kind=Kind.ITEM;else if(decoded instanceof net.neoforged.neoforge.fluids.FluidStack||decoded instanceof net.neoforged.neoforge.fluids.crafting.FluidIngredient||decoded instanceof net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient)kind=Kind.FLUID;}
        if(kind==null&&!list&&value.isJsonPrimitive())kind=value.getAsJsonPrimitive().isBoolean()?Kind.BOOLEAN:value.getAsJsonPrimitive().isNumber()?Kind.NUMBER:value.getAsJsonPrimitive().isString()?Kind.STRING:Kind.JSON;
        if(kind==null)kind=Kind.JSON;var f=d.add(path,path,kind,kind==Kind.NUMBER||kind==Kind.BOOLEAN?Role.PARAMETER:role);f.list=list&&value.isJsonArray()&&f.slot();f.required=required;f.integer=Set.of("kubejs:int","kubejs:long","kubejs:ticks").contains(id);if(id.equals("kubejs:ticks"))f.unit="tick";if(f.slot()){CompatFields.shape(f,value);if(f.quantity.isBlank()&&(role==Role.OUTPUT||decoded instanceof net.minecraft.world.item.ItemStack||decoded instanceof net.neoforged.neoforge.common.crafting.SizedIngredient||decoded instanceof net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient))f.quantity=kind==Kind.FLUID?"amount":"count";if(sample.isJsonObject()&&sample.getAsJsonObject().has("chance"))f.probability="chance";if(f.list&&!value.getAsJsonArray().isEmpty())f.template=sample.deepCopy();}
    }
    private static void addParameters(CompatDefinition d,JsonObject data){for(String path:SimpleRecipeAdapter.parameters(data)){String pointer="/"+path;if(d.fields.stream().anyMatch(f->pointer.equals(f.path)||pointer.startsWith(f.path+"/")))continue;var value=CompatJson.get(data,pointer);d.add(pointer,path,value.getAsJsonPrimitive().isBoolean()?Kind.BOOLEAN:Kind.NUMBER,Role.PARAMETER);}}
    public static void confirm(CompatDefinition d,JsonObject data)throws java.io.IOException{
        confirm(d,data,false);
    }
    public static void confirm(CompatDefinition d,JsonObject data,boolean forceNative)throws java.io.IOException{
        var next=forceNative?ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(d),CompatDefinition.class):MappingConfirmation.prepare(d,data);next.check();next.source="confirmed";next.autoDiscover=false;next.modVersion=modVersion(next.type);next.shape=CompatJson.mappingShape(data);
        for(var f:next.fields){var v=CompatJson.get(data,f.path);if(f.list&&v.isJsonArray()&&!v.getAsJsonArray().isEmpty())f.template=v.getAsJsonArray().get(0).deepCopy();}
        var adapter=new CompatibilityAdapter(next,data);var errors=forceNative?List.<String>of():adapter.validate(data);if(!errors.isEmpty())throw new IllegalArgumentException(String.join("；",errors));
        var saved=new HashMap<>(mappings);saved.put(next.type,next);var definitions=new ArrayList<>(saved.values());definitions.sort(Comparator.comparing(v->v.type));
        FileTransaction.atomicWrite(EditorRuntime.root().resolve("kubejs/config/kubejs_editor/recipe-mappings.json"),ProjectStore.JSON.toJson(definitions).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // Publish only after the file succeeds; a failed confirmation keeps previous mappings intact.
        mappings.put(next.type,next);register(next,data);d.source=next.source;d.autoDiscover=next.autoDiscover;d.modVersion=next.modVersion;d.shape=next.shape;d.fields=next.fields;EditorRuntime.invalidateRecipes();
    }
    public static void reset(CompatDefinition automatic,JsonObject data)throws java.io.IOException{
        var next=ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(automatic),CompatDefinition.class);next.check();next.autoDiscover=true;next.modVersion=modVersion(next.type);next.shape=CompatJson.mappingShape(data);
        var marker=new CompatDefinition(next.type,"automatic");marker.autoDiscover=true;marker.modVersion=next.modVersion;marker.shape=next.shape;
        var saved=new HashMap<>(mappings);saved.put(next.type,marker);var definitions=new ArrayList<>(saved.values());definitions.sort(Comparator.comparing(v->v.type));FileTransaction.atomicWrite(EditorRuntime.root().resolve("kubejs/config/kubejs_editor/recipe-mappings.json"),ProjectStore.JSON.toJson(definitions).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mappings.put(next.type,marker);register(next,data);automatic.autoDiscover=true;automatic.modVersion=next.modVersion;automatic.shape=next.shape;EditorRuntime.invalidateRecipes();
    }
    public static void annotate(RecipeDraft draft){if(draft.data==null||draft.operation.equals("remove"))return;if(RecipeAdapters.get(draft.data.get("type").getAsString()) instanceof CompatibilityAdapter a){draft.compatibility=ProjectStore.JSON.toJsonTree(a.presentation(draft.data).definition).getAsJsonObject();draft.simple=false;}else if(RecipeAdapters.get(draft.data.get("type").getAsString()) instanceof SimpleRecipeAdapter){draft.simple=true;draft.compatibility=null;}}
    public static void restore(RecipeDraft draft){if(draft.operation.equals("remove")||draft.data==null)return;var saved=policy(draft.data.get("type").getAsString(),draft.data);if(saved!=null){if(!saved.source.equals("automatic"))register(saved,draft.data);return;}if(draft.compatibility==null)return;var d=ProjectStore.JSON.fromJson(draft.compatibility,CompatDefinition.class);if(!d.type.equals(draft.data.get("type").getAsString()))throw new IllegalArgumentException("适配类型与配方不一致");if(!d.modVersion.isBlank()&&!d.modVersion.equals(modVersion(d.type)))throw new IllegalArgumentException("模组版本已变化，请重新确认适配映射");register(d,draft.data);}
    public static boolean available(JsonObject data){return data!=null&&RecipeAdapters.get(data.get("type").getAsString()) instanceof CompatibilityAdapter;}
    private CompatibilityRecipes(){}
}
