package dev.kubejseditor;

import dev.kubejseditor.model.*;
import dev.kubejseditor.storage.*;
import dev.kubejseditor.script.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ContentVerification {
    public static int run(){int checks=0;
        for(int rows:new int[]{1,3,9})for(int columns:new int[]{1,9,12})for(int capacity:new int[]{1,64,99,100,4096}){
            var options=ProjectStore.JSON.fromJson("{rows:"+rows+",columns:"+columns+",slotCapacity:"+capacity+"}",StorageOptions.class);options.validate();checks++;
        }
        for(String bad:List.of("{rows:0}","{rows:10}","{columns:13}","{slotCapacity:4097}","{rows:1.5}","{rows:'2'}","{slotCapacity:1e30}","{columns:null}")){
            try{ProjectStore.JSON.fromJson(bad,StorageOptions.class);throw new AssertionError("Invalid storage accepted: "+bad);}catch(com.google.gson.JsonParseException expected){checks++;}
        }
        var crop=new EditorProject.ItemDraft();ItemKinds.initialize(crop,"block");BlockKinds.initialize(crop,"crop");crop.id="kubejs:tomato";
        if(!RegistrationMembers.itemId(crop).equals("kubejs:tomato_seeds")||RegistrationMembers.of(crop).size()!=2)throw new AssertionError("Crop members");checks++;
        var p=new EditorProject();p.items.put(crop.id,crop);var files=KjsGenerator.generate(p).files();String script=new String(files.get(WorkspaceState.GENERATED.get(1)),StandardCharsets.UTF_8);
        if(script.contains(".noDrops()")||!script.contains(".age(7,")||!script.contains(".crop(\"minecraft:wheat\",1)"))throw new AssertionError("Crop behavior generation");checks++;
        String lang=new String(files.get("kubejs/assets/kubejs_editor/lang/zh_cn.json"),StandardCharsets.UTF_8);if(!lang.contains("item.kubejs.tomato_seeds"))throw new AssertionError("Seed localization");checks++;
        var copy=WorkspaceState.copy(crop,EditorProject.ItemDraft.class);copy.block.crop.stages=4;
        try{RegistrationMembers.sameStructure(crop,copy);throw new AssertionError("Unsafe crop shape change accepted");}catch(IllegalArgumentException expected){checks++;}
        var storage=new EditorProject.ItemDraft();ItemKinds.initialize(storage,"block");BlockKinds.initialize(storage,"storage");storage.id="kubejs:store";
        if(RegistrationMembers.of(storage).size()!=3)throw new AssertionError("Storage block entity not tracked");checks++;
        copy=WorkspaceState.copy(storage,EditorProject.ItemDraft.class);copy.block.storage.slotCapacity=4096;
        try{RegistrationMembers.sameStructure(storage,copy);throw new AssertionError("Unsafe storage change accepted");}catch(IllegalArgumentException expected){checks++;}
        return checks;
    }
}
