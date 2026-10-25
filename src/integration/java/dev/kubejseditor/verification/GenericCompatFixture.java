package dev.kubejseditor.verification;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.api.*;
import mezz.jei.api.ingredients.*;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.registries.*;
import java.util.*;

/** One framework fixture, not an adaptation of a named machine; excluded from all production artifacts. */
@JeiPlugin
public final class GenericCompatFixture implements IModPlugin {
    public static final String MOD="kubejs_editor_verification";
    public static boolean enabled(){return Boolean.getBoolean("kubejs.editor.verifyCompat015")||Boolean.getBoolean("kubejs.editor.verifyLoadConditions")||Boolean.getBoolean("kubejs.editor.verifyBatch");}
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,MOD);
    private static final DeferredRegister<net.minecraft.world.item.crafting.RecipeType<?>> TYPES=DeferredRegister.create(Registries.RECIPE_TYPE,MOD);
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeType<?>,net.minecraft.world.item.crafting.RecipeType<GenericRecipe>> TYPE=TYPES.register("generic",()->new net.minecraft.world.item.crafting.RecipeType<>(){});
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<GenericRecipe>> SERIALIZER=SERIALIZERS.register("generic",()->new RecipeSerializer<>(){public MapCodec<GenericRecipe> codec(){return GenericRecipe.CODEC;}public StreamCodec<RegistryFriendlyByteBuf,GenericRecipe> streamCodec(){return ByteBufCodecs.fromCodecWithRegistries(GenericRecipe.CODEC.codec());}});
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeType<?>,net.minecraft.world.item.crafting.RecipeType<ConvertedRecipe>> CONVERTED_TYPE=TYPES.register("converted",()->new net.minecraft.world.item.crafting.RecipeType<>(){});
    public static final DeferredHolder<RecipeSerializer<?>,RecipeSerializer<ConvertedRecipe>> CONVERTED_SERIALIZER=SERIALIZERS.register("converted",()->new RecipeSerializer<>(){public MapCodec<ConvertedRecipe> codec(){return ConvertedRecipe.CODEC;}public StreamCodec<RegistryFriendlyByteBuf,ConvertedRecipe> streamCodec(){return ByteBufCodecs.fromCodecWithRegistries(ConvertedRecipe.CODEC.codec());}});
    public static final DeferredRegister<MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>> CONDITIONS=DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS,MOD);
    public static final DeferredHolder<MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>,MapCodec<FeatureCondition>> FEATURE=CONDITIONS.register("feature",()->RecordCodecBuilder.mapCodec(i->i.group(Codec.BOOL.fieldOf("enabled").forGetter(FeatureCondition::enabled)).apply(i,FeatureCondition::new)));
    public record FeatureCondition(boolean enabled) implements net.neoforged.neoforge.common.conditions.ICondition {public boolean test(IContext context){return enabled;}public MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition> codec(){return FEATURE.get();}}
    public static void register(IEventBus bus){SERIALIZERS.register(bus);TYPES.register(bus);CONDITIONS.register(bus);}
    public record Token(ResourceLocation key,int quantity){public static final Codec<Token> CODEC=RecordCodecBuilder.create(i->i.group(ResourceLocation.CODEC.fieldOf("key").forGetter(Token::key),Codec.intRange(1,1000000).fieldOf("quantity").forGetter(Token::quantity)).apply(i,Token::new));}
    public record Gate(int work,boolean enabled){public static final Codec<Gate> CODEC=RecordCodecBuilder.create(i->i.group(Codec.intRange(1,100000).fieldOf("work").forGetter(Gate::work),Codec.BOOL.fieldOf("enabled").forGetter(Gate::enabled)).apply(i,Gate::new));}
    public record GenericRecipe(SizedIngredient feedstock,SizedFluidIngredient bath,ItemStack product,Token extra,Gate settings,Gate conditions) implements Recipe<CraftingInput>{
        public static final MapCodec<GenericRecipe> CODEC=RecordCodecBuilder.mapCodec(i->i.group(SizedIngredient.NESTED_CODEC.fieldOf("feedstock").forGetter(GenericRecipe::feedstock),SizedFluidIngredient.NESTED_CODEC.fieldOf("bath").forGetter(GenericRecipe::bath),ItemStack.STRICT_CODEC.fieldOf("product").forGetter(GenericRecipe::product),Token.CODEC.fieldOf("extra").forGetter(GenericRecipe::extra),Gate.CODEC.fieldOf("settings").forGetter(GenericRecipe::settings),Gate.CODEC.optionalFieldOf("conditions",new Gate(1,true)).forGetter(GenericRecipe::conditions)).apply(i,GenericRecipe::new));
        public boolean matches(CraftingInput input,Level level){return false;}public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){return product.copy();}public boolean canCraftInDimensions(int w,int h){return true;}public ItemStack getResultItem(HolderLookup.Provider registries){return product;}public RecipeSerializer<?> getSerializer(){return SERIALIZER.get();}public net.minecraft.world.item.crafting.RecipeType<?> getType(){return TYPE.get();}
    }
    public static final IIngredientType<Token> TOKEN=new IIngredientType<>(){public Class<? extends Token> getIngredientClass(){return Token.class;}};
    public record ConvertedRecipe(GenericRecipe payload) implements Recipe<CraftingInput>{
        public static final MapCodec<ConvertedRecipe> CODEC=GenericRecipe.CODEC.xmap(ConvertedRecipe::new,ConvertedRecipe::payload);
        public boolean matches(CraftingInput input,Level level){return false;}public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries){return payload.product.copy();}public boolean canCraftInDimensions(int w,int h){return true;}public ItemStack getResultItem(HolderLookup.Provider registries){return payload.product;}public RecipeSerializer<?> getSerializer(){return CONVERTED_SERIALIZER.get();}public net.minecraft.world.item.crafting.RecipeType<?> getType(){return CONVERTED_TYPE.get();}
    }
    public static final mezz.jei.api.recipe.RecipeType<RecipeHolder<ConvertedRecipe>> CONVERTED_JEI=mezz.jei.api.recipe.RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(MOD,"converted"));
    private static void convertedCategory(IRecipeCategoryRegistration r){var icon=r.getJeiHelpers().getGuiHelper().createDrawableItemStack(new ItemStack(Items.FURNACE));r.addRecipeCategories(new IRecipeCategory<RecipeHolder<ConvertedRecipe>>(){public mezz.jei.api.recipe.RecipeType<RecipeHolder<ConvertedRecipe>> getRecipeType(){return CONVERTED_JEI;}public Component getTitle(){return Component.literal("转换来源验证");}public int getWidth(){return 180;}public int getHeight(){return 86;}public mezz.jei.api.gui.drawable.IDrawable getIcon(){return icon;}public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder b,RecipeHolder<ConvertedRecipe> holder,IFocusGroup focus){var recipe=holder.value().payload();b.addSlot(RecipeIngredientRole.INPUT,4,24).addIngredients(recipe.feedstock().ingredient());b.addSlot(RecipeIngredientRole.INPUT,40,24).addIngredientsUnsafe(Arrays.asList(recipe.bath().getFluids()));b.addSlot(RecipeIngredientRole.INPUT,76,24).addIngredient(TOKEN,recipe.extra());b.addSlot(RecipeIngredientRole.INPUT,4,58).addIngredients(recipe.feedstock().ingredient());b.addSlot(RecipeIngredientRole.OUTPUT,130,24).addItemStack(recipe.product());}});}
    public static final mezz.jei.api.recipe.RecipeType<RecipeHolder<GenericRecipe>> JEI_TYPE=mezz.jei.api.recipe.RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(MOD,"generic"));
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath(MOD,"generic_fixture");}
    @Override public void registerIngredients(IModIngredientRegistration r){if(!enabled())return;var values=List.of(new Token(ResourceLocation.fromNamespaceAndPath(MOD,"red"),4),new Token(ResourceLocation.fromNamespaceAndPath(MOD,"blue"),7));r.register(TOKEN,values,new IIngredientHelper<>(){public IIngredientType<Token> getIngredientType(){return TOKEN;}public String getDisplayName(Token t){return "测试资源 · "+t.key();}public String getUniqueId(Token t,UidContext c){return t.toString();}public ResourceLocation getResourceLocation(Token t){return t.key();}public Token copyIngredient(Token t){return t;}public String getErrorInfo(Token t){return t.toString();}},new IIngredientRenderer<>(){public void render(net.minecraft.client.gui.GuiGraphics g,Token t){g.fill(1,1,15,15,t.key().getPath().equals("blue")?0xFF5599FF:0xFFFF5555);}public List<Component> getTooltip(Token t,TooltipFlag f){return List.of(Component.literal("其他资源 "+t.quantity()));}},Token.CODEC);}
    @Override public void registerCategories(IRecipeCategoryRegistration r){if(!enabled())return;var icon=r.getJeiHelpers().getGuiHelper().createDrawableItemStack(new ItemStack(Items.CRAFTING_TABLE));r.addRecipeCategories(new IRecipeCategory<RecipeHolder<GenericRecipe>>(){public mezz.jei.api.recipe.RecipeType<RecipeHolder<GenericRecipe>> getRecipeType(){return JEI_TYPE;}public Component getTitle(){return Component.literal("通用结构验证");}public int getWidth(){return 180;}public int getHeight(){return 86;}public mezz.jei.api.gui.drawable.IDrawable getIcon(){return icon;}public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder,RecipeHolder<GenericRecipe> holder,IFocusGroup focus){var recipe=holder.value();builder.addSlot(RecipeIngredientRole.INPUT,4,24).addIngredients(recipe.feedstock().ingredient());builder.addSlot(RecipeIngredientRole.INPUT,40,24).addIngredientsUnsafe(Arrays.asList(recipe.bath().getFluids()));builder.addSlot(RecipeIngredientRole.INPUT,76,24).addIngredient(TOKEN,recipe.extra());builder.addSlot(RecipeIngredientRole.INPUT,4,58).addIngredients(recipe.feedstock().ingredient());builder.addSlot(RecipeIngredientRole.OUTPUT,130,24).addItemStack(recipe.product());}});if(Boolean.getBoolean("kubejs.editor.verifyBatch"))convertedCategory(r);}
    public static RecipeHolder<ConvertedRecipe> display(RecipeHolder<GenericRecipe> source,HolderLookup.Provider registries){
        var ops=registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var copy=ConvertedRecipe.CODEC.codec().parse(ops,GenericRecipe.CODEC.codec().encodeStart(ops,source.value()).getOrThrow()).getOrThrow();
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(MOD,"compat/source/"+source.id().getPath()),copy);
    }
    @Override public void registerRecipes(IRecipeRegistration r){
        var mc=net.minecraft.client.Minecraft.getInstance();if(!enabled()||mc.level==null)return;
        r.addRecipes(JEI_TYPE,mc.level.getRecipeManager().getAllRecipesFor(TYPE.get()));
        if(Boolean.getBoolean("kubejs.editor.verifyBatch"))mc.level.getRecipeManager().byKey(ResourceLocation.parse("bbb:mirror")).ifPresent(holder->{@SuppressWarnings("unchecked") var generic=(RecipeHolder<GenericRecipe>)(Object)holder;r.addRecipes(CONVERTED_JEI,List.of(display(generic,mc.level.registryAccess())));});
    }
}
