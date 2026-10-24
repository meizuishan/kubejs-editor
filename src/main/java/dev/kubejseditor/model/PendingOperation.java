package dev.kubejseditor.model;

import java.util.*;

/** Stable keys shared by the queue, errors and cancellation; not a history log. */
public record PendingOperation(String kind,String id,String title) {
    public String key(){return kind+":"+id;}
    public static String configKey(EditorProject.ConfigDraft c){return c.modId+"/"+c.type+"/"+c.file+"/"+c.target+"/"+String.join(".",c.path);}
    public static List<PendingOperation> list(EditorProject p){
        List<PendingOperation> out=new ArrayList<>();
        p.recipes.forEach((id,r)->out.add(new PendingOperation("recipe",id,switch(r.operation){case "remove"->"配方禁用";case "replace"->"配方修改";default->"配方生成";})));
        p.items.forEach((id,i)->out.add(new PendingOperation("item",id,i.register?"物品注册":"物品修改")));
        p.configs.forEach(c->out.add(new PendingOperation("config",configKey(c),"参数修改")));
        p.scripts.forEach((id,s)->out.add(new PendingOperation("script",id,"源码修改")));
        return out;
    }
    public void cancel(EditorProject p){
        switch(kind){
            case "recipe" -> p.recipes.remove(id);
            case "item" -> {p.items.remove(id);p.assets.keySet().removeIf(path->{if(!path.startsWith("kubejs/assets/kubejs_editor/textures/")||!path.endsWith(".png"))return false;String location="kubejs_editor:"+path.substring("kubejs/assets/kubejs_editor/textures/".length(),path.length()-4);return p.items.values().stream().noneMatch(i->BlockKinds.textures(i).contains(location));});}
            case "config" -> p.configs.removeIf(c->configKey(c).equals(id));
            case "script" -> p.scripts.remove(id);
            default -> throw new IllegalArgumentException("未知操作类型："+kind);
        }
    }
}
