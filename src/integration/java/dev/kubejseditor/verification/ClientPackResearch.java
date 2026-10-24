package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import dev.latvian.mods.kubejs.registry.RegistryObjectStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Read-only discovery plus one actual editor transaction, exclusively in a cloned research pack. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientPackResearch {
    private static int phase,ticks;private static final JsonObject report=new JsonObject();private static EditorProject.RecipeDraft request;private static EditorRuntime.ReloadResult result;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void save()throws Exception{Files.writeString(root().resolve("research-result.json"),ProjectStore.JSON.toJson(report));}
    private static JsonObject nativeRecipe(String id){return dev.kubejseditor.export.GenericFormatter.encodeRecipe(Minecraft.getInstance().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).orElseThrow()).getAsJsonObject();}
    private static void registrations()throws Exception{
        var registrations=new JsonArray();int items=0,blocks=0;for(var builder:RegistryObjectStorage.ALL_BUILDERS){
            if(builder.registryKey==null)continue;boolean item=builder.registryKey.equals(net.minecraft.core.registries.Registries.ITEM),block=builder.registryKey.equals(net.minecraft.core.registries.Registries.BLOCK);if(!item&&!block)continue;
            if(item)items++;else blocks++;var row=new JsonObject();row.addProperty("id",builder.id.toString());row.addProperty("registry",item?"item":"block");row.addProperty("builderClass",builder.getClass().getName());row.addProperty("source",String.valueOf(builder.sourceLine));registrations.add(row);
        }report.addProperty("kjsItemBuilders",items);report.addProperty("kjsBlockBuilders",blocks);report.add("registrations",registrations);report.addProperty("editorManagedItems",EditorRuntime.state().applied.items.size());
    }
    private static void discovery()throws Exception{
        registrations();
        var machines=new JsonArray();var runtime=WorkspaceJeiPlugin.runtime;var manager=runtime.getRecipeManager();
        for(var category:manager.createRecipeCategoryLookup().includeHidden().get().toList()){
            var row=new JsonObject();row.addProperty("category",category.getRecipeType().getUid().toString());row.addProperty("title",category.getTitle().getString());
            var catalysts=new JsonArray();for(var typed:manager.createRecipeCatalystLookup(category.getRecipeType()).includeHidden().get().toList())if(typed.getIngredient() instanceof ItemStack stack){var c=new JsonObject();c.addProperty("id",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());c.add("stack",ItemStack.CODEC.encodeStart(Minecraft.getInstance().level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),stack).getOrThrow());catalysts.add(c);}
            row.add("catalysts",catalysts);machines.add(row);
        }report.add("jeiCategories",machines);
        report.addProperty("totalNativeRecipes",Minecraft.getInstance().getSingleplayerServer().getRecipeManager().getRecipes().size());
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void captureJei()throws Exception{
        var mc=Minecraft.getInstance();var manager=WorkspaceJeiPlugin.runtime.getRecipeManager();
        for(mezz.jei.api.recipe.category.IRecipeCategory category:manager.createRecipeCategoryLookup().get().toList())for(Object value:(List<?>)manager.createRecipeLookup(category.getRecipeType()).get().toList())if(value instanceof RecipeHolder<?> holder&&holder.id().toString().equals(request.id)){
            mezz.jei.api.gui.IRecipeLayoutDrawable layout=(mezz.jei.api.gui.IRecipeLayoutDrawable)manager.createRecipeLayoutDrawable(category,value,WorkspaceJeiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            var source=JeiRecipeSources.resolve(layout);report.addProperty("jeiSource",String.valueOf(source.id()));JeiRecipeSources.capture(layout,source.id());report.add("jeiEvidence",ProjectStore.JSON.toJsonTree(SimpleRecipes.evidence(request.id)));return;
        }report.addProperty("jeiSource","not found");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.researchPack")||phase==99)return;var mc=Minecraft.getInstance();ticks++;
        try{
            if(ticks>12000)throw new AssertionError("Research timeout phase "+phase);
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>60){
                var input=JsonParser.parseString(Files.readString(root().resolve("research-request.json"))).getAsJsonObject();
                if(input.has("startupOnly")&&input.get("startupOnly").getAsBoolean()){EditorRuntime.project();registrations();report.addProperty("startupOnly",true);save();mc.stop();phase=99;return;}
                request=ProjectStore.JSON.fromJson(input,EditorProject.RecipeDraft.class);phase=1;ticks=0;mc.tell(()->mc.createWorldOpenFlows().openWorld("EditorVerification",()->{}));return;
            }
            if(phase==1&&mc.player!=null&&mc.level!=null&&WorkspaceJeiPlugin.runtime!=null&&ticks>160){
                EditorRuntime.project();discovery();report.add("original",nativeRecipe(request.id));captureJei();save();SimpleRecipes.setEnabled(EditorRuntime.root(),true);EditorRuntime.invalidateRecipes();
                long start=System.nanoTime();var loaded=EditorRuntime.recipes().stream().filter(r->r.id().equals(request.id)).findFirst().orElseThrow();report.addProperty("indexMillis",(System.nanoTime()-start)/1000000);report.addProperty("readOnlyReason",loaded.error());report.add("requested",request.data);
                try{EditorRuntime.validateRecipe(request.data,Set.of());report.addProperty("nativePreflight",true);}catch(Exception failure){report.addProperty("nativePreflight",false);report.addProperty("nativePreflightError",failure.toString());save();mc.stop();phase=99;return;}
                EditorRuntime.project().recipes.put(request.id,request);EditorRuntime.saveDraft();report.add("issues",ProjectStore.JSON.toJsonTree(EditorRuntime.validationIssues()));save();
                var review=EditorRuntime.review();EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);phase=2;ticks=0;return;
            }
            if(phase==2&&result!=null){report.addProperty("applicationSuccess",result.success());report.addProperty("applicationMessage",result.message());report.add("actual",nativeRecipe(request.id));report.addProperty("activationError",EditorRuntime.activationError);report.addProperty("managedRecipeCount",EditorRuntime.state().applied.recipes.size());save();mc.stop();phase=99;}
        }catch(Throwable error){report.addProperty("error",error.toString());report.addProperty("phase",phase);try{save();}catch(Exception ignored){}error.printStackTrace();mc.stop();phase=99;}
    }
}
