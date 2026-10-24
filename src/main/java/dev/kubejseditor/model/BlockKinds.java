package dev.kubejseditor.model;

import java.util.*;
import dev.kubejseditor.model.EditorProject.ItemDraft;

/** Types and behaviour are implemented by the installed KubeJS builders, not emulated blocks. */
public final class BlockKinds {
    public static final Map<String,String> NAMES=new LinkedHashMap<>();
    public static final List<String> SOUNDS=List.of("stone","wood","glass","metal","gravel","grass","sand","wool","none");
    static{NAMES.put("basic","普通方块");NAMES.put("slab","台阶");NAMES.put("stairs","楼梯");NAMES.put("fence","栅栏");NAMES.put("fence_gate","栅栏门");NAMES.put("wall","墙");NAMES.put("door","门");NAMES.put("trapdoor","活板门");NAMES.put("button","按钮");NAMES.put("pressure_plate","压力板");NAMES.put("carpet","地毯");NAMES.put("pillar","柱状方块");NAMES.put("crop","耕地作物");NAMES.put("storage","存储方块");}
    public static BlockOptions options(ItemDraft item){return item.block==null?new BlockOptions():item.block;}
    public static void initialize(ItemDraft item,String type){
        if(!NAMES.containsKey(type))throw new IllegalArgumentException("不支持的方块类型");
        item.block=new BlockOptions();var b=item.block;b.type=type;
        if(!Set.of("basic","wall").contains(type)){b.sound="wood";item.texture="minecraft:block/oak_planks";}
        if(Set.of("door","trapdoor").contains(type)){b.render="cutout";b.opaque=false;}
        if(type.equals("door")){item.texture="minecraft:block/oak_door_bottom";b.topTexture="minecraft:block/oak_door_top";b.itemTexture="minecraft:item/oak_door";}
        if(type.equals("trapdoor"))item.texture="minecraft:block/oak_trapdoor";
        if(type.equals("carpet")){item.texture="minecraft:block/white_wool";b.sound="wool";b.opaque=false;}
        if(type.equals("crop")){b.sound="grass";b.render="cutout";b.collision=false;b.opaque=false;b.dropSelf=false;item.texture="minecraft:block/wheat_stage0";item.name="新作物";item.settings.addProperty("hardness",0);item.settings.addProperty("resistance",0);}
        if(type.equals("button")||type.equals("pressure_plate")){b.collision=false;b.opaque=false;}
    }
    public static List<String> textures(ItemDraft item){
        List<String> result=new ArrayList<>();if(item.texture!=null&&!item.texture.isBlank())result.add(item.texture);
        if(ItemKinds.kind(item).equals("block")&&options(item).type.equals("door")){
            var b=options(item);if(!b.topTexture.isBlank())result.add(b.topTexture);if(!b.itemTexture.isBlank())result.add(b.itemTexture);
        }
        if(ItemKinds.kind(item).equals("block")&&options(item).type.equals("crop")){result.addAll(options(item).crop.textures);result.add(options(item).crop.seedTexture);}
        return List.copyOf(result);
    }
    public static void validate(ItemDraft item){
        var b=options(item);
        if(b.type.equals("crop")){if(b.crop==null)throw new IllegalArgumentException("缺少作物设置");b.crop.validate();}
        if(b.type.equals("storage")){if(b.storage==null)throw new IllegalArgumentException("缺少存储设置");b.storage.validate();}
        if(!NAMES.containsKey(b.type)||!SOUNDS.contains(b.sound)||!List.of("solid","cutout","cutout_mipped","translucent").contains(b.render))throw new IllegalArgumentException("无效方块类型、声音或渲染方式");
        if(!List.of("none","pickaxe","axe","shovel","hoe").contains(b.miningTool)||!List.of("none","stone","iron","diamond").contains(b.miningLevel))throw new IllegalArgumentException("无效挖掘工具或等级");
        if(b.requiresTool&&b.miningTool.equals("none"))throw new IllegalArgumentException("需要正确工具时，请先选择挖掘工具");
        if(!List.of("oak","stone","iron").contains(b.behaviour))throw new IllegalArgumentException("无效交互材质");
        if(Set.of("button","pressure_plate").contains(b.type)&&(b.collision||b.behaviour.equals("iron")))throw new IllegalArgumentException("按钮／压力板使用原生无碰撞形状，触发类型请选择木质或石质");
        if(Set.of("door","trapdoor").contains(b.type)&&b.behaviour.equals("stone"))throw new IllegalArgumentException("门请选择手动与红石，或仅红石");
        if(!Double.isFinite(b.slipperiness)||b.slipperiness<0||b.slipperiness>1||!Double.isFinite(b.speedFactor)||b.speedFactor<0||!Double.isFinite(b.jumpFactor)||b.jumpFactor<0)throw new IllegalArgumentException("滑度须为 0～1；移动与跳跃倍率须为非负有限数");
        if(b.pressedTicks<1||b.pressedTicks>12000)throw new IllegalArgumentException("按钮／压力板保持时间须为 1～12000 tick");
        if(b.type.equals("door")){EditorProject.requireId(b.topTexture);EditorProject.requireId(b.itemTexture);}
    }
    private BlockKinds(){}
}
