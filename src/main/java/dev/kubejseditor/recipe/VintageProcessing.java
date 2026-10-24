package dev.kubejseditor.recipe;

import com.google.gson.*;
import java.util.*;

/** Native 1.21.1-1.3.7 contract, independent of optional Vintage classes. */
public final class VintageProcessing {
    public static final String MOD="createvintageneoforged",PREFIX=MOD+":";
    public record Mode(String path,String title,String english,String animation,RecipeLimits limits) {public String type(){return PREFIX+path;}}
    public record Workstation(String title,String selector,List<String> types) {}
    private static final List<Workstation> WORKSTATIONS=List.of(
        new Workstation("复古 · 真空腔室","腔室状态",List.of(PREFIX+"vacuumizing",PREFIX+"pressurizing")),
        new Workstation("复古 · 弯曲压力机","压头类型",List.of(PREFIX+"curving_convex",PREFIX+"curving_concave",PREFIX+"curving_w_shaped",PREFIX+"curving_v_shaped"))
    );
    private static final Map<String,Mode> MODES=new LinkedHashMap<>();
    static {
        add("polishing","砂带研磨","Belt grinding","AnimatedGrinder",1,4,false);
        add("coiling","弹簧卷绕","Spring coiling","AnimatedCoiling",1,4,false);
        add("vacuumizing","真空处理","Vacuumizing","AnimatedVacuumChamber",64,4,true);
        add("pressurizing","加压处理","Pressurizing","AnimatedVacuumChamber",64,4,true);
        add("vibrating","振动处理","Vibrating","AnimatedVibratingTable",1,9,false);
        add("centrifugation","离心分离","Centrifugation","AnimatedCentrifuge",64,4,true);
        add("laser_cutting","激光切割","Laser cutting","AnimatedLaser",1,2,false);
        add("curving_convex","凸面弯曲","Convex curving","AnimatedCurvingPress",1,2,false);
        add("curving_concave","凹面弯曲","Concave curving","AnimatedCurvingPress",1,2,false);
        add("curving_w_shaped","W 形弯曲","W-shaped curving","AnimatedCurvingPress",1,2,false);
        add("curving_v_shaped","V 形弯曲","V-shaped curving","AnimatedCurvingPress",1,2,false);
        add("hammering","锤击加工","Hammering","AnimatedHelve",3,3,false);
        add("turning","车床加工","Turning","AnimatedLathe",1,4,false);
    }
    private static void add(String path,String title,String english,String animation,int in,int out,boolean basin){var mode=new Mode(path,"复古 · "+title,"Vintage · "+english,animation,new RecipeLimits(in,out,basin?2:0,basin?2:0));MODES.put(mode.type(),mode);}
    public static Collection<Mode> all(){return Collections.unmodifiableCollection(MODES.values());}
    public static Mode get(String type){return MODES.get(type);}
    public static Workstation workstation(String type){return WORKSTATIONS.stream().filter(w->w.types().contains(type)).findFirst().orElse(null);}
    public static String workstationKey(String type){var station=workstation(type);return station==null?type:station.types().getFirst();}
    public static String workstationTitle(String type,String fallback){var station=workstation(type);return station==null?fallback:station.title();}
    public static String stateLabel(String type){return get(type).title().substring("复古 · ".length());}
    /** Only the serializer changes; the recipe ID, JSON fields and authoring operation stay intact. */
    public static void switchState(JsonObject data,String target){
        String source=data.get("type").getAsString();var station=workstation(source);
        if(station==null||!station.types().contains(target))throw new IllegalArgumentException("只能切换同一设备的加工状态");
        data.addProperty("type",target);
    }
    public static boolean supports(String type){return MODES.containsKey(type);}
    public static boolean basin(String type){return Set.of(PREFIX+"vacuumizing",PREFIX+"pressurizing",PREFIX+"centrifugation").contains(type);}
    public static boolean dynamic(String type){return Set.of(PREFIX+"auto_smithing",PREFIX+"auto_upgrade",PREFIX+"leaves_vibrating").contains(type);}
    public static List<String> dynamicTypes(){return List.of(PREFIX+"auto_smithing",PREFIX+"auto_upgrade",PREFIX+"leaves_vibrating");}
    public static String dynamicTitle(String type){return type.endsWith("leaves_vibrating")?"复古 · 树叶振动":type.endsWith("auto_upgrade")?"复古 · 自动升级":"复古 · 自动锻造";}
    public static String explanation(String type){return type.endsWith("leaves_vibrating")?"树叶振动的产物来自树叶方块掉落，受 allowVibratingLeaves 配置控制；此版本不按展示配方的 results 加工。可在模组参数中管理开关。掉落表编辑尚未提供。":"自动锻造/升级实际读取当前已加载的原版锻造配方；此类型仅用于展示。请通过下方入口修改真实的锻造转换配方，机器会沿用该结果。禁用展示记录不会关闭机器的自动模式。";}
    public static List<String> assemblyTypes(){return List.of(PREFIX+"polishing",PREFIX+"hammering",PREFIX+"turning");}
    public static boolean assemblySupported(String type){return assemblyTypes().contains(type);}
    public static void register(){for(var mode:all())RecipeAdapters.register(new RecipeAdapter(){
        public String type(){return mode.type();}public String title(){return mode.title();}
        public JsonObject newRecipe(){return defaults(type());}
        public List<String> validate(JsonObject data){return RecipeAdapters.validateData(data);}
    });}
    public static JsonObject defaults(String type){
        JsonObject data=RecipeAdapters.parse("{ingredients:[{item:'minecraft:iron_ingot'}],results:[{id:'minecraft:iron_nugget',count:1}]}");data.addProperty("type",type);
        if(type.contains(":curving_")){data.addProperty("requiredBonks",1);data.addProperty("headDamage",1);}
        else if(type.endsWith(":hammering")){data.addProperty("hammer_blows",1);data.addProperty("anvil_block","minecraft:air");}
        else data.addProperty("processing_time",100);
        if(type.endsWith(":polishing")){data.addProperty("speedLimits",0);data.addProperty("fragile",false);}
        if(basin(type))data.addProperty("heat_requirement",type.endsWith(":centrifugation")?"heated":"none");
        return data;
    }
    public static List<String> validate(JsonObject data){
        List<String> errors=new ArrayList<>();String type=data.get("type").getAsString();if(!supports(type))return errors;
        for(String key:List.of("headDamage","requiredBonks","hammer_blows","hammerBlows","speedLimits"))if(data.has(key)){
            double n=data.get(key).getAsDouble();double min=Set.of("requiredBonks","hammer_blows","hammerBlows").contains(key)?1:0;
            if(!Double.isFinite(n)||n!=Math.rint(n)||n<min||key.equals("speedLimits")&&n>3)errors.add("/"+key+" 需要"+(min==1?"正整数":"非负整数")+(key.equals("speedLimits")?"（0～3）":""));
        }
        if(type.endsWith(":centrifugation")&&data.has("heat_requirement")&&data.get("heat_requirement").getAsString().equals("none"))errors.add("/heat_requirement 当前 Vintage 离心机至少需要普通燃烧；none 实际仍按 heated 处理");
        for(String key:List.of("secondaryFluidInput","secondaryFluidOutput"))if(data.has(key)&&data.get(key).getAsInt()>=0)errors.add("/"+key+" 当前 Vintage 1.3.7 的原生 Codec 不保留副流体通道参数，不能保证生效");
        return errors;
    }
    public static List<RecipeCanvas.Slot> slots(JsonObject data){
        if(basin(data.get("type").getAsString()))return CreateProcessingLayout.slots(data);
        List<RecipeCanvas.Slot> out=new ArrayList<>();
        for(boolean output:new boolean[]{false,true}){
            var array=data.getAsJsonArray(output?"results":"ingredients");
            for(int i=0;i<Math.max(1,array.size());i++){int index=i;out.add(new RecipeCanvas.Slot((output?180:8)+i%3*28,116+i/3*38,(output?"产物 ":"原料 ")+(i+1),output,false,()->index<array.size()?array.get(index):JsonNull.INSTANCE,v->{if(v.isJsonNull()&&index>0){array.remove(index);return;}while(array.size()<=index)array.add(JsonNull.INSTANCE);array.set(index,v);}));}
        }return out;
    }
    private VintageProcessing(){}
}
