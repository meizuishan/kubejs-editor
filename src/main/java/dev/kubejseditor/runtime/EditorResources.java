package dev.kubejseditor.runtime;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.storage.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class EditorResources {
    private static final String BASELINES="kubejs/config/kubejs_editor/resource-baselines.json";
    public static void stageTexture(Path root,EditorProject project,EditorProject.ItemDraft item,byte[] png)throws Exception{
        stageTexture(root,project,item,png,"main");
    }
    public static void stageTexture(Path root,EditorProject project,EditorProject.ItemDraft item,byte[] png,String role)throws Exception{
        EditorProject.requireId(item.id);
        boolean door=dev.kubejseditor.model.ItemKinds.kind(item).equals("block")&&item.block.type.equals("door");
        boolean crop=dev.kubejseditor.model.RegistrationMembers.crop(item);
        boolean stage=crop&&role.matches("stage_[0-9]+")&&Integer.parseInt(role.substring(6))<item.block.crop.stages;
        if(!role.equals("main")&&!(door&&Set.of("top","inventory").contains(role))&&!(crop&&role.equals("seed"))&&!stage)throw new IllegalArgumentException("无效贴图用途");
        if(png.length>16*1024*1024||png.length<8||png[0]!=(byte)137||png[1]!=80||png[2]!=78||png[3]!=71)throw new IllegalArgumentException("请选择有效 PNG（最多 16 MiB）");
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));
        String staged="kubejs/config/kubejs_editor/staged_assets/"+hash+".png";
        FileTransaction.atomicWrite(FileTransaction.checkedPath(root,staged),png);
        String[] id=item.id.split(":",2);String suffix=role.equals("main")?"":"_"+role;String output="kubejs/assets/kubejs_editor/textures/item/"+id[0]+"/"+id[1]+suffix+".png";
        project.assets.put(output,staged);String location="kubejs_editor:item/"+id[0]+"/"+id[1]+suffix;
        if(stage)item.block.crop.textures.set(Integer.parseInt(role.substring(6)),location);else if(role.equals("seed"))item.block.crop.seedTexture=location;else if(role.equals("main"))item.texture=location;else if(role.equals("top"))item.block.topTexture=location;else item.block.itemTexture=location;
    }
    /** Captures original resource overrides once; removing a managed override restores that preimage. */
    public static Set<String> augment(Path root,EditorProject project,Map<String,byte[]> files)throws IOException{
        return augment(root,project,files,Set.of());
    }
    public static Set<String> augment(Path root,EditorProject project,Map<String,byte[]> files,Set<String> retained)throws IOException{
        Map<String,byte[]> dynamic=new LinkedHashMap<>();
        Set<String> restored=new LinkedHashSet<>();
        for(var item:project.items.values())if(item.register&&dev.kubejseditor.model.ItemKinds.kind(item).equals("block")&&dev.kubejseditor.model.BlockKinds.options(item).type.equals("door"))dynamic.put(dev.kubejseditor.script.BlockRegistration.itemModelPath(item),dev.kubejseditor.script.BlockRegistration.itemModel(item));
        for(var asset:project.assets.entrySet()){
            String dest=asset.getKey();if(!dest.startsWith("kubejs/assets/kubejs_editor/textures/")||!dest.endsWith(".png"))throw new IOException("草稿包含无效资源路径");
            if(!FileTransaction.checkedPath(root,dest).startsWith(root.toAbsolutePath().normalize().resolve("kubejs/assets/kubejs_editor/textures")))throw new IOException("材质目标超出资源目录");
            String location="kubejs_editor:"+dest.substring("kubejs/assets/kubejs_editor/textures/".length(),dest.length()-4);
            if(project.items.values().stream().noneMatch(i->dev.kubejseditor.model.BlockKinds.textures(i).contains(location)))continue;
            if(!asset.getValue().startsWith("kubejs/config/kubejs_editor/staged_assets/"))throw new IOException("无效材质暂存位置");
            if(!FileTransaction.checkedPath(root,asset.getValue()).startsWith(root.toAbsolutePath().normalize().resolve("kubejs/config/kubejs_editor/staged_assets")))throw new IOException("无效材质暂存位置");
            dynamic.put(dest,Files.readAllBytes(FileTransaction.checkedPath(root,asset.getValue())));
        }
        for(var item:project.items.values())if(!item.register&&item.texture!=null&&!item.texture.isBlank()){
            ResourceLocation id=ResourceLocation.parse(item.id);String path="models/item/"+id.getPath()+".json";
            var original=Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),path));
            JsonObject model;
            if(original.isPresent())try(Reader reader=original.get().openAsReader()){model=JsonParser.parseReader(reader).getAsJsonObject();}
            else throw new IOException("未找到物品模型："+item.id);
            if(!model.has("textures"))model.add("textures",new JsonObject());
            JsonObject textures=model.getAsJsonObject("textures");String parent=model.has("parent")?model.get("parent").getAsString():"";
            if(!textures.has("layer0")&&!Set.of("minecraft:item/generated","minecraft:item/handheld","item/generated","item/handheld").contains(parent))throw new IOException("物品使用自定义模型，当前贴图表单不支持："+item.id);
            EditorProject.requireId(item.texture);textures.addProperty("layer0",item.texture);
            dynamic.put("kubejs/assets/"+id.getNamespace()+"/"+path,ProjectStore.JSON.toJson(model).getBytes(StandardCharsets.UTF_8));
        }
        Path baselineFile=root.resolve(BASELINES);JsonObject baselines=Files.exists(baselineFile)?JsonParser.parseString(Files.readString(baselineFile)).getAsJsonObject():new JsonObject();
        for(var output:dynamic.entrySet())if(!baselines.has(output.getKey())){
            Path path=FileTransaction.checkedPath(root,output.getKey());if(Files.exists(path))baselines.addProperty(output.getKey(),Base64.getEncoder().encodeToString(Files.readAllBytes(path)));else baselines.add(output.getKey(),JsonNull.INSTANCE);
        }
        for(String old:new ArrayList<>(baselines.keySet()))if(!dynamic.containsKey(old)&&!retained.contains(old)){
            if(!old.startsWith("kubejs/assets/"))throw new IOException("资源基线清单损坏");
            if(!FileTransaction.checkedPath(root,old).startsWith(root.toAbsolutePath().normalize().resolve("kubejs/assets")))throw new IOException("资源基线清单路径损坏");
            dynamic.put(old,baselines.get(old).isJsonNull()?null:Base64.getDecoder().decode(baselines.get(old).getAsString()));baselines.remove(old);
            restored.add(old);
        }
        for(String path:retained)if(!dynamic.containsKey(path))files.remove(path);
        files.putAll(dynamic);if(!baselines.isEmpty()||Files.exists(baselineFile))files.put(BASELINES,ProjectStore.JSON.toJson(baselines).getBytes(StandardCharsets.UTF_8));
        return Set.copyOf(restored);
    }
}
