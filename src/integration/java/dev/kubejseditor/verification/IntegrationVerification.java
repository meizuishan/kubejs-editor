package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.config.CreateConfigs;
import dev.kubejseditor.storage.FileTransaction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.nio.file.*;
import java.util.*;

/** A separate dev-only mod. Never included in the deliverable Jar. */
@Mod("kubejs_editor_verification")
public final class IntegrationVerification {
    private final List<String> failures=new ArrayList<>();private int checks;
    public IntegrationVerification(){
        // Match production startup: do not scan third-party dev-only GameTests with optional APIs.
        if(Boolean.getBoolean("kubejs.editor.verifyPackStartup")||Boolean.getBoolean("kubejs.editor.verifyPackRevoke")||Boolean.getBoolean("kubejs.editor.verifyReloadResilience")||Boolean.getBoolean("kubejs.editor.researchPack")){net.minecraft.SharedConstants.IS_RUNNING_IN_IDE=false;System.setProperty("neoforge.enableGameTest","false");}
        System.out.println("[KubeJS Editor Verification] flags: packRevoke="+Boolean.getBoolean("kubejs.editor.verifyPackRevoke")
                +", reloadResilience="+Boolean.getBoolean("kubejs.editor.verifyReloadResilience")
                +", undo="+Boolean.getBoolean("kubejs.editor.verifyUndo")
                +", packStartup="+Boolean.getBoolean("kubejs.editor.verifyPackStartup"));
        if(Boolean.getBoolean("kubejs.editor.verify"))NeoForge.EVENT_BUS.addListener(this::started);
    }
    private void check(boolean pass,String message){checks++;if(!pass)failures.add(message);}
    private void started(ServerStartedEvent event){
        var server=event.getServer();Path root=FMLPaths.GAMEDIR.get().toAbsolutePath();
        try {
            var expected=JsonParser.parseString(Files.readString(root.resolve("expected-recipe-ids.json"))).getAsJsonArray();
            for(var id:expected){String key=id.getAsString();boolean exists=server.getRecipeManager().byKey(ResourceLocation.parse(key)).isPresent();check(key.equals("minecraft:diamond_block")?!exists:exists,"Recipe application: "+key);}
            var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse("kubejs:abyss_test_item"));
            check(item!=Items.AIR,"Custom item registered");check(item.getDefaultInstance().getMaxStackSize()==16,"Registered stack size");
            check(item.getDefaultInstance().getBurnTime(RecipeType.SMELTING)==400,"Fuel duration loaded");
            check(Items.WOODEN_SWORD.getDefaultInstance().getMaxDamage()==123,"Existing item durability changed");
            var food=BuiltInRegistries.ITEM.get(ResourceLocation.parse("kubejs:abyss_test_food")).getDefaultInstance().get(DataComponents.FOOD);
            check(food!=null&&food.nutrition()==6,"Food values loaded");
            check(Items.APPLE.getDefaultInstance().get(DataComponents.FOOD).nutrition()==9,"Existing food values changed");
            var attrs=item.getDefaultInstance().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
            check(attrs.modifiers().stream().anyMatch(e->e.modifier()!=null&&e.modifier().amount()==3),"Attribute component generated correctly");
            var custom=server.getRecipeManager().byKey(ResourceLocation.parse("kubejs_editor:test_new_item"));
            if(custom.isPresent()){
                @SuppressWarnings("unchecked") var recipe=(Recipe<CraftingInput>)custom.get().value();
                var input=CraftingInput.of(1,1,List.of(new ItemStack(Items.DIAMOND)));
                check(recipe.matches(input,server.overworld()),"New recipe matches actual crafting input");
                ItemStack result=recipe.assemble(input,server.registryAccess());check(result.is(item)&&result.getCount()==2,"Actual crafting output and count");
            }
            var configs=CreateConfigs.list();check(!configs.isEmpty(),"Create public config enumeration");
            for(String mod:List.of("createaddition","createdieselgenerators","create_enchantment_industry","create_dragons_plus"))check(configs.stream().anyMatch(e->e.config().getModId().equals(mod)),"Addon config discovered: "+mod);
            var efficiency=configs.stream().filter(e->e.key().contains("generator_efficiency")).findFirst();
            if(efficiency.isPresent()){check(efficiency.get().parse("0.8") instanceof Double,"Addon double parameter parsed");boolean rejected=false;try{efficiency.get().parse("8");}catch(IllegalArgumentException expectedError){rejected=true;}check(rejected,"Addon config range enforced");}
            var config=configs.stream().filter(e->e.editable()&&e.valueType()==Integer.class&&e.current() instanceof Number&&e.spec().test(((Number)e.current()).intValue()+1)).findFirst();
            if(config.isPresent()){
                var entry=config.get();byte[] original=Files.readAllBytes(entry.config().getFullPath());
                CreateConfigs.queue(entry,String.valueOf(((Number)entry.current()).intValue()+1));var plan=FileTransaction.review(root,CreateConfigs.output(root),true,false);Path backup=FileTransaction.apply(plan);
                check(!Arrays.equals(original,Files.readAllBytes(entry.config().getFullPath())),"Config staged value written");
                FileTransaction.rollback(root,backup);check(Arrays.equals(original,Files.readAllBytes(entry.config().getFullPath())),"Config rollback preserves original bytes");CreateConfigs.clear();
            }else check(false,"Editable Create config fixture: "+configs.stream().limit(12).map(e->e.key()+"="+e.current()+"/"+e.valueType()).toList());
            int recipeCount=server.getRecipeManager().getRecipes().size();
            server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((unused,error)->server.execute(()->{
                check(error==null,"Standard reload completed");check(server.getRecipeManager().getRecipes().size()==recipeCount,"Reload does not duplicate recipes");
                finish(root,server);
            }));
        }catch(Throwable error){failures.add(error.toString());finish(root,server);}
    }
    private void finish(Path root,net.minecraft.server.MinecraftServer server){
        try{
            for(String log:List.of("startup.log","server.log")){
                Path file=root.resolve("logs/kubejs/"+log);if(Files.exists(file))check(!Files.readString(file).contains("[ERROR]"),"No KubeJS errors in "+log);
            }
            JsonObject result=new JsonObject();result.addProperty("checks",checks);result.addProperty("passed",failures.isEmpty());JsonArray errors=new JsonArray();failures.forEach(errors::add);result.add("failures",errors);
            Files.writeString(root.resolve("verification-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
            System.out.println("ABYSS_EDITOR_VERIFICATION "+result);
        }catch(Exception error){error.printStackTrace();}finally{server.halt(false);}
    }
}
