package dev.kubejseditor.recipe;

import com.google.gson.*;
import java.util.*;

public final class RecipeTools {
    private static final Set<String> INPUTS = Set.of("ingredient","ingredients","key","template","base","addition");
    public static boolean contains(JsonElement json, String id, boolean output) {
        if (id == null || id.isBlank()) return true;
        JsonObject root = json.getAsJsonObject();
        for (var e : root.entrySet()) {
            if ((output && Set.of("result","results").contains(e.getKey())) || (!output && INPUTS.contains(e.getKey()))) {
                if (containsId(e.getValue(), id)) return true;
            }
        }
        if (!output && root.has("sequence")) for (JsonElement step : root.getAsJsonArray("sequence")) if (contains(step,id,false)) return true;
        return false;
    }
    private static boolean containsId(JsonElement value, String id) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return value.getAsString().equals(id);
        if (value.isJsonArray()) for (JsonElement e : value.getAsJsonArray()) if (containsId(e,id)) return true;
        if (value.isJsonObject()) {
            if(value.getAsJsonObject().has("items")&&value.getAsJsonObject().get("items").isJsonPrimitive()&&value.getAsJsonObject().get("items").getAsString().equals(id))return true;
            String key = id.startsWith("#") ? "tag" : null; String raw = id.startsWith("#") ? id.substring(1) : id;
            for (var e : value.getAsJsonObject().entrySet()) {
                if(e.getKey().equals("components"))continue;
                if ((key == null ? Set.of("id","item","fluid").contains(e.getKey()) : e.getKey().equals(key)) && e.getValue().isJsonPrimitive() && e.getValue().getAsString().equals(raw)) return true;
                if (!e.getValue().isJsonPrimitive() && containsId(e.getValue(),id)) return true;
            }
        }
        return false;
    }
    /** Only adapter-approved recipe fields are rewritten; metadata and components are not searched. */
    public static JsonObject replace(JsonObject source, String from, String to, boolean output) {
        JsonObject result = source.deepCopy();
        for (var e : result.entrySet()) {
            if ((output && Set.of("result","results").contains(e.getKey())) || (!output && INPUTS.contains(e.getKey()))) replaceLeaf(e.getValue(),from,to,output);
        }
        if (!output && result.has("sequence")) {
            JsonArray steps = result.getAsJsonArray("sequence");
            for (int i=0;i<steps.size();i++) steps.set(i,replace(steps.get(i).getAsJsonObject(),from,to,false));
        }
        return result;
    }
    private static void replaceLeaf(JsonElement value, String from, String to, boolean output) {
        if (value.isJsonArray()) { for (JsonElement e : value.getAsJsonArray()) replaceLeaf(e,from,to,output); return; }
        if (!value.isJsonObject()) return;
        JsonObject o = value.getAsJsonObject();
        if(o.has("items")&&o.get("items").isJsonPrimitive()&&o.get("items").getAsString().equals(from)){o.addProperty("items",to);return;}
        String match = from.startsWith("#") ? "tag" : null, raw = from.startsWith("#") ? from.substring(1) : from;
        for (String key : List.of("item","id","tag","fluid")) {
            if (o.has(key) && o.get(key).isJsonPrimitive() && (match == null ? !key.equals("tag") : key.equals(match)) && o.get(key).getAsString().equals(raw)) {
                if (output && to.startsWith("#")) throw new IllegalArgumentException("产物不能是标签");
                if(!output&&o.has("amount")&&(key.equals("fluid")||key.equals("tag"))){o.remove(key);o.addProperty(to.startsWith("#")?"tag":"fluid",to.startsWith("#")?to.substring(1):to);o.addProperty("type",to.startsWith("#")?"neoforge:tag":"neoforge:single");return;}
                o.remove(key); o.addProperty(to.startsWith("#") ? "tag" : key.equals("tag") ? (output ? "id" : "item") : key, to.startsWith("#") ? to.substring(1) : to); return;
            }
        }
        // Key maps and OR ingredient alternatives are structural; item component payloads are opaque.
        if (!o.has("item") && !o.has("id") && !o.has("tag") && !o.has("fluid")) for (var e : o.entrySet()) if (!e.getKey().equals("components")) replaceLeaf(e.getValue(),from,to,output);
    }
}
