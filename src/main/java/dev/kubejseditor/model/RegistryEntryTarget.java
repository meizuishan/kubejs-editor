package dev.kubejseditor.model;

import com.google.gson.*;
import dev.kubejseditor.recipe.LoadConditions;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Optional schema-2 destination. The editor key cannot collide with a normal recipe ID. */
public final class RegistryEntryTarget {
    public static final String NAMESPACE="kubejs_editor_data",PACK="kubejs/config/kubejs_editor/generated_data",DATA=PACK+"/data/";
    public String registry,entry,codecClass,codecField,codecSource;
    public boolean syntheticType;
    public RegistryEntryTarget(){}
    public RegistryEntryTarget(String registry,String entry){this.registry=registry;this.entry=entry;check();}
    public void check(){for(String id:List.of(registry,entry)){EditorProject.requireId(id);for(String part:id.replace(':','/').split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IllegalArgumentException("无效注册表路径："+id);}}
    public String id(){check();return NAMESPACE+":"+HexFormat.of().formatHex(registry.getBytes(StandardCharsets.UTF_8))+"/"+entry.replace(':','/');}
    public static boolean isId(String id){return id!=null&&id.startsWith(NAMESPACE+":");}
    public static RegistryEntryTarget parse(String id){if(!isId(id))return null;String[] parts=id.substring(NAMESPACE.length()+1).split("/",3);if(parts.length!=3)throw new IllegalArgumentException("无效注册表来源 ID");return new RegistryEntryTarget(new String(HexFormat.of().parseHex(parts[0]),StandardCharsets.UTF_8),parts[1]+":"+parts[2]);}
    public String directory(){return registry.startsWith("minecraft:")?registry.substring(10):registry.replace(':','/');}
    public String path(){check();return DATA+entry.substring(0,entry.indexOf(':'))+"/"+directory()+"/"+entry.substring(entry.indexOf(':')+1)+".json";}
    public JsonObject body(JsonObject document){var result=document.deepCopy();if(syntheticType)result.remove("type");return result;}
    public JsonObject generated(EditorProject.RecipeDraft draft){var result=body(LoadConditions.effective(draft.data,draft));if(draft.operation.equals("remove")){var conditions=new JsonArray();var never=new JsonObject();never.addProperty("type","neoforge:false");conditions.add(never);result.add(LoadConditions.KEY,conditions);}return result;}
}
