package dev.kubejseditor.recipe.compat;

import com.google.gson.*;
import java.util.*;

/** Exact JSON-pointer patches. Work on a copy so a failing operation never partly edits a draft. */
public final class JsonPatches {
    public record Field(String path,JsonElement value){}
    private static List<String> parts(String path){
        if(path==null||!path.startsWith("/")||path.length()>4096)throw new IllegalArgumentException("补丁路径必须以 / 开头");
        var result=new ArrayList<String>();for(String part:path.substring(1).split("/",-1)){for(int i=0;i<part.length();i++)if(part.charAt(i)=='~'&&(i+1>=part.length()||"01".indexOf(part.charAt(++i))<0))throw new IllegalArgumentException("无效路径转义："+path);result.add(part.replace("~1","/").replace("~0","~"));}if(result.size()>64)throw new IllegalArgumentException("路径过深");return result;
    }
    private static int index(String part,int size,boolean add){if(add&&part.equals("-"))return size;if(!part.matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("无效数组索引："+part);int value;try{value=Integer.parseInt(part);}catch(NumberFormatException e){throw new IllegalArgumentException("数组索引过大");}if(value<0||value>size||(value==size&&!add))throw new IllegalArgumentException("数组索引越界："+part);return value;}
    private static JsonElement child(JsonElement parent,String part){if(parent.isJsonArray())return parent.getAsJsonArray().get(index(part,parent.getAsJsonArray().size(),false));if(!parent.isJsonObject()||!parent.getAsJsonObject().has(part))throw new IllegalArgumentException("字段不存在："+part);return parent.getAsJsonObject().get(part);}
    public static JsonElement read(JsonObject root,String path){JsonElement value=root;for(String part:parts(path))value=child(value,part);return value.deepCopy();}
    public static JsonObject operation(String op,String path,JsonElement value){var result=new JsonObject();result.addProperty("op",op);result.addProperty("path",path);if(value!=null)result.add("value",value.deepCopy());return result;}
    public static JsonObject apply(JsonObject original,JsonArray operations){
        if(operations.size()>512||operations.toString().length()>262144)throw new IllegalArgumentException("补丁超过大小限制");var copy=original.deepCopy();
        for(var item:operations){if(!item.isJsonObject())throw new IllegalArgumentException("每条补丁需要对象");var patch=item.getAsJsonObject();String op=patch.get("op").getAsString(),path=patch.get("path").getAsString();var keys=parts(path);
            if(op.equals("test")){if(!read(copy,path).equals(Objects.requireNonNull(patch.get("value"),"缺少 value")))throw new IllegalArgumentException("原值核对失败："+path);continue;}
            JsonElement value=null;if(op.equals("copy")||op.equals("move")){String from=patch.get("from").getAsString();if(op.equals("move")&&path.startsWith(from+"/"))throw new IllegalArgumentException("不能移动到自身子字段");value=read(copy,from);if(op.equals("move"))edit(copy,parts(from),"remove",null);}else if(!op.equals("remove"))value=Objects.requireNonNull(patch.get("value"),"缺少 value");
            if(!Set.of("add","remove","replace","copy","move").contains(op))throw new IllegalArgumentException("未知补丁操作："+op);edit(copy,keys,op.equals("copy")||op.equals("move")?"add":op,value);
        }
        if(!Objects.equals(original.get("type"),copy.get("type")))throw new IllegalArgumentException("字段补丁不能更换或删除配方类型");return copy;
    }
    private static void edit(JsonObject root,List<String> keys,String op,JsonElement value){JsonElement owner=root;for(int i=0;i<keys.size()-1;i++)owner=child(owner,keys.get(i));String key=keys.getLast();if(owner.isJsonArray()){var array=owner.getAsJsonArray();int at=index(key,array.size(),op.equals("add"));if(op.equals("remove"))array.remove(at);else if(op.equals("replace"))array.set(at,value.deepCopy());else{array.add(JsonNull.INSTANCE);for(int i=array.size()-1;i>at;i--)array.set(i,array.get(i-1));array.set(at,value.deepCopy());}}else if(owner.isJsonObject()){var object=owner.getAsJsonObject();if(!op.equals("add")&&!object.has(key))throw new IllegalArgumentException("字段不存在："+key);if(op.equals("remove"))object.remove(key);else object.add(key,value.deepCopy());}else throw new IllegalArgumentException("无法在基础值内修改字段");}
    public static List<Field> fields(JsonObject data){var result=new ArrayList<Field>();collect(data,"",0,result);return List.copyOf(result);}
    public static JsonArray replaceValues(JsonObject data,String scope,JsonElement from,JsonElement to){if(!scope.isEmpty())read(data,scope);var operations=new JsonArray();for(var field:fields(data))if((scope.isEmpty()||field.path.equals(scope)||field.path.startsWith(scope+"/"))&&!field.path.equals("/type")&&field.value.equals(from)&&(field.value.isJsonPrimitive()||field.value.isJsonNull()))operations.add(operation("replace",field.path,to));if(operations.size()>512)throw new IllegalArgumentException("匹配字段过多，请缩小替换范围");return operations;}
    private static void collect(JsonElement value,String path,int depth,List<Field> result){if(depth>32||result.size()>=4096)return;if(!path.isEmpty())result.add(new Field(path,value.deepCopy()));if(value.isJsonObject())for(var entry:value.getAsJsonObject().entrySet())collect(entry.getValue(),path+"/"+CompatJson.escape(entry.getKey()),depth+1,result);else if(value.isJsonArray())for(int i=0;i<value.getAsJsonArray().size();i++)collect(value.getAsJsonArray().get(i),path+"/"+i,depth+1,result);}
    private JsonPatches(){}
}
