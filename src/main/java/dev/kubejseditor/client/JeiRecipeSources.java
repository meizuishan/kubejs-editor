package dev.kubejseditor.client;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import dev.kubejseditor.recipe.SimpleRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;

/** Uses only exported JEI interfaces. New source-ID APIs are optional on older JEI releases. */
public final class JeiRecipeSources {
    public record Source(ResourceLocation id,String reason,List<ResourceLocation> candidates,String notice){
        public Source(ResourceLocation id,String reason){this(id,reason,List.of(),"");}
        public Source(ResourceLocation id,String reason,List<ResourceLocation> candidates){this(id,reason,candidates,"");}
    }
    @FunctionalInterface public interface SourceProvider { Source resolve(IRecipeLayoutDrawable<?> layout,net.minecraft.world.item.crafting.RecipeManager manager); }
    private static final List<SourceProvider> providers=new ArrayList<>();
    private static final Map<Object,Source> cache=new IdentityHashMap<>();private static Object lastManager;private static long generation=-1;
    private static final Map<Object,Set<ResourceLocation>> identities=new IdentityHashMap<>();
    private static final Map<String,List<RecipeHolder<?>>> paths=new HashMap<>();
    static List<RecipeHolder<?>> byPath(String path){return path==null?List.of():paths.getOrDefault(path,List.of());}
    public static void register(SourceProvider provider){providers.add(Objects.requireNonNull(provider));cache.clear();}
    public static void associate(IRecipeLayoutDrawable<?> layout,String id){cache.put(layout.getRecipe(),new Source(ResourceLocation.parse(id),null,List.of(),"用户手动选择的实际数据源；修改作用于该来源的全部使用者。"));}
    public static void clear(){cache.clear();identities.clear();paths.clear();lastManager=null;GenericRecipeSources.clear();}
    public static <T> Source resolve(IRecipeLayoutDrawable<T> layout){
        var mc=Minecraft.getInstance();if(mc.level==null)return new Source(null,"世界尚未加载");
        var manager=mc.hasSingleplayerServer()?mc.getSingleplayerServer().getRecipeManager():mc.level.getRecipeManager();
        if(lastManager!=manager||generation!=dev.kubejseditor.runtime.ScriptIndex.serverGeneration){clear();lastManager=manager;generation=dev.kubejseditor.runtime.ScriptIndex.serverGeneration;for(var holder:manager.getRecipes()){identities.computeIfAbsent(holder.value(),k->new LinkedHashSet<>()).add(holder.id());paths.computeIfAbsent(holder.id().getPath(),k->new ArrayList<>()).add(holder);}if(mc.level.getRecipeManager()!=manager)for(var holder:mc.level.getRecipeManager().getRecipes())if(manager.byKey(holder.id()).isPresent())identities.computeIfAbsent(holder.value(),k->new LinkedHashSet<>()).add(holder.id());}
        var existing=cache.get(layout.getRecipe());if(existing!=null)return existing;
        Source result;try{result=resolveNative(layout,manager);}catch(Exception e){CompatDiagnostics.failure("source",layout.getRecipeCategory().getRecipeType().getUid().toString(),e);result=new Source(null,"来源解析失败："+e.getMessage());}cache.put(layout.getRecipe(),result);return result;
    }
    private static <T> Source resolveNative(IRecipeLayoutDrawable<T> layout,net.minecraft.world.item.crafting.RecipeManager manager)throws Exception{
        var options=GenericRecipeSources.inspect(layout.getRecipe(),manager,identities);
        var automatic=GenericRecipeSources.select(options);if(automatic!=null&&options.getFirst().score()>=990)return automatic;
        var ids=new LinkedHashSet<ResourceLocation>();
        try{var method=IRecipeLayoutDrawable.class.getMethod("getSourceRecipeIds");Object value=method.invoke(layout);if(value instanceof Collection<?> list)for(Object id:list)if(id instanceof ResourceLocation location)ids.add(location);}catch(NoSuchMethodException ignored){}catch(ReflectiveOperationException|LinkageError ignored){}
        if(ids.isEmpty())try{var legacy=layout.getRecipeCategory().getRegistryName(layout.getRecipe());if(legacy!=null)ids.add(legacy);}catch(RuntimeException ignored){}
        if(ids.size()==1&&manager.byKey(ids.getFirst()).isPresent())return new Source(ids.getFirst(),null);
        if(ids.size()>1)return new Source(null,"JEI 展示对应多个来源，请确认要编辑的原生配方",ids.stream().filter(id->manager.byKey(id).isPresent()).toList());
        Source deferred=null;for(var provider:providers)try{var found=provider.resolve(layout,manager);if(found!=null){var verified=checked(found,manager);if(verified.id()!=null)return verified;deferred=verified;}}catch(RuntimeException error){CompatDiagnostics.failure("source-provider",layout.getRecipeCategory().getRecipeType().getUid().toString(),error);}
        var converted=ConvertedRecipeSources.resolve(layout,manager);if(converted!=null)return checked(converted,manager);
        if(deferred!=null&&!deferred.candidates().isEmpty())return deferred;
        if(automatic!=null)return automatic;
        if(deferred!=null)return deferred;
        var altar=altar(layout.getRecipe(),manager);if(altar!=null)return altar;
        var infusion=infusion(layout.getRecipe(),manager);if(infusion!=null)return infusion;
        return new Source(null,!dev.kubejseditor.runtime.RegistryDataSources.failure().isBlank()?dev.kubejseditor.runtime.RegistryDataSources.failure():ids.isEmpty()?"JEI 展示没有公开的原生配方来源；当前可查看材料与展示参数":"展示 ID 不在原生 RecipeManager 中，且未识别到可验证的来源转换："+ids.getFirst());
    }
    private static Source checked(Source source,net.minecraft.world.item.crafting.RecipeManager manager){if(source.id!=null&&manager.byKey(source.id).isEmpty())return new Source(null,"来源扩展返回了不存在的配方 ID");return new Source(source.id,source.reason,source.candidates.stream().filter(id->manager.byKey(id).isPresent()).toList(),source.notice);}
    /** This wrapper discards ID/entity/tag provenance, so even a single candidate needs confirmation. */
    private static Source altar(Object display,net.minecraft.world.item.crafting.RecipeManager manager)throws Exception{
        if(!display.getClass().getName().equals("com.github.tartaricacid.touhoulittlemaid.compat.jei.altar.AltarRecipeWrapper"))return null;
        var c=display.getClass();var inputs=(List<?>)c.getMethod("getInputs").invoke(display);var expected=new ArrayList<String>();for(var input:inputs)expected.add(CompatIngredients.signatures((List<?>)input).toString());Collections.sort(expected);
        var output=CompatIngredients.signatures(List.of(c.getMethod("getOutput").invoke(display)));float power=((Number)c.getMethod("getPowerCost").invoke(display)).floatValue();String lang=(String)c.getMethod("getLangKey").invoke(display);var candidates=new ArrayList<ResourceLocation>();
        for(var holder:manager.getRecipes()){var recipe=holder.value();if(!recipe.getClass().getName().equals("com.github.tartaricacid.touhoulittlemaid.crafting.AltarRecipe"))continue;var rc=recipe.getClass();if(!Boolean.TRUE.equals(rc.getMethod("isItemCraft").invoke(recipe)))continue;if(Float.compare(power,((Number)rc.getMethod("getPower").invoke(recipe)).floatValue())!=0||!lang.equals(rc.getMethod("getLangKey").invoke(recipe)))continue;if(!output.equals(CompatIngredients.signatures(List.of(rc.getMethod("getResult").invoke(recipe)))))continue;var actual=new ArrayList<String>();for(var ingredient:recipe.getIngredients())actual.add(CompatIngredients.signatures(Arrays.asList(ingredient.getItems())).toString());Collections.sort(actual);if(expected.equals(actual))candidates.add(holder.id());}
        return new Source(null,candidates.isEmpty()?"祭坛展示未找到完整匹配的物品配方；实体结果保持只读":"祭坛展示未保留来源 ID；已按全部材料、组件、产物、灵力与提示核对，请确认原生来源",List.copyOf(candidates));
    }
    private static Source infusion(Object display,net.minecraft.world.item.crafting.RecipeManager manager)throws Exception{
        if(!display.getClass().getName().equals("com.jdte.client.jei.infusion.InfusionJeiRecipe"))return null;var c=display.getClass();var input=CompatIngredients.signatures(List.of(c.getMethod("inputStack").invoke(display)));var fluid=CompatIngredients.signatures(List.of(c.getMethod("fluidStack").invoke(display)));var output=CompatIngredients.signatures(List.of(c.getMethod("outputStack").invoke(display)));int energy=((Number)c.getMethod("energyCost").invoke(display)).intValue();var candidates=new ArrayList<ResourceLocation>();
        for(var holder:manager.getRecipes()){var recipe=holder.value();var rc=recipe.getClass();if(!rc.getName().equals("com.jdte.common.recipes.InfusionRecipe"))continue;if(energy!=((Number)rc.getMethod("getEnergyCost").invoke(recipe)).intValue())continue;if(input.equals(CompatIngredients.signatures(List.of(rc.getMethod("getInput").invoke(recipe))))&&fluid.equals(CompatIngredients.signatures(List.of(rc.getMethod("getFluidInput").invoke(recipe))))&&output.equals(CompatIngredients.signatures(List.of(rc.getMethod("getOutput").invoke(recipe)))))candidates.add(holder.id());}
        return new Source(null,candidates.isEmpty()?"此灌注展示由运行时生成，没有对应的原生配方；可查看展示与机器配置":"已核对物品、流体、组件、数量及能量；请确认灌注原生来源",List.copyOf(candidates));
    }
    public static <T> void capture(IRecipeLayoutDrawable<T> layout,ResourceLocation id){
        CompatJeiLayouts.remember(id.toString(),layout);
        int inputs=0,outputs=0,fluidInputs=0,fluidOutputs=0;
        for(var slot:layout.getRecipeSlotsView().getSlotViews()){
            boolean input=slot.getRole()==RecipeIngredientRole.INPUT,output=slot.getRole()==RecipeIngredientRole.OUTPUT;if(!input&&!output)continue;
            var ingredients=slot.getAllIngredients().toList();if(ingredients.isEmpty())continue;
            boolean fluid=ingredients.stream().anyMatch(ingredient->ingredient.getIngredient() instanceof FluidStack);
            if(input){inputs++;if(fluid)fluidInputs++;}else{outputs++;if(fluid)fluidOutputs++;}
        }
        SimpleRecipes.record(id.toString(),new SimpleRecipes.JeiEvidence(layout.getRecipeCategory().getRecipeType().getUid().toString(),layout.getRecipeCategory().getTitle().getString(),inputs,outputs,fluidInputs,fluidOutputs));
    }
    public static <T> String diagnostics(IRecipeLayoutDrawable<T> layout,String reason){
        if(layout.getRecipeCategory().getRecipeType().getUid().getNamespace().equals("forbidden_arcanus"))reason+="\n仪式可通过原生数据包注册表编辑，不要求 forbidden_arcanus_js。若仍无法定位，请核对注册表内容和公开数据 Codec。";
        return reason+"\n\nJEI 分类："+layout.getRecipeCategory().getRecipeType().getUid()+"\n"+layout.getRecipeCategory().getTitle().getString()+"\n\n简单适配需要唯一的实际配方来源；JEI 燃料、动态计算或多来源展示需专用接口。";
    }
    public static String title(IRecipeLayoutDrawable<?> layout){return layout.getRecipeSlotsView().getSlotViews().stream().filter(s->s.getRole()==RecipeIngredientRole.OUTPUT).flatMap(s->s.getAllIngredients()).map(v->{Object value=v.getIngredient();return value instanceof net.minecraft.world.item.ItemStack item?item.getHoverName().getString():value instanceof FluidStack fluid?fluid.getHoverName().getString():String.valueOf(value);}).distinct().limit(4).collect(java.util.stream.Collectors.joining(" / "));}
    private JeiRecipeSources(){}
}
