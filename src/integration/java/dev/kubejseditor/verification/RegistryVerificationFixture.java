package dev.kubejseditor.verification;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** An unrelated native registry proves discovery does not depend on a Forbidden Arcanus whitelist. */
public final class RegistryVerificationFixture {
    public static final ResourceKey<Registry<Program>> KEY=ResourceKey.createRegistryKey(ResourceLocation.parse("kubejs_editor_verification:process"));
    public record Program(Ingredient input,ItemStack result,int duration){
        private static final Codec<Program> FORMAT=RecordCodecBuilder.create(i->i.group(Ingredient.CODEC.fieldOf("input").forGetter(Program::input),ItemStack.CODEC.fieldOf("result").forGetter(Program::result),Codec.intRange(1,1000000).fieldOf("duration").forGetter(Program::duration)).apply(i,Program::new));
        private static final Codec<Program> NETWORK=RecordCodecBuilder.create(i->i.group(Ingredient.CODEC.fieldOf("input").forGetter(Program::input),ItemStack.CODEC.fieldOf("result").forGetter(Program::result)).apply(i,(input,result)->new Program(input,result,1)));
    }
    public static void register(IEventBus bus){bus.addListener((DataPackRegistryEvent.NewRegistry event)->event.dataPackRegistry(KEY,Program.FORMAT,Program.NETWORK));}
    private RegistryVerificationFixture(){}
}
