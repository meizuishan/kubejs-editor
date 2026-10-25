package dev.kubejseditor.model;

import com.google.gson.*;
import java.util.*;

/** Conservative literal references, including nested recipes, components and grid metadata. */
public final class ItemReferences {
    public static Set<String> matching(JsonElement value,Set<String> ids){var out=new LinkedHashSet<String>();matching(value,ids,out);return out;}
    private static void matching(JsonElement value,Set<String> ids,Set<String> out){
        if(value==null||value.isJsonNull())return;
        if(value.isJsonPrimitive()){if(value.getAsJsonPrimitive().isString()&&ids.contains(value.getAsString()))out.add(value.getAsString());}
        else if(value.isJsonArray())for(var child:value.getAsJsonArray())matching(child,ids,out);
        else for(var child:value.getAsJsonObject().entrySet())matching(child.getValue(),ids,out);
    }
    public static boolean contains(JsonElement value,String id){
        if(value==null||value.isJsonNull())return false;
        if(value.isJsonPrimitive())return value.getAsJsonPrimitive().isString()&&value.getAsString().equals(id);
        if(value.isJsonArray()){for(var child:value.getAsJsonArray())if(contains(child,id))return true;}
        else for(var entry:value.getAsJsonObject().entrySet())if(contains(entry.getValue(),id))return true;
        return false;
    }
    public static List<String> recipes(EditorProject project,String id){
        List<String> refs=new ArrayList<>();
        for(var draft:project.recipes.values())if(draft.enabled&&!draft.operation.equals("remove")&&(contains(draft.data,id)||draft.grid!=null&&contains(draft.grid.cells,id)))refs.add(draft.id);
        return refs;
    }
    private ItemReferences(){}
}
