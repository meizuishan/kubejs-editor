package dev.kubejseditor.model;

import java.util.*;
import dev.kubejseditor.model.EditorProject.*;

public final class ItemKinds {
    public static final Map<String,String> NAMES=new LinkedHashMap<>();
    static{NAMES.put("basic","普通材料");NAMES.put("food","食物");NAMES.put("pickaxe","镐");NAMES.put("axe","斧");NAMES.put("shovel","锹");NAMES.put("hoe","锄");NAMES.put("sword","剑");NAMES.put("helmet","头盔");NAMES.put("chestplate","胸甲");NAMES.put("leggings","护腿");NAMES.put("boots","靴子");NAMES.put("block","方块");}
    public static String kind(ItemDraft d){return "basic".equals(d.kind)&&d.food?"food":d.kind;}
    public static boolean tool(String k){return Set.of("pickaxe","axe","shovel","hoe","sword").contains(k);}
    public static boolean armor(String k){return Set.of("helmet","chestplate","leggings","boots").contains(k);}
    public static void initialize(ItemDraft d,String kind){
        if(!NAMES.containsKey(kind))throw new IllegalArgumentException("不支持的物品分类");d.kind=kind;d.food=kind.equals("food");d.settings=new com.google.gson.JsonObject();
        d.properties=new com.google.gson.JsonObject();d.attributes.clear();d.replaceAttributes=false;d.texture=kind.equals("block")?"minecraft:block/stone":tool(kind)?"minecraft:item/iron_"+kind:armor(kind)?"minecraft:item/iron_"+kind:"minecraft:item/iron_ingot";
        d.properties.addProperty("maxStackSize",tool(kind)||armor(kind)?1:64);
        if(d.food){d.properties.addProperty("nutrition",4);d.properties.addProperty("saturation",.3);}
        if(tool(kind)){d.properties.addProperty("maxDamage",250);d.settings.addProperty("damage",kind.equals("sword")?6:kind.equals("axe")?9:4);d.settings.addProperty("attackSpeed",kind.equals("sword")?1.6:kind.equals("axe")?.9:1.2);d.settings.addProperty("miningSpeed",6);}
        if(armor(kind)){d.properties.addProperty("maxDamage",switch(kind){case "helmet"->165;case "chestplate"->240;case "leggings"->225;default->195;});d.settings.addProperty("armor",switch(kind){case "chestplate"->6;case "leggings"->5;default->2;});d.settings.addProperty("toughness",0);d.settings.addProperty("knockbackResistance",0);}
        if(kind.equals("block")){d.settings.addProperty("hardness",1.5);d.settings.addProperty("resistance",6);d.settings.addProperty("lightLevel",0);}
    }
    public static double value(ItemDraft d,String key,double fallback){return d.settings!=null&&d.settings.has(key)?d.settings.get(key).getAsDouble():fallback;}
    public static List<AttributeDraft> attributes(ItemDraft d){
        List<AttributeDraft> out=new ArrayList<>(d.attributes);if(!d.register||!armor(kind(d)))return out;
        String slot=switch(kind(d)){case "helmet"->"head";case "chestplate"->"chest";case "leggings"->"legs";default->"feet";};
        for(var key:List.of("armor","toughness","knockbackResistance")){var a=new AttributeDraft();a.attribute="minecraft:generic."+switch(key){case "toughness"->"armor_toughness";case "knockbackResistance"->"knockback_resistance";default->"armor";};a.amount=value(d,key,0);a.id=d.id+"/"+key.toLowerCase(Locale.ROOT);a.slot=slot;out.add(a);}return out;
    }
    public static void validate(ItemDraft d){
        String k=kind(d);if(!NAMES.containsKey(k))throw new IllegalArgumentException("未知物品分类："+k);
        if(d.settings==null)throw new IllegalArgumentException("物品分类参数缺失");
        if(k.equals("block"))BlockKinds.validate(d);
        Set<String> allowed=tool(k)?Set.of("damage","attackSpeed","miningSpeed"):armor(k)?Set.of("armor","toughness","knockbackResistance"):k.equals("block")?Set.of("hardness","resistance","lightLevel"):Set.of();
        for(var e:d.settings.entrySet()){double n=e.getValue().getAsDouble();if(!allowed.contains(e.getKey())||!Double.isFinite(n)||n<0)throw new IllegalArgumentException("无效分类参数："+e.getKey());if(e.getKey().equals("lightLevel")&&(n>15||n!=Math.rint(n)))throw new IllegalArgumentException("亮度须为 0～15 的整数");if(e.getKey().equals("knockbackResistance")&&n>1)throw new IllegalArgumentException("击退抗性须为 0～1");if(e.getKey().equals("attackSpeed")&&n<=0)throw new IllegalArgumentException("攻击速度必须大于零");}
        if(d.register&&(tool(k)||armor(k))&&d.properties.has("maxDamage")&&d.properties.get("maxDamage").getAsDouble()<1)throw new IllegalArgumentException("工具、武器及防具耐久必须大于零");
        if(d.register&&(tool(k)||armor(k))&&d.properties.has("maxStackSize")&&d.properties.get("maxStackSize").getAsInt()!=1)throw new IllegalArgumentException("工具、武器及防具只能堆叠 1 个");
    }
}
