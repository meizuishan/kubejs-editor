package dev.kubejseditor;

import dev.kubejseditor.model.*;
import dev.kubejseditor.script.*;
import dev.kubejseditor.storage.*;
import java.nio.charset.StandardCharsets;

public final class BlockVerification {
    public static int run()throws Exception{
        int checks=0;
        for(String type:BlockKinds.NAMES.keySet()){
            var item=new EditorProject.ItemDraft();ItemKinds.initialize(item,"block");BlockKinds.initialize(item,type);item.id="kubejs:block_"+type;
            var p=new EditorProject();p.items.put(item.id,item);String js=new String(KjsGenerator.generate(p).files().get(WorkspaceState.GENERATED.get(1)),StandardCharsets.UTF_8);
            new dev.latvian.mods.rhino.Parser(new dev.latvian.mods.rhino.ContextFactory().enter()).parse(js,type,1);checks++;
            if(!type.equals("basic")&&!js.contains(",\""+(type.equals("storage")?"kubejs_editor:storage":type)+"\")")&&!(type.equals("crop")&&js.contains(",'crop')")))throw new AssertionError("Missing builder type: "+type);checks++;
            if(!WorkspaceState.copy(item,EditorProject.ItemDraft.class).block.type.equals(type))throw new AssertionError("Subtype lost on persistence");checks++;
            if(type.equals("door")&&(!js.contains("textures({top:")||!new String(KjsGenerator.generate(p).files().get(BlockRegistration.itemModelPath(item)),StandardCharsets.UTF_8).contains("minecraft:item/oak_door")))throw new AssertionError("Door requires top, bottom and inventory textures");
        }
        var item=new EditorProject.ItemDraft();ItemKinds.initialize(item,"block");item.id="kubejs:block_options";item.block.requiresTool=true;item.block.miningTool="pickaxe";item.block.miningLevel="iron";item.block.collision=false;item.block.unbreakable=true;item.block.dropSelf=false;item.block.sound="none";
        var p=new EditorProject();p.items.put(item.id,item);String js=new String(KjsGenerator.generate(p).files().get(WorkspaceState.GENERATED.get(1)),StandardCharsets.UTF_8);
        for(String part:java.util.List.of(".requiresTool(true)","mineable/pickaxe","needs_iron_tool",".noCollision()",".noDrops()",".unbreakable()",".noSoundType()")){if(!js.contains(part))throw new AssertionError("Missing block option: "+part);checks++;}
        item.block.pressedTicks=0;try{BlockKinds.validate(item);throw new AssertionError("Invalid press duration accepted");}catch(IllegalArgumentException expected){checks++;}
        item.block.pressedTicks=20;item.block.miningTool="none";try{BlockKinds.validate(item);throw new AssertionError("Required tool without tool tag accepted");}catch(IllegalArgumentException expected){checks++;}
        var old=ProjectStore.JSON.fromJson("{id:'kubejs:old',kind:'block',register:true}",EditorProject.ItemDraft.class);if(!old.block.type.equals("basic")||!old.block.collision)throw new AssertionError("Old drafts must preserve basic block defaults");checks++;
        var legacyProject=new EditorProject();legacyProject.items.put(old.id,old);String legacy=new String(KjsGenerator.generate(legacyProject).files().get(WorkspaceState.GENERATED.get(1)),StandardCharsets.UTF_8);
        if(!legacy.contains(".lightLevel(0.0).stoneSoundType().requiresTool(false).item(item => {"))throw new AssertionError("Unchanged old basic block must keep its historical startup bytes");checks++;
        BlockKinds.initialize(item,"door");item.texture="kubejs_editor:item/shared";item.block.topTexture="kubejs_editor:item/shared_top";item.block.itemTexture="kubejs_editor:item/shared_inventory";
        p.assets.put("kubejs/assets/kubejs_editor/textures/item/shared_top.png","staged.png");var other=new EditorProject.ItemDraft();ItemKinds.initialize(other,"block");BlockKinds.initialize(other,"door");other.id="kubejs:other";other.block.topTexture=item.block.topTexture;p.items.put(other.id,other);
        new PendingOperation("item",item.id,"注册").cancel(p);if(p.assets.isEmpty())throw new AssertionError("Deleting one door must preserve another door's shared top texture");checks++;
        if(dev.kubejseditor.config.ConfigValueTypes.resolve(null,Object.class,4.0,4.0)!=Double.class)throw new AssertionError("Custom-validator Double misidentified");checks++;
        if(dev.kubejseditor.config.ConfigValueTypes.resolve(null,Object.class,200,200)!=Integer.class)throw new AssertionError("Custom-validator Integer misidentified");checks++;
        if(dev.kubejseditor.config.ConfigValueTypes.resolve(int.class,Object.class,4,4)!=Integer.class)throw new AssertionError("Primitive config type not boxed");checks++;
        for(String bad:java.util.List.of("NaN","Infinity","-Infinity")){try{dev.kubejseditor.config.ConfigValueTypes.scalar(Double.class,bad);throw new AssertionError("Nonfinite config value accepted");}catch(IllegalArgumentException expected){checks++;}}
        try{dev.kubejseditor.config.ConfigValueTypes.scalar(Integer.class,"1.5");throw new AssertionError("Integer config silently truncated");}catch(IllegalArgumentException expected){checks++;}
        var doorProject=new EditorProject();var door=new EditorProject.ItemDraft();ItemKinds.initialize(door,"block");BlockKinds.initialize(door,"door");door.id="kubejs:existing_model";doorProject.items.put(door.id,door);
        var root=java.nio.file.Files.createTempDirectory("kjs-door-resource-");String modelPath=BlockRegistration.itemModelPath(door);var model=root.resolve(modelPath);java.nio.file.Files.createDirectories(model.getParent());byte[] original="{\"parent\":\"original:model\"}".getBytes(StandardCharsets.UTF_8);java.nio.file.Files.write(model,original);
        var files=new java.util.LinkedHashMap<>(KjsGenerator.generate(doorProject).files());dev.kubejseditor.runtime.EditorResources.augment(root,doorProject,files);FileTransaction.apply(FileTransaction.review(root,files,true,true));
        if(java.util.Arrays.equals(original,java.nio.file.Files.readAllBytes(model)))throw new AssertionError("Door inventory override not written");checks++;
        files=new java.util.LinkedHashMap<>();var restored=dev.kubejseditor.runtime.EditorResources.augment(root,new EditorProject(),files);
        if(!restored.contains(modelPath)||!java.util.Arrays.equals(original,files.get(modelPath)))throw new AssertionError("Door removal must restore existing model and release ownership");checks++;
        FileTransaction.apply(FileTransaction.review(root,files,true,true));files=new java.util.LinkedHashMap<>();dev.kubejseditor.runtime.EditorResources.augment(root,new EditorProject(),files);
        if(files.containsKey(modelPath)||!java.util.Arrays.equals(original,java.nio.file.Files.readAllBytes(model)))throw new AssertionError("A later apply must not delete a restored original resource");checks++;
        return checks;
    }
}
