package dev.kubejseditor.recipe;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.recipe.compat.CompatJson;
import java.util.*;

/** An explicit transformation contract; path hints never establish provenance without Codec equality. */
public final class SourceConversion {
    public String category,sourceType,displayType,displayPrefix,label;
    public Map<String,String> fields=new LinkedHashMap<>();
    public void check(){EditorProject.requireId(category);EditorProject.requireId(sourceType);EditorProject.requireId(displayType);if(displayPrefix==null||displayPrefix.isBlank()||displayPrefix.contains(".."))throw new IllegalArgumentException("需要明确的转换 ID 前缀");if(label==null)label=displayType;if(fields==null)fields=new LinkedHashMap<>();for(var e:fields.entrySet())if(!e.getKey().startsWith("/")||!e.getValue().startsWith("/"))throw new IllegalArgumentException("转换字段须使用 JSON 路径");}
    public boolean candidate(String displayId,String sourceId,String nativeType){
        if(!sourceType.equals(nativeType))return false;
        String path=displayId.substring(displayId.indexOf(':')+1);
        return path.startsWith(displayPrefix)&&sourceId.substring(sourceId.indexOf(':')+1).equals(path.substring(displayPrefix.length()));
    }
    public String sourcePath(String displayId){String path=displayId.substring(displayId.indexOf(':')+1);return path.startsWith(displayPrefix)?path.substring(displayPrefix.length()):null;}
    public JsonObject project(JsonObject source){
        var result=LoadConditions.body(source);result.addProperty("type",displayType);
        for(var e:fields.entrySet()){var value=CompatJson.get(source,e.getKey());if(!value.isJsonNull())CompatJson.set(result,e.getValue(),value.deepCopy());}
        return result;
    }
}
