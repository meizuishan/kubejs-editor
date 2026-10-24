package dev.kubejseditor.storage;

import dev.kubejseditor.model.EditorProject;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.IOException;
import java.util.*;

/** Last known active rules are separate from the editable, unapplied queue. */
public final class WorkspaceState {
    public static final String FILE="kubejs/config/kubejs_editor/state.json";
    public static final List<String> GENERATED=List.of("kubejs/server_scripts/kubejs_editor/recipes.js","kubejs/startup_scripts/kubejs_editor/items.js","kubejs/client_scripts/kubejs_editor/tooltips.js");
    public int schemaVersion=2;
    public EditorProject applied=new EditorProject();
    public Map<String,String> paths=new LinkedHashMap<>(),managedHashes=new LinkedHashMap<>();
    public Map<String,Set<String>> frozen=new LinkedHashMap<>();
    public Set<String> manualFiles=new LinkedHashSet<>();
    public Activation activation;
    public static final class Activation {
        public String id=UUID.randomUUID().toString();
        public long boot=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        public long serverGeneration,clientGeneration;
        public EditorProject desired,submitted;
        public Map<String,String> hashes=new LinkedHashMap<>();
        public boolean restart,client,resources;
        /** Transaction-only removal of editor rules; never a new recipe-disable rule. */
        public Set<String> revokedRecipes=new LinkedHashSet<>();
        /** Registration removal is verified after a new JVM starts, never during /reload. */
        public Set<String> deletedItems=new LinkedHashSet<>();
    }
    public static <T> T copy(T value,Class<T> type){return ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(value),type);}
    public static byte[] bytes(Object value){return ProjectStore.JSON.toJson(value).getBytes(StandardCharsets.UTF_8);}
    public static String hash(byte[] value){try{return value==null?"-":HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException(e);}}
    public static String hash(Path path)throws IOException{return hash(Files.exists(path)?Files.readAllBytes(path):null);}
    public static WorkspaceState read(Path root)throws IOException{
        Path file=root.resolve(FILE);WorkspaceState s=Files.exists(file)?ProjectStore.JSON.fromJson(Files.readString(file),WorkspaceState.class):new WorkspaceState();
        if(s==null||s.schemaVersion!=2||s.applied==null||s.paths==null||s.managedHashes==null||s.manualFiles==null||s.frozen==null)throw new IOException("已有修改清单损坏，原文件未覆盖");
        s.applied.validateStructure();return s;
    }
    public EditorProject merge(EditorProject draft){
        EditorProject result=copy(applied,EditorProject.class);
        result.recipes.putAll(copy(draft,EditorProject.class).recipes);result.items.putAll(copy(draft,EditorProject.class).items);
        result.assets.putAll(draft.assets);result.configs=new ArrayList<>(draft.configs);result.scripts.clear();return result;
    }
    public boolean canRevokeRecipe(String id){return applied.recipes.containsKey(id)&&!frozen.getOrDefault(GENERATED.getFirst(),Set.of()).contains(id);}
    public EditorProject withoutRecipe(String id){
        if(!canRevokeRecipe(id))throw new IllegalArgumentException("此规则已转为手写或不属于可撤销的编辑器规则，请从脚本管理编辑来源");
        EditorProject desired=copy(applied,EditorProject.class);desired.recipes.remove(id);return desired;
    }
    public boolean canDeleteRegisteredItem(String id){
        var item=applied.items.get(id);
        return item!=null&&item.register&&frozen.entrySet().stream().noneMatch(e->!e.getKey().equals(GENERATED.getFirst())&&e.getValue().contains(id));
    }
    public EditorProject withoutRegisteredItem(String id){
        if(!canDeleteRegisteredItem(id))throw new IllegalArgumentException("只能删除编辑器拥有的注册物品；手写注册请编辑来源脚本");
        EditorProject desired=copy(applied,EditorProject.class);
        new dev.kubejseditor.model.PendingOperation("item",id,"物品注册").cancel(desired);
        return desired;
    }
    public String outputPath(String base){return paths.getOrDefault(base,base);}
    public String baseFor(String actual){return GENERATED.stream().filter(b->outputPath(b).equals(actual)).findFirst().orElse(null);}
    public boolean isManaged(String actual){return managedHashes.containsKey(actual)&&!manualFiles.contains(actual);}
    public void takeOver(Path root,String actual,EditorProject draft)throws IOException{
        String base=baseFor(actual);if(base==null||!isManaged(actual))throw new IOException("此文件不属于自动生成器");
        boolean recipes=base.equals(GENERATED.getFirst());
        if(recipes?!draft.recipes.isEmpty():!draft.items.isEmpty())throw new IOException("请先应用或移除关联表单草稿，再转为手写");
        frozen.computeIfAbsent(base,k->new LinkedHashSet<>()).addAll(recipes?applied.recipes.keySet():applied.items.keySet());
        manualFiles.add(actual);managedHashes.remove(actual);
        int n=1;String next;do{next=base.substring(0,base.length()-3)+"_overrides_"+(n++)+".js";}while(manualFiles.contains(next)||Files.exists(root.resolve(next)));
        paths.put(base,next);
    }
    public EditorProject forScript(EditorProject content,EditorProject draft,String base){
        EditorProject p=copy(content,EditorProject.class);Set<String> ids=frozen.getOrDefault(base,Set.of());
        if(base.equals(GENERATED.getFirst()))p.recipes.keySet().removeIf(id->ids.contains(id)&&!draft.recipes.containsKey(id));
        else p.items.keySet().removeIf(id->ids.contains(id)&&!draft.items.containsKey(id));
        return p;
    }
    public void promote(EditorProject pending){
        if(activation==null)return;
        applied=copy(activation.desired,EditorProject.class);applied.configs.clear();applied.scripts.clear();
        EditorProject submitted=activation.submitted;
        submitted.recipes.forEach((id,v)->{if(equal(v,pending.recipes.get(id)))pending.recipes.remove(id);});
        submitted.items.forEach((id,v)->{if(equal(v,pending.items.get(id)))pending.items.remove(id);});
        submitted.scripts.forEach((id,v)->{if(equal(v,pending.scripts.get(id)))pending.scripts.remove(id);});
        submitted.assets.forEach((id,v)->pending.assets.remove(id,v));
        pending.configs.removeIf(v->submitted.configs.stream().anyMatch(s->equal(v,s)));
        frozen.forEach((base,ids)->ids.removeAll(base.equals(GENERATED.getFirst())?submitted.recipes.keySet():submitted.items.keySet()));
        activation=null;
    }
    private static boolean equal(Object a,Object b){return ProjectStore.JSON.toJsonTree(a).equals(ProjectStore.JSON.toJsonTree(b));}
}
