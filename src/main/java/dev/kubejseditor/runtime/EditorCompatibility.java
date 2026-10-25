package dev.kubejseditor.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import java.util.*;

/** Optional integrations are discovered from the current instance, never from the development pack. */
public final class EditorCompatibility {
    public static boolean hasCreate(){return ModList.get().isLoaded("create");}
    public static String requirement(String mod,String range){
        var container=ModList.get().getModContainerById(mod);
        if(container.isEmpty())return "缺少模板依赖："+mod;
        try{return org.apache.maven.artifact.versioning.VersionRange.createFromVersionSpec(range).containsVersion(container.get().getModInfo().getVersion())?"":"模板需要 "+mod+" "+range+"，当前为 "+container.get().getModInfo().getVersion();}
        catch(Exception e){throw new IllegalArgumentException("无效版本范围："+mod+" "+range,e);}
    }
    public static boolean hasRecipeType(String type){var id=ResourceLocation.tryParse(type);return id!=null&&BuiltInRegistries.RECIPE_SERIALIZER.containsKey(id);}
    public static String unavailable(String type){
        if(hasRecipeType(type))return "";
        String owner=ResourceLocation.parse(type).getNamespace();
        return ModList.get().isLoaded(owner)?"当前模组未注册此配方类型："+type:"缺少可选模组 "+owner+"，此配方暂不可编辑";
    }
    public static String report(){
        StringBuilder text=new StringBuilder("KubeJS Editor · 当前兼容环境\n\n");
        for(String id:List.of("kubejs_editor","minecraft","neoforge","kubejs","rhino","jei","create","kubejs_create","create_dragons_plus","createaddition","createdieselgenerators","create_enchantment_industry","createvintageneoforged"))
            text.append(id).append("：").append(ModList.get().getModContainerById(id).map(m->m.getModInfo().getVersion().toString()).orElse("未安装")).append('\n');
        text.append("\n必需：Minecraft 1.21.1、NeoForge 21.1.199+（21.1 系列）、KubeJS 2101.7.2-build.368+、JEI 19.25.0.322+（19 系列）。\nCreate 与附属为可选；未安装时相应入口不可用。Forge、Fabric 与其他 Minecraft 版本不能使用此 Jar。\n本机单人可写入；远程服务器仅草稿与导出。\n声明范围不等于逐版实测，组合与验证内容请参阅发布包的兼容性报告。\n\n最后一栏为强制兼容：通用读取配方与 JEI 布局；默认忽略加载条件并按原生 Codec 校验。缺少真实写入来源的纯展示保留只读。JEI 19.25 使用配方边框书本入口，19.27 起使用侧边按钮。");
        return text.toString();
    }
    private EditorCompatibility(){}
}
