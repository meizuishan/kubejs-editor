package dev.kubejseditor.client;

import dev.kubejseditor.config.*;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Friendly names first; exact keys remain in search and parameter details. */
public final class ConfigLabels {
    public static String name(CreateConfigs.Entry e){
        String lang=e.spec().getTranslationKey();
        if(lang!=null&&I18n.exists(lang)&&hasChinese(I18n.get(lang)))return I18n.get(lang);
        String leaf=e.path().getLast();String machine=machine(e.config().getModId(),leaf);
        if(e.path().contains("impact"))return "基础应力消耗 · "+machine;
        if(e.path().contains("capacity"))return "基础应力容量 · "+machine;
        return hasChinese(machine)?machine:ConfigNames.words(leaf);
    }
    private static String machine(String mod,String leaf){
        ResourceLocation id=ResourceLocation.tryParse(leaf.contains(":")?leaf:mod+":"+leaf);
        if(id!=null&&BuiltInRegistries.ITEM.containsKey(id)){String name=BuiltInRegistries.ITEM.get(id).getDescription().getString();if(hasChinese(name))return name;}
        return ConfigNames.words(leaf);
    }
    public static String category(CreateConfigs.Entry e){return String.join(" / ",e.path().subList(0,e.path().size()-1).stream().map(ConfigNames::words).filter(s->!s.isBlank()).toList());}
    public static String choice(CreateConfigs.Entry e){return ConfigNames.mod(e.config().getModId())+" · "+name(e)+" · "+value(e.current());}
    public static String value(Object value){return value instanceof Boolean b?b?"开启":"关闭":value instanceof Enum<?> v?ConfigNames.words(v.name()):String.valueOf(value);}
    public static String restart(CreateConfigs.Entry e){return switch(e.spec().restartType().name()){case "NONE"->"按模组要求刷新；本编辑器保存后提示重启以确保生效";case "WORLD"->"重新进入世界（本编辑器统一提示重启）";default->"重启游戏";};}
    public static String description(CreateConfigs.Entry e){
        return name(e)+"\n分类："+category(e)+"\n作用域："+ConfigNames.scope(e.config().getType().name())+"\n当前值："+value(e.current())+"\n默认值："+value(e.spec().getDefault())+"\n范围："+(e.spec().getRange()==null?"由模组校验":e.spec().getRange())+"\n生效要求："+restart(e)+"\n\n配置路径："+e.key()+"\n文件："+e.file()+"\n\n模组原始说明：\n"+Objects.toString(e.spec().getComment(),"无");
    }
    private static boolean hasChinese(String value){return value.codePoints().anyMatch(c->c>=0x3400&&c<=0x9fff);}
}
