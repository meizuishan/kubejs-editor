package dev.kubejseditor.recipe.compat;

import com.google.gson.*;
import java.util.*;

/** Common structural wrappers, independent of serializer names or machine whitelists. */
public final class CompatFields {
    public static void shape(CompatDefinition.Field f,JsonElement value){if(f.list&&value.isJsonArray()&&!value.getAsJsonArray().isEmpty())value=value.getAsJsonArray().get(0);if(!value.isJsonObject())return;var o=value.getAsJsonObject();
        if(o.has("ingredient")&&(o.get("ingredient").isJsonObject()||o.get("ingredient").isJsonArray()||o.get("ingredient").isJsonPrimitive()))f.valuePath="/ingredient";
        else for(String key:List.of("item","result","fluid"))if(o.has(key)&&o.get(key).isJsonObject()){f.valuePath="/"+key;break;}
        if(o.has("count")&&number(o.get("count"))){f.quantity="count";f.quantityPath="/count";}else if(o.has("amount")&&number(o.get("amount"))){f.quantity="amount";f.quantityPath="/amount";}
        else if(!f.valuePath.isBlank()&&!f.valuePath.equals("/ingredient")){var inner=CompatJson.get(o,f.valuePath);if(inner.isJsonObject()){var i=inner.getAsJsonObject();String key=f.kind==CompatDefinition.Kind.FLUID?"amount":"count";if(i.has(key)||f.role==CompatDefinition.Role.OUTPUT){f.quantity=key;f.quantityPath=f.valuePath+"/"+key;}}}
    }
    public static boolean number(JsonElement value){return value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber();}
    public static boolean sameSlotSemantics(CompatDefinition.Field a,CompatDefinition.Field b){return a.kind==b.kind&&a.role==b.role&&a.valuePath.equals(b.valuePath)&&a.quantity.equals(b.quantity)&&a.quantityPath.equals(b.quantityPath)&&a.ingredientType.equals(b.ingredientType)&&a.probability.equals(b.probability)&&a.expand.equals(b.expand)&&a.registry.equals(b.registry)&&a.required==b.required&&a.displayQuantity==b.displayQuantity&&a.readOnlyReason.equals(b.readOnlyReason);}
    public static String basicRole(CompatDefinition.Field field){return switch(field.role){case INPUT,CENTER->"输入格";case OUTPUT,RESULT->"输出格";case CATALYST->"催化剂";case CONDITION->"条件修改";case RESOURCE,PARAMETER->field.kind==CompatDefinition.Kind.BOOLEAN?"条件修改":"数值修改";};}
    private CompatFields(){}
}
