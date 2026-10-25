package dev.kubejseditor.runtime;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.rhino.*;
import dev.kubejseditor.config.CreateConfigs;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.script.KjsGenerator;
import dev.kubejseditor.storage.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.item.crafting.*;
import dev.kubejseditor.export.GenericFormatter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class EditorRuntime {
    private static EditorProject project;
    private static Path loadedRoot;
    private static WorkspaceState state;
    private static boolean applying;
    private static long lastPoll;
    private static long reloadRequests;
    public static long reloadRequests(){return reloadRequests;}
    public static String activationError="";
    private static final Map<FileTransaction.Review,WorkspaceState> prepared=new IdentityHashMap<>();
    private static Object indexedManager;private static long indexedGeneration=-1;private static int indexedTemplates=-1;
    private static List<Loaded> recipeIndex=List.of();
    private static final GenerationCache<ResourceLocation,Loaded> encodedRecipes=new GenerationCache<>();
    public static long lastPreparationMs,lastIndexMs;
    public static void invalidateRecipes(){indexedManager=null;recipeIndex=List.of();dev.kubejseditor.client.RecipeNames.invalidate();}
    public static void recipesChanged(){encodedRecipes.clear();dev.kubejseditor.client.JeiRecipeSources.clear();invalidateRecipes();}
    public static boolean busy(){return applying;}
    public static boolean locked(){return busy()||state().activation!=null||requiresRestart();}
    public static void requireEditable(){if(Files.exists(root().resolve("kubejs/config/abyss_editor")))throw new IllegalStateException("检测到旧工作区，请关闭游戏后迁移；原数据未覆盖");if(locked())throw new IllegalStateException(state().activation!=null&&state.activation.restart?"存在等待重启的批次，请重启或整批回滚":"上次提交尚未完成，请等待加载结果或先整批回滚");}
    public static List<dev.kubejseditor.model.PendingOperation> operations(){
        if(state().activation!=null&&state.activation.restart&&activationError.isBlank())return List.of();
        return dev.kubejseditor.model.PendingOperation.list(project()).stream().map(op->op.kind().equals("item")&&state().applied.items.containsKey(op.id())?new dev.kubejseditor.model.PendingOperation(op.kind(),op.id(),"物品修改"):op).toList();
    }
    public static void cancel(dev.kubejseditor.model.PendingOperation op)throws IOException{
        requireEditable();op.cancel(project());CreateConfigs.restore(project());saveDraft();prepared.clear();
    }
    public static WorkspaceState state(){project();return state;}
    public static EditorProject content(){return state().merge(project());}
    public static Path lastBackup;
    private static final String RESTART_MARKER="kubejs/config/kubejs_editor/restart-required.json";
    public static boolean requiresRestart(){try{return JsonParser.parseString(Files.readString(root().resolve(RESTART_MARKER))).getAsJsonObject().get("boot").getAsLong()==java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();}catch(Exception e){return false;}}
    private static byte[] restartMarker(){JsonObject marker=new JsonObject();marker.addProperty("boot",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());return ProjectStore.JSON.toJson(marker).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    public record Loaded(String id,String type,JsonObject data,String error,dev.kubejseditor.model.RegistryEntryTarget registryTarget) {
        public Loaded(String id,String type,JsonObject data,String error){this(id,type,data,error,null);}
        public String capabilityLabel(){if(data==null)return "只读";var adapter=RecipeAdapters.get(type);if(adapter instanceof dev.kubejseditor.recipe.compat.CompatibilityAdapter a&&a.definition.fields.stream().anyMatch(f->!f.readOnlyReason.isBlank()))return "部分字段可编辑";if(error==null)return LoadConditions.present(data)?"可编辑 · 含加载条件":"可编辑";if(error.startsWith("尚未识别")||error.startsWith("此类型尚无")||error.startsWith("简单适配不可用")||error.startsWith("需要确认字段映射"))return "尚未识别";return "只读";}
    }
    public static Path root(){return Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();}
    public static boolean canApply(){return Minecraft.getInstance().hasSingleplayerServer()&&Minecraft.getInstance().level!=null;}
    public static EditorProject project(){
        if(project==null||!root().equals(loadedRoot))try {if(Files.exists(root().resolve("kubejs/config/abyss_editor")))throw new IllegalStateException("检测到旧版编辑器目录，请关闭游戏后运行 Install-Release.ps1 完成空工作区迁移；原数据未覆盖");SimpleRecipes.load(root());CompatibilityRecipes.load(root());RecipeTemplates.load(root(),EditorCompatibility::requirement);project=ProjectStore.read(root());state=WorkspaceState.read(root());loadedRoot=root();CreateConfigs.restore(project);adoptLegacy();}catch(Exception e){project=null;throw new IllegalStateException("草稿读取失败，原文件未覆盖："+e.getMessage(),e);}
        return project;
    }
    public static void saveDraft()throws IOException{CreateConfigs.persistInto(project());ProjectStore.saveDraft(root(),project());}
    public static List<ValidationIssue> validationIssues(){
        List<ValidationIssue> issues=validateContent(content());
        for(var d:project().scripts.values())try{
            validateScripts(Map.of(d.path,d.text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            if(!d.baseHash.equals(WorkspaceState.hash(EditorScripts.checked(root(),d.path))))throw new IOException("脚本已被外部修改，请重新打开后合并");
        }catch(Exception e){issues.add(new ValidationIssue("script:"+d.path,"源码",e.getMessage()));}
        try{CreateConfigs.output(root());}catch(Exception e){for(var c:project().configs)issues.add(new ValidationIssue("config:"+dev.kubejseditor.model.PendingOperation.configKey(c),"配置",e.getMessage()));}
        return List.copyOf(issues);
    }
    private static List<ValidationIssue> validateContent(EditorProject p){
        List<ValidationIssue> issues=new ArrayList<>();Set<String> pending=new HashSet<>();
        for(var item:p.items.values())if(item.register)for(var member:RegistrationMembers.of(item))if(member.registry().equals("item")&&!registered(member.registry(),member.id()))pending.add(member.id());
        Set<String> claimed=new HashSet<>();
        for(var item:p.items.values())try{
            boolean exists=BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(item.id));
            if(!item.register&&!exists)throw new IllegalArgumentException("要修改的物品不存在："+item.id);
            if(item.register){
                RegistrationMembers.sameStructure(state().applied.items.get(item.id),item);
                for(var member:RegistrationMembers.of(item)){
                    if(!claimed.add(member.registry()+":"+member.id()))throw new IllegalArgumentException("注册 ID 在多个待办中重复："+member.id());
                    if(registered(member.registry(),member.id())&&!isPreviouslyManaged(member.id()))throw new IllegalArgumentException("注册 ID 已被占用："+member.id());
                }
                if(RegistrationMembers.crop(item)&&!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(item.block.crop.output))&&!pending.contains(item.block.crop.output))throw new IllegalArgumentException("作物产物不存在："+item.block.crop.output);
            }
            EditorProject single=new EditorProject();single.items.put(item.id,item);KjsGenerator.generate(single);
            if(item.register&&dev.kubejseditor.model.ItemKinds.kind(item).equals("block"))for(String texture:dev.kubejseditor.model.BlockKinds.textures(item)){
                ResourceLocation location=ResourceLocation.parse(texture);String path="textures/"+location.getPath()+".png";
                if(!p.assets.containsKey("kubejs/assets/"+location.getNamespace()+"/"+path)&&Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(location.getNamespace(),path)).isEmpty())throw new IllegalArgumentException("未找到方块贴图："+texture);
            }
        }catch(Exception e){issues.add(new ValidationIssue("item:"+item.id,"物品",e.getMessage()));}
        for(var recipe:p.recipes.values())if(recipe.enabled)try{
            if(recipe.registryTarget!=null){RegistryDataSources.validate(recipe);continue;}
            if(recipe.operation.equals("remove"))continue;
            if(!recipe.forceNative){CompatibilityRecipes.restore(recipe);SimpleRecipes.restore(recipe);}
            JsonObject generated=CraftingGrid.generated(recipe);if(!RecipeLoadConditions.matches(generated))continue;List<String> structural=recipe.forceNative?List.of():RecipeAdapters.validateData(generated);
            if(!structural.isEmpty())for(String error:structural)issues.add(new ValidationIssue("recipe:"+recipe.id,"配方",error));
            else validateRecipe(generated,pending,recipe.forceNative);
        }
        catch(Exception e){issues.add(new ValidationIssue("recipe:"+recipe.id,"配方",e.getMessage()));}
        return issues;
    }
    public static List<Loaded> recipes(){
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return List.of();project();List<Loaded> result=new ArrayList<>();
        // Network-synced shaped patterns omit their codec preimage ("unpacked recipe").
        // In local singleplayer the authoritative server retains that lossless data.
        var manager=mc.hasSingleplayerServer()?mc.getSingleplayerServer().getRecipeManager():mc.level.getRecipeManager();
        if(manager==indexedManager&&indexedGeneration==ScriptIndex.serverGeneration&&indexedTemplates==RecipeTemplates.revision())return recipeIndex;
        long started=System.nanoTime();
        // Persisted simple rules remain editable after a cold start with discovery disabled.
        for(var saved:state.merge(project).recipes.values())try{CompatibilityRecipes.restore(saved);SimpleRecipes.restore(saved);}catch(RuntimeException ignored){/* Validation reports the individual invalid rule. */}
        for(RecipeHolder<?> holder:manager.getRecipes())result.add(describe(holder,manager));
        var registryIds=new LinkedHashSet<>(RegistryDataSources.knownIds());state.merge(project).recipes.values().stream().filter(d->d.registryTarget!=null).forEach(d->registryIds.add(d.id));for(String id:registryIds){var entry=recipe(id);if(entry!=null)result.add(entry);}
        result.sort(Comparator.comparing(Loaded::id));indexedManager=manager;indexedGeneration=ScriptIndex.serverGeneration;indexedTemplates=RecipeTemplates.revision();recipeIndex=List.copyOf(result);lastIndexMs=(System.nanoTime()-started)/1_000_000;System.out.println("[KubeJS Editor] 配方索引："+result.size()+" 条，"+lastIndexMs+" ms");return recipeIndex;
    }
    /** A JEI click only needs its authoritative recipe, not a scan of the complete pack. */
    public static Loaded recipe(String id){
        if(dev.kubejseditor.model.RegistryEntryTarget.isId(id)){project();try{var entry=RegistryDataSources.read(id);if(entry==null)return null;var d=entry.data();String warning=null;try{CompatibilityRecipes.discover(d.get("type").getAsString(),d,entry.value());}catch(RuntimeException e){warning="部分字段尚未识别，可编辑原始 JSON："+e.getMessage();}return new Loaded(id,d.get("type").getAsString(),d,warning,entry.target());}catch(RuntimeException e){return new Loaded(id,"kubejs_editor:registry_entry",null,"原生注册表读取失败："+e.getMessage(),RegistryDataSources.target(id));}}
        var mc=Minecraft.getInstance();if(mc.level==null)return null;project();var manager=mc.hasSingleplayerServer()?mc.getSingleplayerServer().getRecipeManager():mc.level.getRecipeManager();
        var saved=project.recipes.getOrDefault(id,state.applied.recipes.get(id));if(saved!=null)try{CompatibilityRecipes.restore(saved);SimpleRecipes.restore(saved);}catch(RuntimeException ignored){}
        return manager.byKey(ResourceLocation.parse(id)).map(holder->describe(holder,manager)).orElse(null);
    }
    private static Loaded describe(RecipeHolder<?> holder,RecipeManager manager){
        var mc=Minecraft.getInstance();String type=BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer()).toString();
        var encoded=encodedRecipes.get(manager,ScriptIndex.serverGeneration,holder.id(),()->{try{var data=GenericFormatter.encodeRecipe(holder).getAsJsonObject();data.addProperty("type",type);preserveLoadConditions(holder.id(),data);return new Loaded(holder.id().toString(),type,data,null);}catch(Exception e){return new Loaded(holder.id().toString(),type,null,"配方无法序列化："+e.getMessage());}});
        if(encoded.data()==null)return encoded;
        try{JsonObject data=encoded.data();
                String error;
                if(mc.hasSingleplayerServer()&&SimpleRecipes.enabled()&&(RecipeAdapters.get(type)==null||RecipeAdapters.get(type) instanceof SimpleRecipeAdapter||RecipeAdapters.get(type) instanceof dev.kubejseditor.recipe.compat.CompatibilityAdapter)){try{CompatibilityRecipes.discover(type,data,holder.value());}catch(RuntimeException problem){return new Loaded(holder.id().toString(),type,data,"需要确认字段映射："+problem.getMessage());}}
                if(RecipeAdapters.get(type) instanceof dev.kubejseditor.recipe.compat.CompatibilityAdapter compatibility){error=!mc.hasSingleplayerServer()?"通用适配需要本机服务端":String.join("；",compatibility.validate(data));if(error.isBlank())error=null;}
                else if(!mc.hasSingleplayerServer()&&(RecipeAdapters.get(type) instanceof SimpleRecipeAdapter||RecipeAdapters.get(type)==null&&SimpleRecipes.enabled()))error="简单适配需要本机服务端核对原始来源；远程配方保留只读";
                else if(RecipeAdapters.get(type)==null&&SimpleRecipes.enabled()){
                    try{SimpleRecipeAdapter.bindings(data);SimpleRecipes.discover(data);error=SimpleRecipes.boundary(holder.id().toString(),data);}catch(Exception problem){error="尚未识别：打开后依据 JEI 自动分析；"+problem.getMessage();}
                }else {error=RecipeAdapters.get(type)==null?"此类型尚无无损编辑适配，可查看或按 ID 删除":RecipeAdapters.get(type) instanceof SimpleRecipeAdapter?SimpleRecipes.boundary(holder.id().toString(),data):importBoundary(data);}
                return new Loaded(holder.id().toString(),type,data,error);
        }catch(Exception e){return new Loaded(holder.id().toString(),type,encoded.data(),"尚未识别："+e.getMessage());}
    }
    private static void preserveLoadConditions(ResourceLocation id,JsonObject data){
        var server=Minecraft.getInstance().getSingleplayerServer();if(server==null)return;
        var source=server.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"recipe/"+id.getPath()+".json"));
        if(source.isPresent())try(var reader=source.get().openAsReader()){
            var raw=JsonParser.parseReader(reader);if(raw.isJsonObject())LoadConditions.restoreFromSource(data,raw.getAsJsonObject());
        }catch(Exception error){throw new IllegalArgumentException("无法核对配方来源条件："+id,error);}
    }
    /** Custom ingredient codecs and OR ingredients remain opaque; never flatten to an example stack. */
    public static String importBoundary(JsonObject data){
        if(data.has("type")&&VintageProcessing.dynamic(data.get("type").getAsString()))return VintageProcessing.explanation(data.get("type").getAsString());
        if(hasOpaqueIngredient(data,""))return "包含复合/自定义原料，表单暂不能无损编辑";
        return null;
    }
    private static boolean hasOpaqueIngredient(JsonElement e,String key){
        if(Set.of("ingredient","base","addition","template").contains(key)&&e.isJsonArray())return true;
        if(key.equals("ingredients")&&e.isJsonArray())for(JsonElement child:e.getAsJsonArray())if(child.isJsonArray())return true;
        if(e.isJsonObject()){
            JsonObject o=e.getAsJsonObject();
            if(o.has("type")&&o.get("type").getAsString().equals("neoforge:components")&&(!o.has("items")||!o.get("items").isJsonPrimitive()))return true;
            if(o.has("type")&&Set.of("ingredient","ingredients","template","base","addition").contains(key)&&!Set.of("neoforge:single","neoforge:tag","neoforge:components").contains(o.get("type").getAsString())&&o.get("type").getAsString().contains(":"))return true;
            for(var c:o.entrySet())if(!c.getKey().equals("components")&&hasOpaqueIngredient(c.getValue(),c.getKey()))return true;
        }
        if(e.isJsonArray())for(JsonElement v:e.getAsJsonArray())if(hasOpaqueIngredient(v,key.equals("sequence")?"":key))return true;
        return false;
    }
    public static void validateRecipe(EditorProject.RecipeDraft draft,Set<String> pendingItems){if(draft.registryTarget!=null){RegistryDataSources.validate(draft);return;}validateRecipe(CraftingGrid.generated(draft),pendingItems,draft.forceNative);}
    /** Raw patches use native data validation and reject edits entirely discarded by the native codec. */
    @SuppressWarnings({"rawtypes","unchecked"}) public static void validatePatch(EditorProject.RecipeDraft current,JsonObject replacement){
        if(!Objects.equals(current.data.get("type"),replacement.get("type")))throw new IllegalArgumentException("不能更换补丁来源类型");
        var next=WorkspaceState.copy(current,EditorProject.RecipeDraft.class);next.data=replacement.deepCopy();next.forceNative=true;validateRecipe(next,Set.of());if(current.data.equals(replacement))return;
        if(next.registryTarget!=null?!RegistryDataSources.shouldExist(next):!RecipeLoadConditions.matches(LoadConditions.effective(next.data,next)))return;
        var access=Minecraft.getInstance().getSingleplayerServer().registryAccess();var ops=access.createSerializationContext(JsonOps.INSTANCE);
        com.mojang.serialization.Codec codec=next.registryTarget!=null?registryCodec(next.registryTarget):BuiltInRegistries.RECIPE_SERIALIZER.get(ResourceLocation.parse(next.data.get("type").getAsString())).codec().codec();
        JsonObject before=LoadConditions.body(next.registryTarget==null?current.data:next.registryTarget.body(current.data)),after=LoadConditions.body(next.registryTarget==null?replacement:next.registryTarget.body(replacement));
        Object a=codec.parse(ops,before).getOrThrow(),b=codec.parse(ops,after).getOrThrow();boolean conditionsChanged=!LoadConditions.ignored(next)&&!Objects.equals(current.data.get(LoadConditions.KEY),replacement.get(LoadConditions.KEY));
        if(!conditionsChanged&&codec.encodeStart(ops,a).getOrThrow().equals(codec.encodeStart(ops,b).getOrThrow()))throw new IllegalArgumentException("修改未改变原生编码内容；字段可能未被模组读取，或加载条件当前被忽略");
    }
    private static com.mojang.serialization.Codec<Object> registryCodec(dev.kubejseditor.model.RegistryEntryTarget target){try{return RegistryDataSources.codec(target);}catch(ReflectiveOperationException e){throw new IllegalArgumentException("注册表数据接口不可用",e);}}
    public static void validateRecipe(JsonObject data,Set<String> pendingItems){validateRecipe(data,pendingItems,false);}
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void validateRecipe(JsonObject data,Set<String> pendingItems,boolean forceNative){
        if(!RecipeLoadConditions.matches(data))return;
        data=LoadConditions.body(data);
        String type=data.get("type").getAsString();RecipeAdapter adapter=RecipeAdapters.get(type);
        if(adapter==null&&!forceNative)throw new IllegalArgumentException("未适配的配方类型："+type);
        if(!EditorCompatibility.hasRecipeType(type))throw new IllegalArgumentException(EditorCompatibility.unavailable(type));
        if(!forceNative){List<String> errors=adapter.validate(data);if(!errors.isEmpty())throw new IllegalArgumentException(String.join("；",errors));
        if(adapter instanceof SimpleRecipeAdapter simple){for(var slot:simple.slots(data))validateSimpleSlot(slot,pendingItems);}else if(!(adapter instanceof dev.kubejseditor.recipe.compat.CompatibilityAdapter))validateReferences(data,"",pendingItems);}
        var mc=Minecraft.getInstance();var serializer=BuiltInRegistries.RECIPE_SERIALIZER.get(ResourceLocation.parse(type));
        if(serializer==null)throw new IllegalArgumentException("模组未提供此配方类型："+type);
        JsonObject validation = data.deepCopy(); substitutePending(validation,pendingItems);
        var registries=mc.hasSingleplayerServer()?mc.getSingleplayerServer().registryAccess():mc.level.registryAccess();var ops=registries.createSerializationContext(JsonOps.INSTANCE);
        Object decoded=serializer.codec().codec().parse(ops,validation).getOrThrow();
        if(!forceNative&&adapter instanceof SimpleRecipeAdapter simple){com.mojang.serialization.Codec codec=serializer.codec().codec();JsonObject encoded=((JsonElement)codec.encodeStart(ops,decoded).getOrThrow()).getAsJsonObject();simple.verifyRoundTrip(validation,encoded);}
        if(!forceNative&&adapter instanceof dev.kubejseditor.recipe.compat.CompatibilityAdapter compat){com.mojang.serialization.Codec codec=serializer.codec().codec();JsonObject encoded=((JsonElement)codec.encodeStart(ops,decoded).getOrThrow()).getAsJsonObject();compat.presentation(validation).verifyRoundTrip(encoded);}
    }
    private static void validateSimpleSlot(RecipeCanvas.Slot slot,Set<String> pending){
        var value=slot.get().getAsJsonObject();for(String key:List.of("id","item","fluid","items"))if(value.has(key)&&value.get(key).isJsonPrimitive()){
            String id=value.get(key).getAsString();if(id.startsWith("#"))continue;ResourceLocation loc=ResourceLocation.tryParse(id);
            if(loc==null||!(slot.fluid()?BuiltInRegistries.FLUID.containsKey(loc):BuiltInRegistries.ITEM.containsKey(loc)||pending.contains(id)))throw new IllegalArgumentException("未知槽位资源："+id);
        }
    }
    private static void substitutePending(JsonElement data,Set<String> pending){
        if(data.isJsonObject())for(var e:new ArrayList<>(data.getAsJsonObject().entrySet())){
            if(e.getValue().isJsonPrimitive()&&e.getValue().getAsJsonPrimitive().isString()&&pending.contains(e.getValue().getAsString()))data.getAsJsonObject().addProperty(e.getKey(),"minecraft:paper");
            else substitutePending(e.getValue(),pending);
        }
        if(data.isJsonArray())for(JsonElement e:data.getAsJsonArray())substitutePending(e,pending);
    }
    private static void validateReferences(JsonElement data,String key,Set<String> pending){
        if(data.isJsonObject())for(var e:data.getAsJsonObject().entrySet()){
            if(Set.of("id","item").contains(e.getKey())&&e.getValue().isJsonPrimitive()){
                String id=e.getValue().getAsString();ResourceLocation loc=ResourceLocation.tryParse(id);
                boolean fluid=data.getAsJsonObject().has("amount");
                if(loc==null||!(fluid?BuiltInRegistries.FLUID.containsKey(loc):BuiltInRegistries.ITEM.containsKey(loc)||pending.contains(id)))throw new IllegalArgumentException("未知资源："+id);
            }else validateReferences(e.getValue(),e.getKey(),pending);
        }
        if(data.isJsonArray())for(JsonElement e:data.getAsJsonArray())validateReferences(e,key,pending);
    }
    private static void adoptLegacy()throws IOException{
        if(Files.exists(root().resolve(WorkspaceState.FILE))||!Files.exists(root().resolve("kubejs/config/kubejs_editor/project.v1.backup.json")))return;
        // Exact generated bytes establish ownership; unmatched legacy entries remain pending.
        try{
            var generated=KjsGenerator.generate(project).files();
            for(var entry:generated.entrySet())if(!entry.getKey().equals(ProjectStore.FILE)&&Files.exists(root().resolve(entry.getKey()))&&Arrays.equals(entry.getValue(),Files.readAllBytes(root().resolve(entry.getKey()))))state.managedHashes.put(entry.getKey(),WorkspaceState.hash(entry.getValue()));
            if(state.managedHashes.containsKey(WorkspaceState.GENERATED.getFirst())){state.applied.recipes.putAll(project.recipes);project.recipes.clear();}
            if(state.managedHashes.containsKey(WorkspaceState.GENERATED.get(1))){state.applied.items.putAll(project.items);state.applied.assets.putAll(project.assets);project.items.clear();project.assets.clear();}
            FileTransaction.atomicWrite(root().resolve(WorkspaceState.FILE),WorkspaceState.bytes(state));ProjectStore.saveDraft(root(),project);
        }catch(IllegalArgumentException ignored){/* unfinished templates stay pending */}
    }
    public static FileTransaction.Review review()throws IOException{
        long started=System.nanoTime();
        if(busy())throw new IOException("正在应用，请稍候");
        if(state().activation!=null)throw new IOException(state.activation.restart?"存在等待重启的批次，请重启或回滚":"上次提交尚未确认成功，请先整批回滚");
        CreateConfigs.persistInto(project());WorkspaceState next=WorkspaceState.copy(state(),WorkspaceState.class);next.activation=null;
        for(var script:project.scripts.values())if(script.takeover)next.takeOver(root(),script.path,project);
        EditorProject p=next.merge(project);normalizeAttributes(p);Set<String> pending=new HashSet<>();
        for(var item:p.items.values()){
            if(!item.register&&BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(item.id)))item.nameKey=BuiltInRegistries.ITEM.get(ResourceLocation.parse(item.id)).getDescriptionId();
            if(item.register)for(var member:RegistrationMembers.of(item))if(member.registry().equals("item")&&!registered(member.registry(),member.id()))pending.add(member.id());
        }
        List<ValidationIssue> issues=validateContent(p);
        if(!issues.isEmpty())throw new IllegalArgumentException(issues.stream().map(ValidationIssue::text).collect(java.util.stream.Collectors.joining("\n\n")));
        Map<String,byte[]> files=generate(next,p,project);Map<String,byte[]> configOutput=CreateConfigs.output(root());files.putAll(configOutput);Set<String> restoredResources=EditorResources.augment(root(),p,files);
        for(var script:project.scripts.values()){
            Path path=EditorScripts.checked(root(),script.path);String actual=WorkspaceState.hash(path),desired=WorkspaceState.hash(script.text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(!actual.equals(script.baseHash)&&!(state.activation!=null&&desired.equals(actual)&&actual.equals(state.activation.hashes.get(script.path))))throw new IOException("脚本已被外部修改，请重新打开后合并："+script.path);
            files.put(script.path,script.text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        validateScripts(files);
        var initial=FileTransaction.review(root(),files,false,false);
        boolean restart=requiresRestart()||!pending.isEmpty()||initial.changes().stream().anyMatch(c->{String path=relative(c.path());return path.startsWith("kubejs/startup_scripts/")||path.startsWith(dev.kubejseditor.model.RegistryEntryTarget.DATA)||configOutput.containsKey(path)||configOutput.containsKey(path.replace('/',java.io.File.separatorChar));});
        boolean assets=initial.changes().stream().anyMatch(c->relative(c.path()).startsWith("kubejs/assets/"));
        boolean client=initial.changes().stream().anyMatch(c->relative(c.path()).startsWith("kubejs/client_scripts/"));
        if(initial.changes().isEmpty()&&project.recipes.isEmpty()&&project.items.isEmpty()&&project.scripts.isEmpty()&&project.configs.isEmpty())return initial;
        files.put(ProjectStore.FILE,WorkspaceState.bytes(project));
        var activation=new WorkspaceState.Activation();activation.desired=p;activation.submitted=WorkspaceState.copy(project,EditorProject.class);activation.restart=restart;activation.client=client;activation.resources=assets;activation.serverGeneration=ScriptIndex.serverGeneration;activation.clientGeneration=ScriptIndex.clientGeneration;
        for(var e:files.entrySet()){
            String path=e.getKey().replace('\\','/');if(!path.equals(ProjectStore.FILE))activation.hashes.put(path,WorkspaceState.hash(e.getValue()));
            if(restoredResources.contains(path)||e.getValue()==null&&path.startsWith(dev.kubejseditor.model.RegistryEntryTarget.DATA))next.managedHashes.remove(path);
            else if(path.startsWith("kubejs/")&&(!path.startsWith("kubejs/config/")||path.startsWith(dev.kubejseditor.model.RegistryEntryTarget.DATA))&&!project.scripts.containsKey(path)&&!next.manualFiles.contains(path))next.managedHashes.put(path,WorkspaceState.hash(e.getValue()));
        }
        next.activation=activation;files.put(WorkspaceState.FILE,WorkspaceState.bytes(next));
        if(restart)files.put(RESTART_MARKER,restartMarker());
        FileTransaction.Review result=FileTransaction.review(root(),files,restart,assets);prepared.clear();prepared.put(result,next);lastPreparationMs=(System.nanoTime()-started)/1_000_000;return result;
    }
    private static String relative(Path p){return root().relativize(p).toString().replace('\\','/');}
    /** The single-rule API shares the same atomic, one-reload batch transaction. */
    public static void revokeRecipe(String id,java.util.function.Consumer<ReloadResult> result)throws IOException{revokeChanges(Set.of(id),Set.of(),result);}
    public static void revokeChanges(Collection<String> recipeIds,Collection<String> itemIds,java.util.function.Consumer<ReloadResult> result)throws IOException{
        long started=System.nanoTime();requireEditable();if(!canApply())throw new IOException("只能删除本机单人实例中的编辑器修改");
        var recipes=new LinkedHashSet<>(recipeIds);var items=new LinkedHashSet<>(itemIds);
        var next=WorkspaceState.copy(state(),WorkspaceState.class);var desired=next.withoutRules(recipes,items);
        var refs=deletionBlockers(recipes,items);if(!refs.isEmpty())throw new IOException("注册内容仍被引用，请先处理相关内容：\n"+String.join("\n",refs));
        Map<String,byte[]> files=new LinkedHashMap<>();Set<String> restoredResources=Set.of();var retainedAssets=new LinkedHashMap<String,String>();if(!items.isEmpty())retainedAssets.putAll(state().retainedAssets(project(),items));retainedAssets.keySet().removeAll(desired.assets.keySet());
        if(recipes.stream().anyMatch(id->state().applied.recipes.get(id).registryTarget==null)){
            String base=WorkspaceState.GENERATED.getFirst(),target=next.outputPath(base);
            if(!next.isManaged(target))throw new IOException("无法定位生成文件，请检查来源："+target);
            if(!Objects.equals(next.managedHashes.get(target),WorkspaceState.hash(FileTransaction.checkedPath(root(),target))))throw new IOException("生成文件已被外部修改，停止删除以保留手写内容："+target);
            var content=next.forScript(desired,new EditorProject(),base);content.items.clear();content.assets.clear();content.configs.clear();
            files.put(target,routeScript(KjsGenerator.generate(content).files().get(base),base,target));
        }
        boolean registryRestart=recipes.stream().anyMatch(id->state().applied.recipes.get(id).registryTarget!=null);
        if(registryRestart){var registryContent=new EditorProject();desired.recipes.forEach((id,d)->{if(d.registryTarget!=null)registryContent.recipes.put(id,d);});var generated=KjsGenerator.generate(registryContent).files();generated.forEach((path,bytes)->{if(path.startsWith(dev.kubejseditor.model.RegistryEntryTarget.DATA))files.put(path,bytes);});checkRegistryFiles(next,files);}
        if(!items.isEmpty()){
            var owner=WorkspaceState.copy(next,WorkspaceState.class);owner.managedHashes.keySet().removeIf(p->!p.startsWith("kubejs/startup_scripts/")&&!p.startsWith("kubejs/client_scripts/")&&!p.startsWith("kubejs/assets/"));
            var content=WorkspaceState.copy(desired,EditorProject.class);content.recipes.clear();content.configs.clear();content.scripts.clear();
            files.putAll(generate(owner,content,new EditorProject()));restoredResources=EditorResources.augment(root(),content,files,retainedAssets.keySet());
            files.put("kubejs/config/kubejs_editor/applied-items.json",WorkspaceState.bytes(desired.items.values().stream().filter(i->i.register).flatMap(i->RegistrationMembers.of(i).stream()).map(RegistrationMembers.Member::id).distinct().toList()));
        }
        validateScripts(files);saveDraft();var remaining=WorkspaceState.copy(project(),EditorProject.class);
        for(String id:items)new PendingOperation("item",id,"物品修改").cancel(remaining);
        retainedAssets.forEach(remaining.assets::putIfAbsent);
        files.put(ProjectStore.FILE,WorkspaceState.bytes(remaining));
        var activation=new WorkspaceState.Activation();activation.desired=desired;activation.submitted=new EditorProject();activation.revokedRecipes.addAll(recipes);activation.revokedItems.addAll(items);activation.restart=!items.isEmpty()||registryRestart;
        for(String id:items){var old=state().applied.items.get(id);if(old.register)for(var member:RegistrationMembers.of(old))activation.deletedItems.add(member.id());}
        activation.serverGeneration=ScriptIndex.serverGeneration;activation.clientGeneration=ScriptIndex.clientGeneration;
        var changes=FileTransaction.review(root(),files,activation.restart,false).changes();activation.resources=changes.stream().anyMatch(c->relative(c.path()).startsWith("kubejs/assets/"));activation.client=changes.stream().anyMatch(c->relative(c.path()).startsWith("kubejs/client_scripts/"));
        for(var entry:files.entrySet())if(!entry.getKey().equals(ProjectStore.FILE)){
            activation.hashes.put(entry.getKey(),WorkspaceState.hash(entry.getValue()));
            if(entry.getValue()==null||restoredResources.contains(entry.getKey()))next.managedHashes.remove(entry.getKey());else next.managedHashes.put(entry.getKey(),WorkspaceState.hash(entry.getValue()));
        }
        next.activation=activation;files.put(WorkspaceState.FILE,WorkspaceState.bytes(next));if(activation.restart)files.put(RESTART_MARKER,restartMarker());
        var review=FileTransaction.review(root(),files,activation.restart,activation.resources);prepared.clear();prepared.put(review,next);lastPreparationMs=(System.nanoTime()-started)/1_000_000;
        apply(review);copyPending(remaining);
        if(activation.restart)result.accept(new ReloadResult(true,"已保存批量删除："+recipes.size()+" 条配方、"+items.size()+" 条物品规则，统一等待重启；其他待办保留"));else reloadResult(activation.resources,result);
    }
    /** Check the final batch, so deleting a recipe and its owned item together is possible. */
    public static List<String> deletionBlockers(Collection<String> recipeIds,Collection<String> itemIds)throws IOException{
        if(itemIds.isEmpty())return List.of();
        var desired=state().withoutRules(recipeIds,itemIds);var pending=WorkspaceState.copy(project(),EditorProject.class);for(String id:itemIds)new PendingOperation("item",id,"物品修改").cancel(pending);
        var targets=new LinkedHashSet<String>();for(String id:itemIds){var item=state().applied.items.get(id);if(item.register)for(var member:RegistrationMembers.of(item))targets.add(member.id());}
        if(targets.isEmpty())return List.of();var refs=new LinkedHashSet<String>();
        for(var d:desired.recipes.values())if(d.enabled&&!d.operation.equals("remove")){references(refs,"保留配方："+d.id,d.data,targets);if(d.grid!=null)references(refs,"保留配方："+d.id,d.grid.cells,targets);}
        for(var d:pending.recipes.values())if(d.enabled&&!d.operation.equals("remove")){references(refs,"待办配方："+d.id,d.data,targets);if(d.grid!=null)references(refs,"待办配方："+d.id,d.grid.cells,targets);}
        for(String id:recipeIds){var old=state().applied.recipes.get(id);boolean added="add".equals(old.origin)||(old.origin==null&&"add".equals(old.operation));if(!added){if(old.source==null)refs.add("原来源快照缺失，需先单独撤销配方以核对引用："+id);else references(refs,"将恢复的原来源："+id,old.source,targets);}}
        for(var recipe:recipes()){
            var applied=state().applied.recipes.get(recipe.id());
            if(applied==null)references(refs,"游戏配方："+recipe.id(),recipe.data(),targets);
        }
        for(var script:EditorScripts.list())if(!script.managed()){var lines=Files.readAllLines(EditorScripts.checked(root(),script.path()));for(int n=0;n<lines.size();n++)for(String id:targets)if(lines.get(n).contains(id))refs.add("源码引用："+script.path()+":"+(n+1)+" → "+id);}
        for(var script:pending.scripts.values())for(String id:targets)if(script.text.contains(id))refs.add("源码待办："+script.path+" → "+id);
        var allItems=new LinkedHashMap<>(desired.items);allItems.putAll(pending.items);for(var item:allItems.values())if(RegistrationMembers.crop(item)&&targets.contains(item.block.crop.output))refs.add("作物产物引用："+item.id);
        return List.copyOf(refs);
    }
    private static void references(Set<String> refs,String label,JsonElement data,Set<String> targets){for(String id:ItemReferences.matching(data,targets))refs.add(label+" → "+id);}
    /** Report dependencies without mutating recipes, scripts or the authoring queue. */
    private static boolean registered(String registry,String id){var key=ResourceLocation.parse(id);return switch(registry){case "item"->BuiltInRegistries.ITEM.containsKey(key);case "block"->BuiltInRegistries.BLOCK.containsKey(key);case "block_entity_type"->BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(key);default->false;};}
    public static List<String> itemDeletionBlockers(String id)throws IOException{
        var item=state().applied.items.get(id);Set<String> references=new LinkedHashSet<>();
        for(var member:item==null?List.of(new RegistrationMembers.Member("item",id)):RegistrationMembers.of(item))references.addAll(singleDeletionBlockers(member.id(),id));
        return List.copyOf(references);
    }
    private static List<String> singleDeletionBlockers(String id,String owner)throws IOException{
        EditorProject.requireId(id);Set<String> refs=new LinkedHashSet<>();
        for(String recipe:dev.kubejseditor.model.ItemReferences.recipes(state().applied,id))refs.add("已应用配方："+recipe);
        for(String recipe:dev.kubejseditor.model.ItemReferences.recipes(project(),id))refs.add("待办配方："+recipe);
        for(var recipe:recipes())if(recipe.data()!=null&&!state().applied.recipes.containsKey(recipe.id())&&dev.kubejseditor.model.ItemReferences.contains(recipe.data(),id))refs.add("游戏配方："+recipe.id());
        for(var script:EditorScripts.list())if(!script.managed()){
            List<String> lines=Files.readAllLines(EditorScripts.checked(root(),script.path()));
            for(int line=0;line<lines.size();line++)if(lines.get(line).contains(id))refs.add("请检查源码引用："+script.path()+":"+(line+1));
        }
        for(var script:project().scripts.values())if(script.text.contains(id))refs.add("源码待办："+script.path);
        for(var other:content().items.values())if(!other.id.equals(owner)&&RegistrationMembers.crop(other)&&other.block.crop.output.equals(id))refs.add("作物产物引用："+other.id);
        return List.copyOf(refs);
    }
    /** Backward-compatible registration-only entry, using the shared batch implementation. */
    public static void deleteRegisteredItem(String id)throws IOException{
        if(!state().canDeleteRegisteredItem(id))throw new IllegalArgumentException("只能删除编辑器拥有的注册物品");
        revokeChanges(Set.of(),Set.of(id),ignored->{});
    }
    private static byte[] routeScript(byte[] text,String base,String target){
        if(text!=null&&!target.equals(base)){int version=Integer.parseInt(target.substring(target.lastIndexOf('_')+1,target.length()-3));return new String(text,java.nio.charset.StandardCharsets.UTF_8).replace("// priority: -100\n","// priority: "+(-1000-version)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);}return text;
    }
    private static Map<String,byte[]> generate(WorkspaceState owner,EditorProject content,EditorProject draft)throws IOException{
        Map<String,byte[]> files=owner.output(content,draft);
        checkRegistryFiles(owner,files);
        for(var old:owner.managedHashes.entrySet())if(!owner.manualFiles.contains(old.getKey())){
            String current=WorkspaceState.hash(FileTransaction.checkedPath(root(),old.getKey()));
            if(!old.getValue().equals(current)&&!(state.activation!=null&&current.equals(state.activation.hashes.get(old.getKey()))))throw new IOException("生成文件已被外部修改，请从已有修改转为手写或恢复备份："+old.getKey());
            if(!files.containsKey(old.getKey())&&!old.getKey().startsWith("kubejs/config/"))files.put(old.getKey(),null);
        }
        return files;
    }
    private static void checkRegistryFiles(WorkspaceState owner,Map<String,byte[]> files)throws IOException{
        String prefix=dev.kubejseditor.model.RegistryEntryTarget.DATA;
        for(String path:new ArrayList<>(files.keySet()))if(path.startsWith(prefix)&&Files.exists(FileTransaction.checkedPath(root(),path))&&!owner.isManaged(path))throw new IOException("原生数据覆盖文件已存在且不属于编辑器："+path);
        for(var old:owner.managedHashes.entrySet())if(old.getKey().startsWith(prefix)){
            if(!old.getValue().equals(WorkspaceState.hash(FileTransaction.checkedPath(root(),old.getKey()))))throw new IOException("原生数据文件已被外部修改，未覆盖："+old.getKey());
            if(!files.containsKey(old.getKey()))files.put(old.getKey(),null);
        }
    }
    private static void normalizeAttributes(EditorProject project){
        for(var item:project.items.values())if(!item.register&&!item.replaceAttributes&&!item.attributes.isEmpty()){
            ResourceLocation id=ResourceLocation.parse(item.id);if(!BuiltInRegistries.ITEM.containsKey(id))continue;
            Map<String,EditorProject.AttributeDraft> entries=new LinkedHashMap<>();
            var current=BuiltInRegistries.ITEM.get(id).getDefaultInstance().getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
            for(var e:current.modifiers()){
                var a=new EditorProject.AttributeDraft();a.attribute=e.attribute().unwrapKey().orElseThrow().location().toString();a.id=e.modifier().id().toString();a.amount=e.modifier().amount();a.operation=e.modifier().operation().getSerializedName();a.slot=e.slot().getSerializedName();entries.put(a.id,a);
            }
            item.attributes.forEach(a->entries.put(a.id,a));item.attributes=new ArrayList<>(entries.values());item.replaceAttributes=true;
        }
        for(var item:project.items.values())if(!item.register){ResourceLocation id=ResourceLocation.tryParse(item.id);if(id!=null&&BuiltInRegistries.ITEM.containsKey(id))item.nameKey=BuiltInRegistries.ITEM.get(id).getDescriptionId();}
    }
    private static boolean isPreviouslyManaged(String id){try{return JsonParser.parseString(Files.readString(root().resolve("kubejs/config/kubejs_editor/applied-items.json"))).getAsJsonArray().asList().stream().anyMatch(e->e.getAsString().equals(id));}catch(Exception e){return false;}}
    public static void validateScripts(Map<String,byte[]> files){
        for(var e:files.entrySet())if(e.getKey().endsWith(".js")&&e.getValue()!=null){
            var cx=new ContextFactory().enter();
            new Parser(cx).parse(new String(e.getValue(),java.nio.charset.StandardCharsets.UTF_8),e.getKey(),1);
        }
    }
    public static Path apply(FileTransaction.Review review)throws IOException{
        if(!canApply())throw new IOException("远程服务器中仅允许草稿和导出");
        if(busy())throw new IOException("正在应用，请勿重复提交");
        requireEditable();
        WorkspaceState next=prepared.remove(review);if(next==null)throw new IOException("预览已失效，请重新预览");
        if(review.changes().isEmpty())throw new IOException("没有需要应用的文件更改");
        for(var change:review.changes()){
            String path=relative(change.path());var submitted=next.activation==null?null:next.activation.submitted.scripts.get(path);
            String expected=submitted!=null?submitted.baseHash:state.isManaged(path)?state.managedHashes.get(path):null;
            if(expected!=null&&!expected.equals(WorkspaceState.hash(change.path())))throw new IOException("文件已被外部修改，停止提交以保留内容："+path);
        }
        System.out.println("[KubeJS Editor] 应用文件事务："+review.changes().size()+" 个文件");
        applying=true;try{lastBackup=FileTransaction.apply(review);state=next;activationError="";System.out.println("[KubeJS Editor] 文件事务已写入："+lastBackup);return lastBackup;}finally{applying=false;}
    }
    private static Path backup()throws IOException{
        if(lastBackup==null){Path pointer=root().resolve("kubejs/config/kubejs_editor/last-transaction.txt");if(Files.exists(pointer))lastBackup=FileTransaction.checkedPath(root(),Files.readString(pointer));}
        if(lastBackup==null)throw new IOException("当前没有可恢复的本机事务");return lastBackup;
    }
    public static void rollback()throws IOException{
        if(!canApply()||busy())throw new IOException("当前不能回滚");Path b=backup();
        boolean restart=Files.readAllLines(b.resolve("manifest.tsv")).stream().anyMatch(s->s.contains("/startup_scripts/")||s.contains(dev.kubejseditor.model.RegistryEntryTarget.DATA)||s.endsWith(".toml"));
        System.err.println("[KubeJS Editor] 整批回滚："+b);
        FileTransaction.rollback(root(),b);EditorProject restored=ProjectStore.read(root());copyPending(restored);state=WorkspaceState.read(root());activationError="";CreateConfigs.restore(project);
        if(restart)FileTransaction.atomicWrite(root().resolve(RESTART_MARKER),restartMarker());
        var diagnostic=new EditorDiagnostics.Diagnostic();diagnostic.operation="rollback";diagnostic.phase="complete";diagnostic.outcome="success";diagnostic.message="已恢复事务前文件";
        try{diagnostic.backup=root().relativize(b).toString().replace('\\','/');}catch(Exception ignored){diagnostic.backup=b.toString();}
        EditorDiagnostics.record(root(),diagnostic);
    }
    private static void copyPending(EditorProject value){project.recipes=value.recipes;project.items=value.items;project.configs=value.configs;project.assets=value.assets;project.scripts=value.scripts;}
    public static Path export()throws IOException{
        var review=review();Path folder=root().resolve("kubejs/config/kubejs_editor/exports/"+System.currentTimeMillis());List<String> deletions=new ArrayList<>();
        // Export the complete generated rules, not just the files different on this machine.
        WorkspaceState owner=WorkspaceState.copy(prepared.getOrDefault(review,state()),WorkspaceState.class);owner.managedHashes.clear();owner.managedHashes.putAll(state().managedHashes);owner.manualFiles.forEach(owner.managedHashes::remove);
        Map<String,byte[]> output=generate(owner,content(),project());EditorResources.augment(root(),content(),output);output.put(ProjectStore.FILE,WorkspaceState.bytes(project));
        for(String manual:owner.manualFiles)if(!output.containsKey(manual)&&Files.exists(EditorScripts.checked(root(),manual)))output.put(manual,Files.readAllBytes(EditorScripts.checked(root(),manual)));
        for(var d:project.scripts.values())output.put(d.path,d.text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        validateScripts(output);
        for(var e:output.entrySet()){if(e.getValue()==null){deletions.add(e.getKey());continue;}Path f=folder.resolve(e.getKey());Files.createDirectories(f.getParent());Files.write(f,e.getValue());}
        if(!deletions.isEmpty())Files.write(folder.resolve("DELETE_FILES.txt"),deletions);return folder;
    }
    private static List<String> verifyActivation()throws IOException{
        var a=state.activation;if(a==null)return List.of();
        List<String> notes=new ArrayList<>();
        if(!ProjectStore.JSON.toJsonTree(ProjectStore.read(root())).equals(ProjectStore.JSON.toJsonTree(project)))throw new IOException("草稿文件与当前编辑不同，请先保存草稿或重新载入后合并");
        for(var e:a.hashes.entrySet())if(!e.getKey().equals(ProjectStore.FILE)&&!e.getValue().equals(WorkspaceState.hash(FileTransaction.checkedPath(root(),e.getKey()))))throw new IOException("待生效文件发生变化："+e.getKey());
        List<String> errors=new ArrayList<>(ScriptIndex.errors(dev.latvian.mods.kubejs.script.ConsoleJS.SERVER));
        if(a.client||a.restart)errors.addAll(ScriptIndex.errors(dev.latvian.mods.kubejs.script.ConsoleJS.CLIENT));
        if(a.restart)errors.addAll(ScriptIndex.errors(dev.latvian.mods.kubejs.script.ConsoleJS.STARTUP));
        if(!errors.isEmpty())throw new IOException(String.join("\n",errors));
        var server=Minecraft.getInstance().getSingleplayerServer();if(server==null)throw new IOException("本机世界尚未加载");
        for(var d:a.desired.recipes.values())if(d.enabled){
            if(d.registryTarget!=null){RegistryDataSources.verify(d,RegistryDataSources.shouldExist(d));continue;}
            if(state.frozen.getOrDefault(WorkspaceState.GENERATED.getFirst(),Set.of()).contains(d.id)&&!a.submitted.recipes.containsKey(d.id))continue;
            var found=server.getRecipeManager().byKey(ResourceLocation.parse(d.id));
            boolean shouldExist=!d.operation.equals("remove")&&RecipeLoadConditions.matches(CraftingGrid.generated(d));
            if(shouldExist?found.isEmpty():found.isPresent())throw new IOException("配方生效状态不符合预览："+d.id);
            if(shouldExist)verifyRecipeContent(d,found.orElseThrow());
            else if(!d.operation.equals("remove"))notes.add("加载条件未满足："+d.id+"（本轮跳过，规则已保存）");
        }
        // A revoked rule must really be gone or truly restored; checking only desired recipes
        // would report success even when KJS left the edited recipe in place.
        var scriptSources=a.revokedRecipes!=null&&!a.revokedRecipes.isEmpty()?ScriptIndex.recipeSources():Map.<String,String>of();
        if(a.revokedRecipes!=null)for(String id:a.revokedRecipes){
            var old=state.applied.recipes.get(id);
            if(old!=null&&old.registryTarget!=null){boolean added="add".equals(old.origin);if(!added&&old.source==null)throw new IOException("注册表撤销缺少来源快照："+id);var restored=WorkspaceState.copy(old,EditorProject.RecipeDraft.class);restored.operation="replace";restored.loadConditionPolicy=LoadConditions.PRESERVE;if(!added)restored.data=old.source.deepCopy();RegistryDataSources.verify(restored,!added&&RegistryDataSources.shouldExist(restored));continue;}
            var found=server.getRecipeManager().byKey(ResourceLocation.parse(id));
            if(old==null){notes.add("撤销记录缺少原规则，仅核对当前状态："+id);continue;}
            boolean editorAdded="add".equals(old.origin)||(old.origin==null&&"add".equals(old.operation));
            if(editorAdded){
                if(found.isPresent()){
                    String external=scriptSources.get(id);
                    if(external==null)throw new IOException("撤销新增后配方仍然存在："+id);
                    notes.add("撤销新增后由外部脚本恢复此 ID："+id+" · "+external);
                }
            }else{
                if(old.source!=null&&!RecipeLoadConditions.matches(old.source)){if(found.isEmpty()){notes.add("来源加载条件未满足，撤销后保持未加载："+id);continue;}String external=scriptSources.get(id);if(external==null)throw new IOException("条件未满足的来源仍被注册："+id);notes.add("原资源条件未满足，但外部脚本恢复了来源："+id+" · "+external);}
                if(found.isEmpty())throw new IOException("撤销后来源配方未恢复："+id);
                if(old.source!=null&&old.source.isJsonObject()){
                    try{verifyRecipeData(id,old.source,found.orElseThrow());}
                    catch(IOException mismatch){notes.add("恢复的来源内容与快照不同（可能由其他脚本再次修改）："+id+" · "+mismatch.getMessage());}
                }else notes.add("来源快照缺失，仅核对存在性："+id);
            }
        }
        for(var config:a.desired.configs){
            var entry=CreateConfigs.all().stream().filter(e->e.config().getModId().equals(config.modId)&&e.config().getFileName().equals(config.file)&&e.config().getType().name().equals(config.type)&&e.path().equals(config.path)&&(config.target==null||e.file().equals(root().resolve(config.target).normalize()))).findFirst();
            if(entry.isEmpty()||!Objects.equals(entry.get().current(),entry.get().parse(config.value)))throw new IOException("模组参数尚未生效或所属世界未加载："+config.modId+" / "+config.path);
        }
        for(var item:a.desired.items.values())if(item.register&&!state.frozen.getOrDefault(WorkspaceState.GENERATED.get(1),Set.of()).contains(item.id))for(var member:RegistrationMembers.of(item))if(!registered(member.registry(),member.id()))throw new IOException("注册尚未生效："+member.registry()+" / "+member.id());
        if(a.deletedItems!=null)for(String id:a.deletedItems){
            ResourceLocation loc=ResourceLocation.parse(id);
            if(BuiltInRegistries.ITEM.containsKey(loc)||BuiltInRegistries.BLOCK.containsKey(loc)||BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(loc))throw new IOException("删除尚未生效，物品或方块仍被其他来源注册："+id);
        }
        if(a.revokedItems!=null)for(String id:a.revokedItems){var old=state.applied.items.get(id);if(old!=null&&!old.register&&!registered("item",id))throw new IOException("原有物品在重启后缺失："+id);}
        return List.copyOf(notes);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void verifyRecipeContent(EditorProject.RecipeDraft draft,RecipeHolder<?> actual)throws IOException{
        verifyRecipeData(draft.id,CraftingGrid.generated(draft),actual);
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void verifyRecipeData(String id,JsonObject expectedData,RecipeHolder<?> actual)throws IOException{
        expectedData=LoadConditions.body(expectedData);
        try{
            if(expectedData==null||!expectedData.has("type"))throw new IOException("缺少配方类型");
            var serializer=BuiltInRegistries.RECIPE_SERIALIZER.get(ResourceLocation.parse(expectedData.get("type").getAsString()));
            if(serializer==null)throw new IOException("未知的配方序列化器："+expectedData.get("type").getAsString());
            com.mojang.serialization.Codec codec=serializer.codec().codec();var ops=Minecraft.getInstance().level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            Object expected=codec.parse(ops,expectedData).getOrThrow(),loaded=codec.parse(ops,GenericFormatter.encodeRecipe(actual)).getOrThrow();
            if(!codec.encodeStart(ops,expected).getOrThrow().equals(codec.encodeStart(ops,loaded).getOrThrow()))throw new IOException("配方内容与预期不同："+id+"\n预期："+codec.encodeStart(ops,expected).getOrThrow()+"\n实际："+codec.encodeStart(ops,loaded).getOrThrow());
        }catch(IOException error){throw error;}catch(Exception error){throw new IOException("无法确认配方实际内容："+id+" · "+error.getMessage(),error);}
    }
    private static String conditionSummary(List<String> notes){long count=notes.stream().filter(n->n.startsWith("加载条件未满足：")).count();return count==0?"":" · "+count+" 条因加载条件未满足而跳过";}
    private static List<String> promote()throws IOException{
        List<String> notes=verifyActivation();var next=WorkspaceState.copy(state,WorkspaceState.class);EditorProject remaining=WorkspaceState.copy(project,EditorProject.class);next.promote(remaining);
        Path b=backup();FileTransaction.commitMetadata(root(),b,Map.of(WorkspaceState.FILE,WorkspaceState.bytes(next),ProjectStore.FILE,WorkspaceState.bytes(remaining)));
        state=next;copyPending(remaining);activationError="";CreateConfigs.clear();CreateConfigs.restore(project);
        return notes;
    }
    public static void pollActivation(){
        if(project==null||busy()||!canApply()||state.activation==null||System.currentTimeMillis()-lastPoll<2000)return;
        lastPoll=System.currentTimeMillis();
        var a=state.activation;
        boolean restarted=a.boot!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        boolean loaded=restarted?ScriptIndex.completedGeneration>0&&ScriptIndex.clientGeneration>0:!a.restart&&ScriptIndex.completedGeneration>a.serverGeneration&&(!a.client||ScriptIndex.clientGeneration>a.clientGeneration);
        if(loaded)try{promote();}catch(Exception error){
            activationError=error.getMessage();
            var diagnostic=new EditorDiagnostics.Diagnostic();diagnostic.operation="activation";diagnostic.phase="poll";diagnostic.outcome="failure";diagnostic.message=error.getMessage();
            diagnostic.error=EditorDiagnostics.Error.of(error);diagnostic.throwable=error;fillActivationDiagnostic(diagnostic);EditorDiagnostics.record(root(),diagnostic);
        }
    }
    public static String logs(){
        StringBuilder b=new StringBuilder();for(String file:List.of("startup.log","server.log","client.log"))try{
            Path p=root().resolve("logs/kubejs/"+file);if(!Files.exists(p))continue;List<String> lines=Files.readAllLines(p);b.append(file).append('\n');
            lines.stream().skip(Math.max(0,lines.size()-60)).forEach(s->b.append(s).append('\n'));
        }catch(IOException e){b.append(e.getMessage());}return b.toString();
    }
    public record ReloadResult(boolean success,String message){}
    public static void reload(boolean resources,java.util.function.Consumer<String> status)throws IOException{reloadResult(resources,r->status.accept(r.message()));}
    public static void reloadResult(boolean resources,java.util.function.Consumer<ReloadResult> result)throws IOException{
        if(!canApply())throw new IOException("只能重载本机单人实例");
        if(busy())throw new IOException("正在应用，请稍候");
        if(requiresRestart())throw new IOException("存在尚未生效的启动期或配置更改，请重启游戏");
        Minecraft mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();applying=true;reloadRequests++;
        long generation=ScriptIndex.serverGeneration;
        var diagnostic=new EditorDiagnostics.Diagnostic();
        diagnostic.operation="reload";diagnostic.phase="server-reload";diagnostic.generations.before=generation;
        diagnostic.timings.put("lastPreparationMs",lastPreparationMs);long reloadStarted=System.nanoTime();
        server.execute(()->{
            try{
                server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((v,error)->{diagnostic.timings.put("serverReloadMs",(System.nanoTime()-reloadStarted)/1_000_000);long queued=System.nanoTime();mc.execute(()->{
                    diagnostic.timings.put("clientQueueWaitMs",(System.nanoTime()-queued)/1_000_000);
                    diagnostic.generations.after=ScriptIndex.serverGeneration;
                    if(error!=null){completeFailedReload(server,error,generation,resources,diagnostic,result);return;}
                    completeNormalReload(mc,resources,generation,diagnostic,result);
                });});
            }catch(Throwable failure){
                mc.execute(()->failReload(diagnostic,"无法开始配方重载："+shortError(failure),failure,result));
            }
        });
    }
    private static void completeNormalReload(Minecraft mc,boolean resources,long generation,EditorDiagnostics.Diagnostic diagnostic,java.util.function.Consumer<ReloadResult> result){
        try{
            if(ScriptIndex.serverGeneration<=generation)throw new IOException("未收到本次 KubeJS 加载结果");
            List<String> errors=ScriptIndex.errors(dev.latvian.mods.kubejs.script.ConsoleJS.SERVER);
            diagnostic.kubeJsErrors.addAll(errors);
            if(!errors.isEmpty())throw new IOException(String.join("\n",errors));
            if(state().activation!=null&&state().activation.client)dev.latvian.mods.kubejs.client.KubeJSClient.reloadClientScripts();
            java.util.concurrent.CompletableFuture<Void> refresh=resources?mc.reloadResourcePacks():java.util.concurrent.CompletableFuture.completedFuture(null);
            refresh.whenComplete((ignored,failure)->mc.execute(()->{
                if(failure!=null){failReload(diagnostic,"资源刷新失败："+failure.getMessage(),failure,result);return;}
                try{
                    boolean activated=state().activation!=null;
                    int revoked=activated&&state().activation.revokedRecipes!=null?state().activation.revokedRecipes.size():0;long verifyStarted=System.nanoTime();
                    fillActivationDiagnostic(diagnostic);
                    if(activated)diagnostic.notes.addAll(promote());
                    diagnostic.timings.put("verificationCommitMs",(System.nanoTime()-verifyStarted)/1_000_000);
                    diagnostic.phase="complete";diagnostic.outcome="success";diagnostic.promoted=activated;diagnostic.verification.passed=true;
                    diagnostic.message=(revoked>0?"已撤销 "+revoked+" 条配方修改并统一重载，其他待办保留":activated?"应用成功 · 操作记录已清空":"重载成功")+conditionSummary(diagnostic.notes);
                    EditorDiagnostics.record(root(),diagnostic);
                    applying=false;
                    result.accept(new ReloadResult(true,diagnostic.message));
                }catch(Exception e){failReload(diagnostic,e.getMessage(),e,result);}
            }));
        }catch(Exception e){failReload(diagnostic,e.getMessage(),e,result);}
    }
    private static void completeFailedReload(net.minecraft.server.MinecraftServer server,Throwable error,long generation,boolean resources,EditorDiagnostics.Diagnostic diagnostic,java.util.function.Consumer<ReloadResult> result){
        diagnostic.phase="reload-future";diagnostic.error=EditorDiagnostics.Error.of(error);diagnostic.throwable=error;
        diagnostic.kubeJsErrors.addAll(ScriptIndex.errors(dev.latvian.mods.kubejs.script.ConsoleJS.SERVER));
        if(ScriptIndex.serverGeneration<=generation){failReload(diagnostic,"本次 KubeJS 加载未完成："+shortError(error),error,result);return;}
        if(!isDatapackSyncFailure(error)){failReload(diagnostic,"配方重载失败："+shortError(error),error,result);return;}
        // The OnDatapackSyncEvent listener failed before PlayerList sent the recipe/tag packets.
        // Verify the authoritative server state first; never compensate over a bad editor result.
        try{
            List<String> notes=verifyActivation();
            diagnostic.verification.passed=true;diagnostic.verification.detail="通过";
            diagnostic.notes.addAll(notes);
        }catch(Exception verify){
            diagnostic.verification.passed=false;diagnostic.verification.detail=verify.getMessage();
            failReload(diagnostic,"重载被第三方同步监听中断，且目标状态核对失败："+verify.getMessage()+"（重载异常："+shortError(error)+"）",error,result);
            return;
        }
        diagnostic.compensation.attempted=true;
        Minecraft mc=Minecraft.getInstance();
        try{
            server.execute(()->{
                try{
                    syncLocalPlayers(server,diagnostic.compensation);
                    mc.execute(()->{
                        diagnostic.notes.add("OnDatapackSyncEvent 第三方监听器异常；已补发标签、配方和配方书；其他同步监听器未重放");
                        diagnostic.outcome="compensated-success";diagnostic.phase="compensated";
                        completeCompensatedReload(resources,diagnostic,result);
                    });
                }catch(Exception send){
                    mc.execute(()->{
                        diagnostic.compensation.error=send.getMessage();
                        failReload(diagnostic,"第三方同步中断后补偿客户端配方/标签失败："+send.getMessage(),send,result);
                    });
                }
            });
        }catch(Throwable failure){
            failReload(diagnostic,"无法开始客户端补偿同步："+shortError(failure),failure,result);
        }
    }
    private static void completeCompensatedReload(boolean resources,EditorDiagnostics.Diagnostic diagnostic,java.util.function.Consumer<ReloadResult> result){
        Minecraft mc=Minecraft.getInstance();
        java.util.concurrent.CompletableFuture<Void> refresh=resources?mc.reloadResourcePacks():java.util.concurrent.CompletableFuture.completedFuture(null);
        refresh.whenComplete((ignored,failure)->mc.execute(()->{
            if(failure!=null){failReload(diagnostic,"资源刷新失败："+failure.getMessage(),failure,result);return;}
            try{
                boolean activated=state().activation!=null;
                int revoked=activated&&state().activation.revokedRecipes!=null?state().activation.revokedRecipes.size():0;
                fillActivationDiagnostic(diagnostic);
                if(activated)diagnostic.notes.addAll(promote());
                diagnostic.promoted=activated;
                diagnostic.message=(revoked>0?"已撤销 "+revoked+" 条配方修改并补偿客户端同步":activated?"已应用修改并补偿客户端同步":"已重载并补偿客户端同步")+conditionSummary(diagnostic.notes);
                EditorDiagnostics.record(root(),diagnostic);
                applying=false;
                result.accept(new ReloadResult(true,diagnostic.message+"；包内第三方同步异常已记录，建议重启核查其他数据包内容"));
            }catch(Exception e){failReload(diagnostic,e.getMessage(),e,result);}
        }));
    }
    private static void failReload(EditorDiagnostics.Diagnostic diagnostic,String message,Throwable error,java.util.function.Consumer<ReloadResult> result){
        diagnostic.outcome="failure";diagnostic.message=message==null?"重载失败":message;
        if(diagnostic.error==null&&error!=null)diagnostic.error=EditorDiagnostics.Error.of(error);
        if(diagnostic.throwable==null)diagnostic.throwable=error;
        fillActivationDiagnostic(diagnostic);
        EditorDiagnostics.record(root(),diagnostic);
        activationError=diagnostic.message;
        applying=false;
        result.accept(new ReloadResult(false,"重载失败："+diagnostic.message+" · 草稿保留，可回滚（更多 → 最近诊断）"));
    }
    /** PlayerList.reloadResources posts OnDatapackSyncEvent before sending recipes/tags. */
    private static boolean isDatapackSyncFailure(Throwable error){
        return stackMentions(error,new HashSet<>(),"net.minecraft.server.players.PlayerList","reloadResources")
                ||stackMentions(error,new HashSet<>(),"net.neoforged.neoforge.event.OnDatapackSyncEvent",null);
    }
    private static boolean stackMentions(Throwable error,Set<Throwable> seen,String className,String method){
        if(error==null||!seen.add(error))return false;
        for(StackTraceElement frame:error.getStackTrace())if(frame.getClassName().equals(className)&&(method==null||frame.getMethodName().equals(method)))return true;
        for(Throwable suppressed:error.getSuppressed())if(stackMentions(suppressed,seen,className,method))return true;
        return stackMentions(error.getCause(),seen,className,method);
    }
    /**
     * Replay exactly the packets that PlayerList.reloadResources would have sent after the
     * failing event. Other datapack-sync listeners cannot be replayed safely; the diagnostic
     * and UI say so, and a restart remains the only full synchronization.
     */
    private static void syncLocalPlayers(net.minecraft.server.MinecraftServer server,EditorDiagnostics.Compensation compensation)throws IOException{
        if(server==null)throw new IOException("本机服务端不存在");
        List<ServerPlayer> players=server.getPlayerList().getPlayers();
        if(players.isEmpty())throw new IOException("没有可同步的本机玩家");
        ClientboundUpdateTagsPacket tags=new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(server.registries()));
        ClientboundUpdateRecipesPacket recipes=new ClientboundUpdateRecipesPacket(server.getRecipeManager().getOrderedRecipes());
        for(ServerPlayer player:players){
            player.connection.send(tags);compensation.tagsSent=true;
            player.connection.send(recipes);compensation.recipesSent=true;
            player.getRecipeBook().sendInitialRecipeBook(player);
        }
        compensation.players=players.size();
    }
    private static void fillActivationDiagnostic(EditorDiagnostics.Diagnostic diagnostic){
        var activation=state==null?null:state.activation;
        if(activation==null)return;
        LinkedHashSet<String> ids=new LinkedHashSet<>(),types=new LinkedHashSet<>();
        if(activation.revokedRecipes!=null)ids.addAll(activation.revokedRecipes);
        if(activation.desired!=null){
            ids.addAll(activation.desired.recipes.keySet());
            for(var recipe:activation.desired.recipes.values())if(recipe.data!=null&&recipe.data.has("type"))types.add(recipe.data.get("type").getAsString());
        }
        diagnostic.recipeIds=new ArrayList<>(ids);diagnostic.serializers=new ArrayList<>(types);
        if(lastBackup!=null)try{diagnostic.backup=root().relativize(lastBackup).toString().replace('\\','/');}catch(Exception ignored){diagnostic.backup=lastBackup.toString();}
    }
    private static String shortError(Throwable error){
        if(error==null)return "未知错误";
        Throwable root=error;
        while(root.getCause()!=null&&root.getCause()!=root)root=root.getCause();
        String message=root.getMessage();
        return root.getClass().getSimpleName()+(message==null||message.isBlank()?"":": "+message);
    }
}
