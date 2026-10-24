package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import java.nio.file.*;
import java.util.*;

@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientTypesVerification {
    private static int phase,ticks,checks,shotDelay;private static String shot;private static WorkspaceScreen screen;private static EditorRuntime.ReloadResult result;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static Object get(String key)throws Exception{var f=WorkspaceScreen.class.getDeclaredField(key);f.setAccessible(true);return f.get(screen);}
    private static void set(String key,Object value)throws Exception{var f=WorkspaceScreen.class.getDeclaredField(key);f.setAccessible(true);f.set(screen,value);}
    private static Object call(String name,Class<?>[] types,Object... args)throws Exception{for(Class<?> c=WorkspaceScreen.class;c!=null;c=c.getSuperclass())try{var m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(screen,args);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
    private static void call(String name)throws Exception{call(name,new Class<?>[]{});}
    private static void capture(String name){shot=name;shotDelay=8;}
    private static void click(String text){var b=Minecraft.getInstance().screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().equals(text)).map(c->(Button)c).findFirst().orElseThrow(()->new AssertionError("Missing button "+text));check(b.active,"Button active: "+text);b.onPress();}
    private static EditorProject.RecipeDraft current()throws Exception{return (EditorProject.RecipeDraft)get("recipe");}
    private static void select(String type)throws Exception{var d=new EditorProject.RecipeDraft("kubejs_editor:v5_view",RecipeAdapters.get(type).newRecipe());call("selectRecipe",new Class[]{EditorProject.RecipeDraft.class,boolean.class},d,false);}
    private static void frame()throws Exception{call("rebuildWidgets");}
    private static void pendingClear()throws Exception{set("recipe",null);set("item",null);set("dirty",false);for(var op:EditorRuntime.operations())EditorRuntime.cancel(op);frame();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyTypes")||phase==99)return;
        var mc=Minecraft.getInstance();ticks++;if(shot!=null&&--shotDelay<=0){Screenshot.grab(mc.gameDirectory,"kjs-v050-"+shot+".png",mc.getMainRenderTarget(),c->{});shot=null;}
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>100){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&ticks>120){
                check(net.neoforged.fml.ModList.get().getModContainerById("kubejs_editor").orElseThrow().getModInfo().getDisplayName().equals("KubeJS Editor"),"User-facing mod title has no prefix");
                for(String mod:List.of("jei","create","aeronautics","sable","ftblibrary","inventoryprofilesnext"))check(net.neoforged.fml.ModList.get().isLoaded(mod),"Compatibility mod: "+mod);
                if(Files.exists(root().resolve("types-stage1.json"))){
                    if(EditorRuntime.state().activation!=null){if(ticks>500)throw new AssertionError("Restart activation: "+EditorRuntime.activationError);return;}
                    check(EditorRuntime.project().items.isEmpty(),"Restart activation clears pending registration");
                    for(String kind:ItemKinds.NAMES.keySet()){
                        var id=ResourceLocation.parse("kubejs:v5_"+kind);check(BuiltInRegistries.ITEM.containsKey(id),"Registered "+kind);var item=BuiltInRegistries.ITEM.get(id);var stack=item.getDefaultInstance();
                        if(kind.equals("food"))check(stack.get(DataComponents.FOOD)!=null&&stack.get(DataComponents.FOOD).nutrition()==7,"Food nutrition actually applies");
                        if(ItemKinds.tool(kind))check(item instanceof TieredItem&&stack.getMaxDamage()==333,"Real tool/weapon class and durability: "+kind);
                        if(ItemKinds.tool(kind)){var draft=EditorRuntime.state().applied.items.get(id.toString());var entries=stack.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers();for(String stat:List.of("damage","attackSpeed")){double expected=ItemKinds.value(draft,stat,0)-(stat.equals("damage")?1:4);String attribute=stat.equals("damage")?"minecraft:generic.attack_damage":"minecraft:generic.attack_speed";check(entries.stream().anyMatch(a->a.attribute().unwrapKey().orElseThrow().location().toString().equals(attribute)&&Math.abs(a.modifier().amount()-expected)<.001),"Native combat stat "+kind+" / "+stat);}}
                        if(kind.equals("pickaxe"))check(item instanceof PickaxeItem,"Pickaxe has native mining behavior");if(kind.equals("sword"))check(item instanceof SwordItem,"Sword is a native weapon");
                        if(ItemKinds.armor(kind)){check(item instanceof ArmorItem&&stack.getMaxDamage()==333,"Native armor and durability: "+kind);check(stack.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream().anyMatch(a->a.modifier().amount()==9),"Custom armor value is effective: "+kind);}
                        if(kind.equals("block")){check(item instanceof BlockItem&&BuiltInRegistries.BLOCK.containsKey(id),"Block category creates both registries");var block=BuiltInRegistries.BLOCK.get(id);check(Math.abs(block.defaultBlockState().getDestroySpeed(mc.level,mc.player.blockPosition())-2.5)<.01&&block.defaultBlockState().getLightEmission()==12,"Block hardness and brightness actually apply");check(block.getName().getString().equals("分类验证方块"),"Registered block Chinese name");}
                    }
                    screen=new WorkspaceScreen();mc.setScreen(screen);call("selectItem",new Class[]{EditorProject.ItemDraft.class},EditorRuntime.state().applied.items.get("kubejs:v5_block"));capture("registered-block");phase=50;ticks=0;return;
                }
                var review=EditorRuntime.review();EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);phase=2;ticks=0;return;
            }
            if(phase<2||ticks<25)return;if(ticks>1800)throw new AssertionError("Timed out at phase "+phase);
            switch(phase){
                case 2 -> {if(result==null)return;check(result.success(),"Fixture activation");screen=new WorkspaceScreen();mc.setScreen(screen);select("create:mechanical_crafting");CraftingGrid.resize(current(),9);for(int i:new int[]{0,8,72,80})RecipeCanvas.slots(current()).get(i).set(RecipeAdapters.parse("{item:'minecraft:iron_ingot'}"));frame();capture("mechanical9");}
                case 3 -> {
                    check(screen.ghostTargets().size()==82,"All 81 grid cells and result fit on screen");for(var target:screen.ghostTargets()){var a=target.area();check(a.getX()>=(int)get("canvasX")&&a.getX()+a.getWidth()<=(int)get("propX")&&a.getY()+a.getHeight()<=(int)call("canvasBottom",new Class<?>[]{}),"Scaled JEI target inside canvas");}
                    var target=screen.ghostTargets().get(80);var before=mc.player.getInventory().getItem(0).copy();target.accept().accept(new ItemStack(Items.GOLD_INGOT));check(current().grid.cells.get(80).getAsJsonObject().get("item").getAsString().equals("minecraft:gold_ingot"),"Scaled bottom-right ghost slot writes exact cell");check(ItemStack.matches(before,mc.player.getInventory().getItem(0)),"Drag consumes no inventory");
                    mc.options.guiScale().set(3);mc.resizeDisplay();frame();capture("mechanical9-scale3");
                }
                case 4 -> {check((int)get("listW")>0&&screen.ghostTargets().size()==82,"Pending sidebar stays visible and all cells fit at GUI scale 3");for(int tab:List.of(0,1,2)){set("tab",tab);frame();check((int)get("listW")>0,"Sidebar available in editing tab "+tab);}mc.options.guiScale().set(2);mc.resizeDisplay();select("create:sequenced_assembly");var d=current().data;RecipeCanvas.addStep(d,"create:pressing");RecipeCanvas.addStep(d,"create:cutting");RecipeCanvas.addStep(d,"create:filling");frame();capture("sequence");}
                case 5 -> {check(screen.ghostTargets().size()>=13,"Sequence has visible material, intermediate and result targets");var fluid=screen.ghostTargets().stream().filter(WorkspaceScreen.GhostTarget::fluid).findFirst().orElseThrow();fluid.accept().accept(new FluidStack(net.minecraft.world.level.material.Fluids.WATER,500));check(current().data.getAsJsonArray("sequence").get(3).getAsJsonObject().getAsJsonArray("ingredients").get(1).getAsJsonObject().get("amount").getAsInt()==500,"Sequence fluid target reaches exact step");check(CreateSceneRenderer.failures().isEmpty(),"Sequence native animations render");select("create:pressing");capture("pressing-depot");}
                case 6 -> {select("create:filling");current().data.getAsJsonArray("ingredients").set(1,RecipeAdapters.parse("{amount:250}"));frame();capture("spout-empty");}
                case 7 -> {check(CreateSceneRenderer.failures().isEmpty(),"Complete empty spout assembly renders");var fluid=screen.ghostTargets().stream().filter(WorkspaceScreen.GhostTarget::fluid).findFirst().orElseThrow();fluid.accept().accept(new FluidStack(net.minecraft.world.level.material.Fluids.WATER,250));capture("spout-water");}
                case 8 -> {check(CreateSceneRenderer.failures().isEmpty(),"Filled and empty spouts both render");pendingClear();set("tab",0);frame();int before=EditorRuntime.project().items.size();click("+注册物品");check(mc.screen instanceof ChoiceScreen&&EditorRuntime.project().items.size()==before,"Category choice precedes any pending registration");capture("categories");}
                case 9 -> {click("工具");click("镐");check(((EditorProject.ItemDraft)get("item")).kind.equals("pickaxe")&&get("form").toString().contains("挖掘速度")&&!get("form").toString().contains("饥饿值"),"Tool category exposes only relevant numeric fields");capture("tool-fields");}
                case 10 -> {
                    pendingClear();for(String kind:ItemKinds.NAMES.keySet()){
                        var d=new EditorProject.ItemDraft();ItemKinds.initialize(d,kind);d.id="kubejs:v5_"+kind;d.name="分类验证"+ItemKinds.NAMES.get(kind);if(ItemKinds.tool(kind)||ItemKinds.armor(kind))d.properties.addProperty("maxDamage",333);if(ItemKinds.armor(kind))d.settings.addProperty("armor",9);if(kind.equals("food"))d.properties.addProperty("nutrition",7);if(kind.equals("block")){d.settings.addProperty("hardness",2.5);d.settings.addProperty("lightLevel",12);d.name="分类验证方块";}EditorRuntime.project().items.put(d.id,d);
                    }
                    EditorRuntime.saveDraft();check(EditorRuntime.validationIssues().isEmpty(),"All supported category scripts validate");var review=EditorRuntime.review();check(review.restartRequired(),"Typed registrations require restart");EditorRuntime.apply(review);Files.writeString(root().resolve("types-stage1.json"),"{\"passed\":true,\"checks\":"+checks+"}");mc.stop();phase=99;return;
                }
                case 50 -> {mc.options.guiScale().set(3);mc.resizeDisplay();select("create:mechanical_crafting");CraftingGrid.resize(current(),9);frame();capture("mechanical9-final-scale3");}
                case 51 -> {check(screen.ghostTargets().size()==82&&CreateSceneRenderer.failures().isEmpty(),"Final auto-fit includes native crafter preview and every slot");check(WorkspaceJeiPlugin.runtime.getIngredientListOverlay().isListDisplayed(),"JEI ingredient panel remains visible after GUI scaling");Files.writeString(root().resolve("types-stage2.json"),"{\"passed\":true,\"checks\":"+checks+"}");mc.stop();phase=99;return;}
            }
            phase++;ticks=0;
        }catch(Throwable e){try{Files.writeString(root().resolve("types-failure.json"),"{\"phase\":"+phase+",\"error\":"+ProjectStore.JSON.toJson(e.toString())+"}");}catch(Exception ignored){}e.printStackTrace();mc.stop();phase=99;}
    }
}
