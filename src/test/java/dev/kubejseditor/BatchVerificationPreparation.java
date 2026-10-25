package dev.kubejseditor;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.script.KjsGenerator;
import dev.kubejseditor.storage.*;
import java.nio.file.*;
import java.util.*;

/** Creates only a new, named run-vN/client fixture; never accepts an instance path. */
public final class BatchVerificationPreparation {
    public static final String OWNED="kubejs_editor:batch_owned";
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]).toAbsolutePath().normalize(),workspace=Path.of("").toAbsolutePath().normalize();
        if(!root.getFileName().toString().equals("client")||!root.getParent().getFileName().toString().matches("run-v[0-9]+")||!root.getParent().getParent().equals(workspace)||Files.exists(root.resolve(WorkspaceState.FILE)))throw new IllegalArgumentException("只允许新的 run-vN/client 隔离目录");
        var applied=new EditorProject();var item=new EditorProject.ItemDraft();item.id=OWNED;item.name="";applied.items.put(item.id,item);
        var files=KjsGenerator.generate(applied).files();var state=new WorkspaceState();state.applied=applied;
        for(var entry:files.entrySet())if(!entry.getKey().equals(ProjectStore.FILE)&&entry.getValue()!=null){Path file=FileTransaction.checkedPath(root,entry.getKey());Files.createDirectories(file.getParent());Files.write(file,entry.getValue());state.managedHashes.put(entry.getKey(),WorkspaceState.hash(entry.getValue()));}
        Files.createDirectories(root.resolve(WorkspaceState.FILE).getParent());Files.write(root.resolve(WorkspaceState.FILE),WorkspaceState.bytes(state));ProjectStore.saveDraft(root,new EditorProject());
        byte[] registered=WorkspaceState.bytes(List.of(OWNED));Files.write(root.resolve("kubejs/config/kubejs_editor/applied-items.json"),registered);if(state.managedHashes.containsKey("kubejs/config/kubejs_editor/applied-items.json")){state.managedHashes.put("kubejs/config/kubejs_editor/applied-items.json",WorkspaceState.hash(registered));Files.write(root.resolve(WorkspaceState.FILE),WorkspaceState.bytes(state));}
        JsonObject sample=JsonParser.parseString("{type:'kubejs_editor_verification:generic',feedstock:{ingredient:{item:'minecraft:iron_ingot'},count:20480},bath:{ingredient:{fluid:'minecraft:water'},amount:250},product:{id:'minecraft:gold_ingot',count:2,components:{'minecraft:custom_data':{origin:'real'}}},extra:{key:'kubejs_editor_verification:red',quantity:4},settings:{work:40,enabled:true},conditions:{work:3,enabled:false},'neoforge:conditions':[{type:'kubejs_editor_verification:feature',enabled:true}]}").getAsJsonObject();
        for(String id:List.of("bbb:mirror","other:mirror","bbb:ambiguous","other:ambiguous","bbb:disabled")){
            var data=sample.deepCopy();if(id.equals("other:mirror"))data.getAsJsonObject("product").getAsJsonObject("components").getAsJsonObject("minecraft:custom_data").addProperty("origin","different");
            String[] parts=id.split(":",2);Path file=root.resolve("kubejs/data/"+parts[0]+"/recipe/"+parts[1]+".json");Files.createDirectories(file.getParent());Files.writeString(file,ProjectStore.JSON.toJson(data));
        }
        System.out.println("Prepared generic conversion, native conditions and one exactly generated owned item in "+root);
    }
}
