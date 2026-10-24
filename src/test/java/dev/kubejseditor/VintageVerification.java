package dev.kubejseditor;

import com.google.gson.*;
import dev.kubejseditor.recipe.*;
import java.util.*;

final class VintageVerification {
    private static int checks;
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    static int run(){
        check(VintageProcessing.all().size()==13,"All fixed Vintage serializers are enumerated");
        for(var mode:VintageProcessing.all()){
            var adapter=RecipeAdapters.get(mode.type());var data=adapter.newRecipe();
            check(adapter.validate(data).isEmpty(),"Vintage defaults: "+mode.path());
            var unknown=RecipeAdapters.parse("{source:'keep',nested:[1,2]}");data.add("unknown",unknown.deepCopy());
            var slots=RecipeCanvas.slots(data);slots.getFirst().set(RecipeAdapters.parse("{tag:'c:ingots/iron'}"));
            check(data.get("unknown").equals(unknown),"Vintage slots retain unknown data: "+mode.path());
            while(data.getAsJsonArray("results").size()<mode.limits().outputs())data.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:iron_nugget'}"));
            check(!mode.limits().canAdd(data,true,false),"Output button limit: "+mode.path());
            data.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:gold_nugget'}"));
            check(!adapter.validate(data).isEmpty(),"Imported excess outputs are diagnosed: "+mode.path());
            check(data.getAsJsonArray("results").size()==mode.limits().outputs()+1,"Imported excess outputs are retained: "+mode.path());
        }
        for(String type:VintageProcessing.dynamicTypes())check(RecipeAdapters.get(type)==null,"Dynamic mechanic cannot masquerade as a fixed recipe: "+type);
        var curving=VintageProcessing.defaults(VintageProcessing.PREFIX+"curving_convex");curving.addProperty("requiredBonks",0);check(!RecipeAdapters.validateData(curving).isEmpty(),"Curving rejects zero bonks");curving.addProperty("requiredBonks",1);curving.addProperty("headDamage",.5);check(!RecipeAdapters.validateData(curving).isEmpty(),"Curving rejects fractional head damage");
        var pol=VintageProcessing.defaults(VintageProcessing.PREFIX+"polishing");pol.addProperty("speedLimits",4);check(!RecipeAdapters.validateData(pol).isEmpty(),"Grinding speed levels are bounded");
        var vac=VintageProcessing.defaults(VintageProcessing.PREFIX+"vacuumizing");vac.addProperty("secondaryFluidInput",0);check(!RecipeAdapters.validateData(vac).isEmpty(),"Codec-inert legacy secondary channel is not advertised as working");
        var cen=VintageProcessing.defaults(VintageProcessing.PREFIX+"centrifugation");cen.addProperty("heat_requirement","none");check(!RecipeAdapters.validateData(cen).isEmpty(),"Centrifuge cannot falsely promise no heating");
        var seq=RecipeAdapters.get("create:sequenced_assembly").newRecipe();seq.add("sequence",new JsonArray());for(String type:VintageProcessing.assemblyTypes())RecipeCanvas.addStep(seq,type);
        check(seq.getAsJsonArray("sequence").size()==3&&RecipeAdapters.validateData(seq).isEmpty(),"Supported Vintage assembly steps preserve native defaults");
        RecipeCanvas.normalizeTransition(seq);check(RecipeCanvas.slots(seq).size()>=9,"Vintage assembly steps expose intermediate slots");
        return checks;
    }
}
