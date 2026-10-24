package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.config.CreateConfigs;
import dev.kubejseditor.model.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Native registration/interaction and configuration-consumer verification, in an isolated world. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientBlocksVerification {
    private static int phase,ticks,checks;private static WorkspaceScreen screen;private static CompletableFuture<List<String>> interaction;
    private static BlockPos buttonPos;private static String shot;private static int shotDelay;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static Object call(String name,Class<?>[] types,Object... args)throws Exception{for(Class<?> c=WorkspaceScreen.class;c!=null;c=c.getSuperclass())try{var m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(screen,args);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
    private static void click(String text){var button=Minecraft.getInstance().screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(text)).map(c->(Button)c).findFirst().orElseThrow();check(button.active,"Active button "+text);button.onPress();}
    private static String id(String type){return "kubejs:v10_"+type;}
    private static Block block(String type){return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id(type)));}
    private static void shot(String name){shot=name;shotDelay=8;}
    private static void audit()throws Exception{
        List<Map<String,Object>> rows=new ArrayList<>();var visible=CreateConfigs.list();
        for(var type:ModConfig.Type.values())for(ModConfig config:ModConfigs.getConfigSet(type))if(config.getLoadedConfig()!=null&&config.getSpec() instanceof ModConfigSpec spec)walk(config,spec.getSpec(),new ArrayList<>(),visible,rows);
        Files.writeString(root().resolve("config-audit.json"),ProjectStore.JSON.toJson(rows));
    }
    private static void walk(ModConfig config,com.electronwill.nightconfig.core.UnmodifiableConfig tree,List<String> path,List<CreateConfigs.Entry> visible,List<Map<String,Object>> rows){
        for(var value:tree.valueMap().entrySet()){
            var next=new ArrayList<>(path);next.add(value.getKey());
            if(value.getValue() instanceof ModConfigSpec.ValueSpec spec){
                var e=new CreateConfigs.Entry(config,config.getFullPath().toAbsolutePath().normalize(),next,spec,config.getLoadedConfig().config().get(next));
                Map<String,Object> row=new LinkedHashMap<>();row.put("mod",config.getModId());row.put("file",root().toAbsolutePath().normalize().relativize(e.file()).toString());row.put("scope",typeName(config));row.put("key",String.join(".",next));row.put("current",e.text(e.current()));row.put("default",e.text(spec.getDefault()));row.put("type",e.valueType().getName());row.put("currentType",e.current()==null?"null":e.current().getClass().getName());row.put("editable",e.editable());row.put("visible",visible.stream().anyMatch(v->v.config()==config&&v.path().equals(next)));row.put("restart",spec.restartType().name());
                try{Object parsed=e.parse(e.text(e.current()));row.put("roundTrip",spec.test(parsed));}catch(Exception error){row.put("roundTrip",false);row.put("reason",error.getMessage());}rows.add(row);
            }else if(value.getValue() instanceof com.electronwill.nightconfig.core.UnmodifiableConfig nested)walk(config,nested,next,visible,rows);
        }
    }
    private static String typeName(ModConfig config){return config.getType().name();}
    private static void finish(int stage)throws Exception{Files.writeString(root().resolve("blocks-stage"+stage+".json"),"{\"passed\":true,\"checks\":"+checks+"}");Minecraft.getInstance().stop();phase=99;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyBlocks")||phase==99)return;var mc=Minecraft.getInstance();ticks++;
        if(shot!=null&&--shotDelay<=0){Screenshot.grab(mc.gameDirectory,"blocks-"+shot+".png",mc.getMainRenderTarget(),c->{});shot=null;}
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>100){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&ticks>120){EditorRuntime.project();if(EditorRuntime.state().activation!=null){if(ticks>700)throw new AssertionError(EditorRuntime.activationError);return;}audit();screen=new WorkspaceScreen();mc.setScreen(screen);phase=Files.exists(root().resolve("blocks-stage1.json"))?20:2;ticks=0;return;}
            if(phase<2||ticks<25)return;if(ticks>1800)throw new AssertionError("Timeout phase "+phase);
            switch(phase){
                case 2 -> {for(var op:new ArrayList<>(EditorRuntime.operations()))EditorRuntime.cancel(op);click("+注册物品");click("方块");check(mc.screen instanceof ChoiceScreen,"Block category opens subtype picker before drafting");check(EditorRuntime.project().items.isEmpty(),"Opening subtype picker creates no item");shot("types");}
                case 3 -> {click("门");check(mc.screen==screen,"Subtype choice returns to workspace");var field=WorkspaceScreen.class.getDeclaredField("item");field.setAccessible(true);var item=(EditorProject.ItemDraft)field.get(screen);check(item.block.type.equals("door"),"Door choice selects native door form");shot("door-form");}
                case 4 -> {call("selectItem",new Class[]{EditorProject.ItemDraft.class},EditorRuntime.project().items.values().iterator().next());for(var op:new ArrayList<>(EditorRuntime.operations()))EditorRuntime.cancel(op);var f=WorkspaceScreen.class.getDeclaredField("item");f.setAccessible(true);f.set(screen,null);
                    for(String type:BlockKinds.NAMES.keySet().stream().filter(type->!Set.of("crop","storage").contains(type)).toList()){var item=new EditorProject.ItemDraft();ItemKinds.initialize(item,"block");BlockKinds.initialize(item,type);item.id=id(type);item.name="方块验证·"+BlockKinds.NAMES.get(type);item.settings.addProperty("hardness",2.75);item.settings.addProperty("resistance",8);item.settings.addProperty("lightLevel",3);if(type.equals("button"))item.block.pressedTicks=6;if(type.equals("basic")){item.block.requiresTool=true;item.block.miningTool="pickaxe";item.block.miningLevel="iron";item.block.slipperiness=.8;item.block.speedFactor=.75;item.block.jumpFactor=1.25;}EditorRuntime.project().items.put(item.id,item);}
                    var iron=WorkspaceState.copy(EditorRuntime.project().items.get(id("door")),EditorProject.ItemDraft.class);iron.id=id("iron_door");iron.block.behaviour="iron";EditorRuntime.project().items.put(iron.id,iron);
                    var transparent=WorkspaceState.copy(EditorRuntime.project().items.get(id("basic")),EditorProject.ItemDraft.class);transparent.id=id("glass");transparent.block.render="translucent";transparent.block.opaque=false;transparent.block.collision=false;transparent.block.sound="glass";transparent.block.dropSelf=false;EditorRuntime.project().items.put(transparent.id,transparent);
                    Map<String,String> changed=new LinkedHashMap<>();var entries=CreateConfigs.list();
                    for(var entry:entries){boolean stress=entry.config().getModId().equals("create")&&entry.path().contains("impact")&&entry.path().getLast().equals("mechanical_mixer");boolean speed=entry.config().getModId().equals("create")&&entry.path().getLast().equals("maxRotationSpeed");boolean vintage=entry.config().getModId().equals("createvintageneoforged")&&entry.path().getLast().equals("allowVibratingLeaves");if((stress||speed||vintage)&&entry.editable()){String value=stress?"13.0":speed?"320":String.valueOf(!Boolean.parseBoolean(entry.text(entry.current())));CreateConfigs.queue(entry,value);changed.put(entry.key(),value);}}
                    check(changed.size()>=2,"Editable native configuration fixtures selected");Files.writeString(root().resolve("changed-configs.json"),ProjectStore.JSON.toJson(changed));EditorRuntime.saveDraft();check(EditorRuntime.validationIssues().isEmpty(),"All block registrations and config values validate");var review=EditorRuntime.review();check(review.restartRequired(),"Block/config batch correctly requires restart");EditorRuntime.apply(review);finish(1);return;}
                case 20 -> {check(EditorRuntime.project().items.isEmpty()&&EditorRuntime.project().configs.isEmpty(),"Restart promotes registrations and configs");Map<String,Class<?>> expected=Map.ofEntries(Map.entry("slab",SlabBlock.class),Map.entry("stairs",StairBlock.class),Map.entry("fence",FenceBlock.class),Map.entry("fence_gate",FenceGateBlock.class),Map.entry("wall",WallBlock.class),Map.entry("door",DoorBlock.class),Map.entry("trapdoor",TrapDoorBlock.class),Map.entry("button",ButtonBlock.class),Map.entry("pressure_plate",PressurePlateBlock.class),Map.entry("carpet",CarpetBlock.class));for(String type:BlockKinds.NAMES.keySet().stream().filter(type->!Set.of("crop","storage").contains(type)).toList()){check(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(id(type))),"Registered block "+type);check(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id(type))) instanceof net.minecraft.world.item.BlockItem,"Placeable item "+type);if(expected.containsKey(type))check(expected.get(type).isInstance(block(type)),"Real native block class "+type);check(block(type).getName().getString().equals("方块验证·"+BlockKinds.NAMES.get(type)),"Chinese name "+type);}
                    check(block("pillar").defaultBlockState().hasProperty(BlockStateProperties.AXIS),"KubeJS native pillar exposes axis placement state");
                    check(block("basic").defaultBlockState().requiresCorrectToolForDrops()&&Math.abs(block("basic").getFriction()-.8)<.001&&Math.abs(block("basic").getSpeedFactor()-.75)<.001&&Math.abs(block("basic").getJumpFactor()-1.25)<.001,"Native material/mining/movement properties apply");
                    check(!new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WOODEN_PICKAXE).isCorrectToolForDrops(block("basic").defaultBlockState())&&new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE).isCorrectToolForDrops(block("basic").defaultBlockState()),"Generated mining tags enforce selected iron tool tier");
                    var changed=JsonParser.parseString(Files.readString(root().resolve("changed-configs.json"))).getAsJsonObject();boolean editedStress=changed.keySet().stream().anyMatch(k->k.endsWith("impact.mechanical_mixer"));var impact=Class.forName("com.simibubi.create.api.stress.BlockStressValues").getMethod("getImpact",Block.class).invoke(null,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:mechanical_mixer")));check(((Number)impact).doubleValue()==(editedStress?13:4),"Native Create stress consumer matches whether the value was editable");
                    Object server=Class.forName("com.simibubi.create.infrastructure.config.AllConfigs").getMethod("server").invoke(null);Object kinetics=server.getClass().getField("kinetics").get(server);Object speed=kinetics.getClass().getField("maxRotationSpeed").get(kinetics);check(((Number)speed.getClass().getMethod("get").invoke(speed)).intValue()==320,"Create cached config wrapper reads new maximum speed");
                    for(var entry:CreateConfigs.list())if(changed.has(entry.key()))check(entry.text(entry.current()).equals(changed.get(entry.key()).getAsString()),"Loaded actual config equals submitted value: "+entry.key());
                    var resource=mc.getResourceManager().getResource(ResourceLocation.parse("kubejs:models/item/v10_door.json")).orElseThrow();try(var reader=resource.openAsReader()){check(JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("textures").get("layer0").getAsString().equals("minecraft:item/oak_door"),"Door inventory texture is generated correctly");}
                    interaction=new CompletableFuture<>();mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();var base=player.blockPosition().offset(4,4,0);List<String> ok=new ArrayList<>();buttonPos=base;level.setBlock(base.below(),Blocks.STONE.defaultBlockState(),3);var state=block("button").defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR);level.setBlock(base,state,3);((ButtonBlock)block("button")).press(state,level,base,player);if(!level.getBlockState(base).getValue(BlockStateProperties.POWERED))throw new AssertionError("Button did not emit power");ok.add("Button native interaction emits power");
                        for(String type:List.of("door","iron_door")){var pos=base.offset(type.equals("door")?2:4,0,0);var door=block(type);var lower=door.defaultBlockState();level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,lower,3);level.setBlock(pos.above(),lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER),3);level.getBlockState(pos).useWithoutItem(level,player,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));boolean opened=level.getBlockState(pos).getValue(BlockStateProperties.OPEN);if(opened!=type.equals("door"))throw new AssertionError("Door opening material mismatch: "+type);ok.add(type+" hand interaction follows selected behaviour");if(type.equals("iron_door")){level.setBlock(pos.east(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);if(!level.getBlockState(pos).getValue(BlockStateProperties.OPEN))throw new AssertionError("Iron door did not respond to redstone");ok.add("Iron door responds to native redstone updates");}}
                        var plate=base.offset(6,0,0);level.setBlock(plate.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(plate,block("pressure_plate").defaultBlockState(),3);var entity=new net.minecraft.world.entity.item.ItemEntity(level,plate.getX()+.5,plate.getY()+.1,plate.getZ()+.5,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));level.addFreshEntity(entity);level.getBlockState(plate).entityInside(level,plate,entity);if(!level.getBlockState(plate).getValue(BlockStateProperties.POWERED))throw new AssertionError("Wooden pressure plate did not detect dropped item");ok.add("Pressure plate detects real dropped item entity");
                        if(!Block.getDrops(block("glass").defaultBlockState(),level,base,null,player,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE)).isEmpty())throw new AssertionError("No-drops block produced loot");ok.add("No-drops option yields no native loot");interaction.complete(ok);}catch(Throwable e){interaction.completeExceptionally(e);}});}
                case 21 -> {if(!interaction.isDone())return;for(String message:interaction.get())check(true,message);check(!mc.getSingleplayerServer().overworld().getBlockState(buttonPos).getValue(BlockStateProperties.POWERED),"Button releases after configured native scheduled tick");call("selectItem",new Class[]{EditorProject.ItemDraft.class},EditorRuntime.state().applied.items.get(id("door")));mc.options.guiScale().set(3);mc.resizeDisplay();shot("door-scale3");}
                case 22 -> {check(WorkspaceJeiPlugin.runtime.getIngredientListOverlay().isListDisplayed(),"JEI visible with expanded block fields");finish(2);return;}
            }
            phase++;ticks=0;
        }catch(Throwable error){try{Files.writeString(root().resolve("blocks-failure.json"),"{\"phase\":"+phase+",\"error\":"+ProjectStore.JSON.toJson(error.toString())+"}");}catch(Exception ignored){}error.printStackTrace();mc.stop();phase=99;}
    }
}
