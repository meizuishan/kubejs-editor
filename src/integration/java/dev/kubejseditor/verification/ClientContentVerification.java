package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.content.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import dev.latvian.mods.kubejs.block.entity.KubeBlockEntity;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Native registration, persistence and menu verification; never shipped in the production Jar. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientContentVerification {
    private static int phase,ticks,checks;private static CompletableFuture<Void> task;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static ResourceLocation id(String name){return ResourceLocation.parse("kubejs:v11_"+name);}
    private static EditorProject.ItemDraft block(String name,String type){var d=new EditorProject.ItemDraft();ItemKinds.initialize(d,"block");BlockKinds.initialize(d,type);d.id=id(name).toString();d.name="注册验证·"+name;return d;}
    private static StorageAttachment storage(net.minecraft.server.level.ServerLevel level,BlockPos pos){return (StorageAttachment)((KubeBlockEntity)level.getBlockEntity(pos)).attachments.get(StorageAttachment.KEY);}
    private static void finish(int stage)throws Exception{Files.writeString(root().resolve("content-stage"+stage+".json"),"{\"passed\":true,\"checks\":"+checks+"}");Minecraft.getInstance().stop();phase=99;}
    private static void runServer(Runnable action){task=new CompletableFuture<>();Minecraft.getInstance().getSingleplayerServer().execute(()->{try{action.run();task.complete(null);}catch(Throwable error){task.completeExceptionally(error);}});}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyContent")||phase==99)return;
        var mc=Minecraft.getInstance();ticks++;
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>90){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&!mc.player.isAlive()){mc.player.respawn();ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&ticks>100){
                if(EditorRuntime.state().activation!=null){if(ticks>600)throw new AssertionError(EditorRuntime.activationError);return;}
                if(Files.exists(root().resolve("content-stage5.json"))){check(BuiltInRegistries.BLOCK.containsKey(id("store"))&&BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id("store")),"Storage registration returns after rollback and actual restart");check(!EditorRuntime.requiresRestart(),"Rollback restart marker expires");finish(6);return;}
                if(Files.exists(root().resolve("content-stage4.json"))){check(!BuiltInRegistries.BLOCK.containsKey(id("store"))&&!BuiltInRegistries.ITEM.containsKey(id("store"))&&!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id("store")),"All storage registry members disappear after deletion");check(EditorRuntime.project().recipes.containsKey("kubejs_editor:unrelated"),"Storage deletion preserves unrelated pending work");EditorRuntime.rollback();check(EditorRuntime.requiresRestart(),"Rollback requires actual restart");finish(5);return;}
                if(Files.exists(root().resolve("content-stage3.json"))){check(!BuiltInRegistries.BLOCK.containsKey(id("crop"))&&!BuiltInRegistries.ITEM.containsKey(id("crop_seeds")),"Crop and seed disappear together after deletion");check(EditorRuntime.project().recipes.containsKey("kubejs_editor:unrelated"),"Crop deletion preserves unrelated pending work");EditorRuntime.deleteRegisteredItem(id("store").toString());finish(4);return;}
                if(!Files.exists(root().resolve("content-stage1.json"))){
                    var p=EditorRuntime.project();p.recipes.clear();p.items.clear();p.configs.clear();
                    var output=new EditorProject.ItemDraft();output.id=id("output").toString();output.name="新作物产物";p.items.put(output.id,output);
                    var crop=block("crop","crop");crop.block.crop.output=output.id;crop.block.crop.seedName="验证种子";crop.block.crop.outputCount=3;p.items.put(crop.id,crop);
                    var fast=block("fast_crop","crop");fast.block.crop.stages=2;fast.block.crop.textures=new ArrayList<>(List.of("minecraft:block/wheat_stage0","minecraft:block/wheat_stage7"));fast.block.crop.vanillaGrowth=false;fast.block.crop.growthSpeed=1000;fast.block.crop.vanillaBonemeal=false;fast.block.crop.bonemealStages=1;fast.block.crop.seedDrops=false;p.items.put(fast.id,fast);
                    var big=block("store","storage");big.block.storage.rows=9;big.block.storage.columns=12;big.block.storage.slotCapacity=4096;p.items.put(big.id,big);
                    var small=block("small_store","storage");small.block.storage.rows=1;small.block.storage.columns=1;small.block.storage.slotCapacity=1;p.items.put(small.id,small);
                    var normal=block("normal_store","storage");p.items.put(normal.id,normal);
                    EditorRuntime.saveDraft();check(EditorRuntime.validationIssues().isEmpty(),"New registration forms validate: "+EditorRuntime.validationIssues());
                    EditorRuntime.apply(EditorRuntime.review());check(EditorRuntime.state().activation.restart,"Registration requires actual restart");finish(1);return;
                }
                for(String name:List.of("crop","fast_crop","store","small_store","normal_store"))check(BuiltInRegistries.BLOCK.containsKey(id(name)),"Registered block: "+name);
                check(BuiltInRegistries.ITEM.containsKey(id("crop_seeds"))&&!BuiltInRegistries.ITEM.containsKey(id("crop")),"Crop seed is separate from crop block");
                check(new ItemStack(BuiltInRegistries.ITEM.get(id("crop_seeds"))).getHoverName().getString().equals("验证种子"),"Seed localization");
                if(!Files.exists(root().resolve("content-stage2.json"))){runServer(ClientContentVerification::exercise);phase=2;ticks=0;return;}
                runServer(()->{var level=mc.getSingleplayerServer().overworld();var s=storage(level,new BlockPos(10,90,10));check(s.inventory.count(107)==4096&&s.inventory.count(0)==4096,"Last and first slots persist across real JVM restart");check(((CropBlock)level.getBlockState(new BlockPos(12,90,10)).getBlock()).getAge(level.getBlockState(new BlockPos(12,90,10)))==7,"Mature crop state persists");});phase=5;ticks=0;return;
            }
            if(phase==2&&task.isDone()&&ticks>60){task.get();check(mc.screen instanceof StorageScreen,"Native storage opens dedicated menu: screen="+mc.screen+" serverMenu="+mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().containerMenu.getClass().getName());check(((StorageScreen)mc.screen).getMenu().quantity(107)==4096,"Logical count synchronizes to client");mc.options.guiScale().set(2);mc.resizeDisplay();phase=3;ticks=0;return;}
            if(phase==3&&ticks>15){Screenshot.grab(mc.gameDirectory,"content-storage-scale2.png",mc.getMainRenderTarget(),c->{});mc.options.guiScale().set(3);mc.resizeDisplay();phase=4;ticks=0;return;}
            if(phase==4&&ticks>15){Screenshot.grab(mc.gameDirectory,"content-storage-scale3.png",mc.getMainRenderTarget(),c->{});finish(2);return;}
            if(phase==5&&task.isDone()){
                task.get();
                var recipe=new EditorProject.RecipeDraft("kubejs_editor:unrelated",dev.kubejseditor.recipe.RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'minecraft:iron_ingot'}],result:{id:'minecraft:iron_nugget'}}"));EditorRuntime.project().recipes.put(recipe.id,recipe);EditorRuntime.saveDraft();
                var referenced=new EditorProject.RecipeDraft("kubejs_editor:seed_reference",dev.kubejseditor.recipe.RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'kubejs:v11_crop_seeds'}],result:{id:'minecraft:iron_nugget'}}"));EditorRuntime.project().recipes.put(referenced.id,referenced);EditorRuntime.saveDraft();check(!EditorRuntime.itemDeletionBlockers(id("crop").toString()).isEmpty(),"Derived seed reference blocks crop deletion");EditorRuntime.project().recipes.remove(referenced.id);EditorRuntime.saveDraft();EditorRuntime.deleteRegisteredItem(id("crop").toString());finish(3);return;
            }
        }catch(Throwable error){try{Files.writeString(root().resolve("content-failure.json"),ProjectStore.JSON.toJson(Map.of("phase",phase,"error",error.toString())));}catch(Exception ignored){}error.printStackTrace();mc.stop();phase=99;}
    }
    private static void exercise(){
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();player.setInvulnerable(true);player.setHealth(player.getMaxHealth());player.fallDistance=0;
        for(int capacity:new int[]{1,64,99,100,4096}){
            var inv=new StorageInventory(1,1,capacity,()->{});int wanted=capacity;
            while(wanted>0){int offered=Math.min(64,wanted);check(inv.insertItem(0,new ItemStack(Items.IRON_INGOT,offered),false).isEmpty(),"Insert capacity "+capacity);wanted-=offered;}
            check(inv.count(0)==capacity&&inv.insertItem(0,new ItemStack(Items.IRON_INGOT),true).getCount()==1,"Full capacity and simulation "+capacity);
            check(inv.extractItem(0,Integer.MAX_VALUE,true).getCount()<=64&&inv.count(0)==capacity,"Extract simulation and physical bound");
            var copy=new StorageInventory(1,1,capacity,()->{});copy.load(level.registryAccess(),inv.save(level.registryAccess()));check(copy.count(0)==capacity,"Count codec boundary "+capacity);
            int[] drained={0};copy.drain(stack->{check(stack.getCount()<=stack.getMaxStackSize(),"Legal dropped stack");drained[0]+=stack.getCount();});check(drained[0]==capacity,"Drain conserves all items");
        }
        var test=new StorageInventory(1,2,4096,()->{});var named=new ItemStack(Items.IRON_INGOT);named.set(DataComponents.CUSTOM_NAME,Component.literal("Distinct"));test.insertItem(0,named,false);
        check(test.insertItem(0,new ItemStack(Items.IRON_INGOT),false).getCount()==1,"Different components remain distinct");test.insertItem(1,new ItemStack(Items.IRON_SWORD),false);check(test.insertItem(1,new ItemStack(Items.IRON_SWORD),false).getCount()==1,"Unstackables remain one");
        BlockPos pos=new BlockPos(10,90,10);for(int x=-2;x<=4;x++)for(int z=-2;z<=4;z++)level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);if(level.getBlockEntity(pos) instanceof KubeBlockEntity old&&old.attachments.get(StorageAttachment.KEY) instanceof StorageAttachment previous)previous.inventory.drain(stack->{});level.removeBlock(pos,false);level.setBlock(pos,BuiltInRegistries.BLOCK.get(id("store")).defaultBlockState(),3);var storage=storage(level,pos);
        for(int i=0;i<108;i++)for(int n=0;n<64;n++)check(storage.inventory.insertItem(i,new ItemStack(Items.IRON_INGOT,64),false).isEmpty(),"Max inventory fill");
        check(level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,pos,null)==storage.inventory,"Real item-handler capability exposed");
        player.connection.teleport(10.5,90,12.5,0,0);
        var menu=new StorageMenu(17,player.getInventory(),storage);int total=storage.inventory.count(0);menu.clicked(0,0,ClickType.PICKUP,player);check(menu.getCarried().getCount()==64&&storage.inventory.count(0)==total-64,"Pickup splits oversized logical stack");menu.clicked(0,0,ClickType.PICKUP,player);check(menu.getCarried().isEmpty()&&storage.inventory.count(0)==total,"Pickup reinserts without loss");
        menu.clicked(0,1,ClickType.PICKUP,player);check(menu.getCarried().getCount()==64,"Half pickup stays physically legal");menu.clicked(0,0,ClickType.PICKUP,player);
        var second=new StorageMenu(18,player.getInventory(),storage);menu.clicked(0,0,ClickType.PICKUP,player);second.clicked(0,0,ClickType.PICKUP,player);check(storage.inventory.count(0)==total-128,"Separate menus use authoritative quantity");menu.clicked(0,0,ClickType.PICKUP,player);second.clicked(0,0,ClickType.PICKUP,player);
        exerciseMenus(storage,player,server,level);exerciseHoppers(level,pos.offset(5,0,0));
        var crop=(CropBlock)BuiltInRegistries.BLOCK.get(id("crop"));BlockPos cropPos=pos.offset(2,0,0);level.setBlock(cropPos.below(),Blocks.FARMLAND.defaultBlockState(),3);level.setBlock(cropPos,crop.getStateForAge(7),3);
        var drops=Block.getDrops(crop.getStateForAge(7),level,cropPos,null,player,new ItemStack(Items.IRON_HOE));check(drops.stream().filter(s->s.is(BuiltInRegistries.ITEM.get(id("output")))).mapToInt(ItemStack::getCount).sum()==3,"Mature crop produces newly registered output");
        check(Block.getDrops(crop.getStateForAge(0),level,cropPos,null,player,new ItemStack(Items.IRON_HOE)).stream().noneMatch(s->s.is(BuiltInRegistries.ITEM.get(id("output")))),"Immature crop does not produce harvest output");
        check(crop.getStateForAge(0).canSurvive(level,cropPos),"Crop survives on real farmland");level.setBlock(cropPos.below(),Blocks.STONE.defaultBlockState(),3);check(!crop.getStateForAge(0).canSurvive(level,cropPos),"Crop rejects stone planting ground");level.setBlock(cropPos.below(),Blocks.FARMLAND.defaultBlockState(),3);level.setBlock(cropPos,crop.getStateForAge(0),3);crop.performBonemeal(level,level.random,cropPos,level.getBlockState(cropPos));check(crop.getAge(level.getBlockState(cropPos))>0,"Native bone meal advances growth");level.setBlock(cropPos,crop.getStateForAge(7),3);
        player.connection.teleport(10.5,90,12.5,0,0);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,p)->new StorageMenu(id,inventory,storage),Component.literal("大容量存储验证")),buf->{buf.writeBlockPos(pos);buf.writeVarInt(storage.rows);buf.writeVarInt(storage.columns);buf.writeVarInt(storage.capacity);});
    }
    private static int allItems(StorageAttachment storage,net.minecraft.server.level.ServerPlayer a,net.minecraft.server.level.ServerPlayer b,StorageMenu first,StorageMenu second){
        int sum=first.getCarried().getCount()+second.getCarried().getCount();for(int i=0;i<storage.inventory.getSlots();i++)sum+=storage.inventory.count(i);for(var player:List.of(a,b))for(int i=0;i<player.getInventory().getContainerSize();i++)sum+=player.getInventory().getItem(i).getCount();
        for(var entity:a.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,a.getBoundingBox().inflate(8)))sum+=entity.getItem().getCount();return sum;
    }
    private static void exerciseMenus(StorageAttachment main,net.minecraft.server.level.ServerPlayer player,net.minecraft.server.MinecraftServer server,net.minecraft.server.level.ServerLevel level){
        var options=new StorageOptions();options.rows=1;options.columns=3;options.slotCapacity=4096;var storage=new StorageAttachment(main.entity,options);
        var backup=new ArrayList<ItemStack>();for(int i=0;i<player.getInventory().getContainerSize();i++)backup.add(player.getInventory().getItem(i).copy());player.getInventory().clearContent();
        var other=new net.minecraft.server.level.ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"StorageTester"),net.minecraft.server.level.ClientInformation.createDefault());other.setPos(player.position());
        for(int n=0;n<8;n++)storage.inventory.insertItem(0,new ItemStack(Items.IRON_INGOT,64),false);player.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,64));player.getInventory().setItem(1,new ItemStack(Items.COPPER_INGOT,32));
        var first=new StorageMenu(30,player.getInventory(),storage);var second=new StorageMenu(31,other.getInventory(),storage);int expected=allItems(storage,player,other,first,second);
        first.clicked(0,0,ClickType.PICKUP,player);first.clicked(1,1,ClickType.PICKUP,player);first.clicked(1,0,ClickType.PICKUP,player);check(allItems(storage,player,other,first,second)==expected,"Left and right clicks conserve counts");
        first.clicked(0,0,ClickType.QUICK_MOVE,player);first.clicked(30,0,ClickType.QUICK_MOVE,player);check(allItems(storage,player,other,first,second)==expected,"Shift transfers both directions conserve counts");
        first.clicked(2,1,ClickType.SWAP,player);first.clicked(2,1,ClickType.SWAP,player);check(allItems(storage,player,other,first,second)==expected,"Number-key swaps conserve counts");
        first.clicked(31,0,ClickType.PICKUP,player);first.clicked(-999,0,ClickType.QUICK_CRAFT,player);first.clicked(0,1,ClickType.QUICK_CRAFT,player);first.clicked(2,1,ClickType.QUICK_CRAFT,player);first.clicked(-999,2,ClickType.QUICK_CRAFT,player);check(allItems(storage,player,other,first,second)==expected,"Drag distribution conserves counts");
        first.clicked(0,0,ClickType.PICKUP,player);first.clicked(0,0,ClickType.PICKUP_ALL,player);first.clicked(0,0,ClickType.PICKUP,player);check(allItems(storage,player,other,first,second)==expected,"Double-click collection conserves counts");
        first.clicked(0,1,ClickType.THROW,player);check(allItems(storage,player,other,first,second)==expected,"Dropped stack is included in conservation");
        first.clicked(1,0,ClickType.PICKUP,player);second.clicked(1,0,ClickType.PICKUP,other);check(allItems(storage,player,other,first,second)==expected,"Two actual player instances share authoritative inventory");first.clicked(1,0,ClickType.PICKUP,player);second.clicked(1,0,ClickType.PICKUP,other);
        player.getInventory().clearContent();for(int i=0;i<backup.size();i++)player.getInventory().setItem(i,backup.get(i));
    }
    private static void exerciseHoppers(net.minecraft.server.level.ServerLevel level,BlockPos pos){
        level.setBlock(pos,BuiltInRegistries.BLOCK.get(id("normal_store")).defaultBlockState(),3);var storage=storage(level,pos);level.setBlock(pos.above(),Blocks.HOPPER.defaultBlockState(),3);level.setBlock(pos.below(),Blocks.HOPPER.defaultBlockState(),3);
        var top=(net.minecraft.world.level.block.entity.HopperBlockEntity)level.getBlockEntity(pos.above());var bottom=(net.minecraft.world.level.block.entity.HopperBlockEntity)level.getBlockEntity(pos.below());top.setItem(0,new ItemStack(Items.IRON_INGOT,8));
        net.minecraft.world.level.block.entity.HopperBlockEntity.pushItemsTick(level,pos.above(),level.getBlockState(pos.above()),top);check(storage.inventory.count(0)==1,"Actual upper hopper inserts into storage capability");
        check(net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(level,bottom)&&bottom.getItem(0).getCount()==1,"Actual lower hopper extracts normal stack");check(storage.inventory.count(0)+top.getItem(0).getCount()+bottom.getItem(0).getCount()==8,"Hopper transfer conserves items");
    }
}
