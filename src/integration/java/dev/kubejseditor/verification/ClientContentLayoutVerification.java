package dev.kubejseditor.verification;

import dev.kubejseditor.client.*;
import dev.kubejseditor.content.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientContentLayoutVerification {
    private static int phase,ticks,checks,unloadTicks;private static CompletableFuture<Void> task;private static CompletableFuture<Boolean> unloaded;private static StorageAttachment farStorage;private static final BlockPos FAR=new BlockPos(16000,90,16000);
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void select(WorkspaceScreen screen,EditorProject.ItemDraft item)throws Exception{var m=WorkspaceScreen.class.getDeclaredMethod("selectItem",EditorProject.ItemDraft.class);m.setAccessible(true);m.invoke(screen,item);}
    private static void write(WorkspaceScreen screen,String label,String value)throws Exception{
        for(Object form:(List<?>)field(screen,"form")){var l=form.getClass().getDeclaredMethod("label");l.setAccessible(true);if(!l.invoke(form).equals(label))continue;var w=form.getClass().getDeclaredMethod("set");w.setAccessible(true);@SuppressWarnings("unchecked") var consumer=(Consumer<String>)w.invoke(form);consumer.accept(value);return;}throw new AssertionError("Missing form "+label);
    }
    private static void forms()throws Exception{
        var mc=Minecraft.getInstance();var screen=new WorkspaceScreen();mc.setScreen(screen);var item=new EditorProject.ItemDraft();ItemKinds.initialize(item,"block");BlockKinds.initialize(item,"storage");item.id="kubejs:v11_ui_store";select(screen,item);
        write(screen,"库存行数（1～9）","9");write(screen,"库存列数（1～12）","12");write(screen,"单格容量（1～4096）","4096");check(((EditorProject.ItemDraft)field(screen,"item")).block.storage.slotCapacity==4096,"Storage form accepts maximum values");
        for(String bad:List.of("0","10","1.5","NaN")){boolean rejected=false;try{write(screen,"库存行数（1～9）",bad);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Storage UI rejects invalid row: "+bad);}
        var crop=new EditorProject.ItemDraft();ItemKinds.initialize(crop,"block");BlockKinds.initialize(crop,"crop");crop.id="kubejs:v11_ui_crop";select(screen,crop);write(screen,"生长阶段数（2～16）","16");var selected=(EditorProject.ItemDraft)field(screen,"item");check(selected.block.crop.textures.size()==16,"Stage selector rebuilds sixteen texture fields");write(screen,"阶段 15 材质","minecraft:block/wheat_stage7");
        for(int scale:new int[]{2,3}){mc.options.guiScale().set(scale);mc.resizeDisplay();check(WorkspaceJeiPlugin.runtime.getIngredientListOverlay().isListDisplayed(),"JEI remains visible beside new registration forms at scale "+scale);}
        var selectedField=WorkspaceScreen.class.getDeclaredField("item");selectedField.setAccessible(true);selectedField.set(screen,null);var dirty=WorkspaceScreen.class.getDeclaredField("dirty");dirty.setAccessible(true);dirty.setBoolean(screen,false);
        for(String id:List.of("kubejs:v11_ui_crop","kubejs:v11_ui_store"))if(EditorRuntime.project().items.containsKey(id))EditorRuntime.cancel(new PendingOperation("item",id,"注册验证"));mc.setScreen(null);
    }
    private static void cropAndStackLimits(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player){
        level.setDayTime(1000);var block=(net.minecraft.world.level.block.CropBlock)BuiltInRegistries.BLOCK.get(ResourceLocation.parse("kubejs:v11_fast_crop"));var pos=new BlockPos(14,90,12);level.setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState(),3);level.removeBlock(pos,false);
        var held=player.getMainHandItem().copy();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("kubejs:v11_fast_crop_seeds"))));
        var hit=new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(14.5,90,12.5),net.minecraft.core.Direction.UP,pos.below(),false);player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit));check(level.getBlockState(pos).is(block),"Native seed item plants its crop on farmland");player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,held);
        block.performBonemeal(level,level.random,pos,level.getBlockState(pos));check(block.getAge(level.getBlockState(pos))==1,"Custom bone meal advances to configured final stage");level.setBlock(pos,block.getStateForAge(0),3);level.getBlockState(pos).randomTick(level,pos,level.random);check(block.getAge(level.getBlockState(pos))==1,"Custom growth callback advances on a real random tick");
        var drops=net.minecraft.world.level.block.Block.getDrops(block.getStateForAge(1),level,pos,null,player,new ItemStack(Items.IRON_HOE));check(drops.stream().noneMatch(s->s.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("kubejs:v11_fast_crop_seeds")))),"Disabled seed drops are absent from native mature loot");
        var inv=new StorageInventory(1,2,4096,()->{});check(!net.neoforged.neoforge.items.IItemHandlerModifiable.class.isAssignableFrom(inv.getClass()),"Automation cannot replace logical totals through a modifiable stack projection");inv.insertItem(0,new ItemStack(Items.ENDER_PEARL,16),false);inv.insertItem(0,new ItemStack(Items.ENDER_PEARL,16),false);check(inv.count(0)==32&&inv.extractItem(0,100,true).getCount()==16,"Sixteen-stack items grow inside storage and extract at native limit");
        var large=new ItemStack(Items.IRON_INGOT,99);large.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,99);inv.insertItem(1,large,false);var copy=new StorageInventory(1,2,4096,()->{});copy.load(level.registryAccess(),inv.save(level.registryAccess()));check(copy.extractItem(1,4096,false).getCount()==99,"Legal 99-stack component survives persistence and extraction");
        var dropPos=new BlockPos(20,90,20);level.setBlock(dropPos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("kubejs:v11_store")).defaultBlockState(),3);var broken=(StorageAttachment)((dev.latvian.mods.kubejs.block.entity.KubeBlockEntity)level.getBlockEntity(dropPos)).attachments.get("inventory");for(int i=0;i<64;i++)broken.inventory.insertItem(0,new ItemStack(Items.IRON_INGOT,64),false);level.destroyBlock(dropPos,true,player);
        int dropped=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(dropPos).inflate(2)).stream().filter(e->e.getItem().is(Items.IRON_INGOT)).mapToInt(e->e.getItem().getCount()).sum();check(dropped==4096,"Destroying a real block drops every item from a full logical slot");
        level.setChunkForced(FAR.getX()>>4,FAR.getZ()>>4,true);level.setBlock(FAR,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("kubejs:v11_store")).defaultBlockState(),3);farStorage=(StorageAttachment)((dev.latvian.mods.kubejs.block.entity.KubeBlockEntity)level.getBlockEntity(FAR)).attachments.get("inventory");for(int i=0;i<64;i++)farStorage.inventory.insertItem(107,new ItemStack(Items.IRON_INGOT,64),false);level.setChunkForced(FAR.getX()>>4,FAR.getZ()>>4,false);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyContentLayout")||phase==99)return;var mc=Minecraft.getInstance();ticks++;
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>90){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.player!=null&&ticks>100){forms();task=new CompletableFuture<>();mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var pos=new BlockPos(10,90,10);for(int x=-2;x<=4;x++)for(int z=-2;z<=4;z++)level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("kubejs:v11_store")).defaultBlockState(),3);var storage=(StorageAttachment)((dev.latvian.mods.kubejs.block.entity.KubeBlockEntity)level.getBlockEntity(pos)).attachments.get("inventory");storage.inventory.insertItem(107,new ItemStack(Items.IRON_INGOT,64),false);cropAndStackLimits(level,player);player.setInvulnerable(true);player.connection.teleport(10.5,90,12.5,0,0);player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,p)->new StorageMenu(id,inv,storage),net.minecraft.network.chat.Component.literal("滚动库存验证")),buf->{buf.writeBlockPos(pos);buf.writeVarInt(storage.rows);buf.writeVarInt(storage.columns);buf.writeVarInt(storage.capacity);});task.complete(null);}catch(Throwable e){task.completeExceptionally(e);}});phase=2;ticks=0;return;}
            if(phase==2&&task.isDone()&&ticks>40){task.get();check(mc.screen instanceof StorageScreen,"Storage screen registered");var screen=(StorageScreen)mc.screen;screen.init(mc,320,200);for(int i=0;i<9;i++)screen.mouseScrolled(screen.getGuiLeft()+8,screen.getGuiTop()+20,0,-1);var menu=screen.getMenu();check(!menu.slots.getFirst().isActive()&&menu.slots.get(107).isActive(),"Scrolling hides first row and reveals last row");
                var method=net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredMethod("findSlot",double.class,double.class);method.setAccessible(true);var last=menu.slots.get(107);var found=(net.minecraft.world.inventory.Slot)method.invoke(screen,(double)screen.getGuiLeft()+last.x+8,(double)screen.getGuiTop()+last.y+8);check(found!=null&&found.index==107,"Scrolled mouse hit maps to exact final slot");
                mc.gameMode.handleInventoryMouseClick(menu.containerId,107,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);phase=3;ticks=0;return;}
            if(phase==3&&ticks>20){check(mc.player.containerMenu.getCarried().getCount()==64,"Actual menu click packet extracts a legal stack");phase=4;ticks=0;return;}
            if(phase==4&&ticks>20){unloadTicks+=ticks;if(unloadTicks>800)throw new AssertionError("Far chunk did not unload");unloaded=new CompletableFuture<>();mc.getSingleplayerServer().execute(()->{try{if(!farStorage.entity.isRemoved()){unloaded.complete(false);return;}var level=mc.getSingleplayerServer().overworld();level.getChunkAt(FAR);var restored=(StorageAttachment)((dev.latvian.mods.kubejs.block.entity.KubeBlockEntity)level.getBlockEntity(FAR)).attachments.get("inventory");check(restored.entity!=farStorage.entity&&restored.inventory.count(107)==4096,"Natural chunk unload and reload preserve last logical slot");unloaded.complete(true);}catch(Throwable e){unloaded.completeExceptionally(e);}});phase=5;ticks=0;return;}
            if(phase==5&&unloaded.isDone()){if(!unloaded.get()){phase=4;ticks=0;return;}Files.writeString(root().resolve("content-layout.json"),ProjectStore.JSON.toJson(Map.of("passed",true,"checks",checks,"smallWindow","320x200","chunkUnloadReload",true,"actualDropCount",4096)));mc.stop();phase=99;}
        }catch(Throwable e){try{Files.writeString(root().resolve("content-layout-failure.json"),ProjectStore.JSON.toJson(Map.of("phase",phase,"error",e.toString())));}catch(Exception ignored){}e.printStackTrace();mc.stop();phase=99;}
    }
}
