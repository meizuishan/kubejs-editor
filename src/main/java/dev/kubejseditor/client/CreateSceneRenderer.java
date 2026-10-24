package dev.kubejseditor.client;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.fluids.FluidStack;
import dev.kubejseditor.recipe.FanProcessing;
import java.util.*;

/** Version-pinned bridge: reuses Create's actual JEI animation without embedding its assets. */
public final class CreateSceneRenderer {
    private static final Map<String,Object> animations=new HashMap<>();
    private static final Set<String> failed=new HashSet<>();
    public static boolean draw(GuiGraphics g,String type,int x,int y){
        return draw(g,type,x,y,List.of());
    }
    public static boolean draw(GuiGraphics g,String type,int x,int y,List<FluidStack> fluids){
        if(dev.kubejseditor.recipe.VintageProcessing.supports(type))return VintageSceneRenderer.draw(g,type,x,y);
        String name=switch(type){case "create:deploying"->"AnimatedDeployer";case "create:pressing","create:compacting"->"AnimatedPress";case "create:mixing"->"AnimatedMixer";case "create:cutting"->"AnimatedSaw";case "create:crushing"->"AnimatedCrushingWheels";case "create:milling"->"AnimatedMillstone";case "create:filling"->"AnimatedSpout";case "create:emptying"->"AnimatedItemDrain";case "create:mechanical_crafting"->"AnimatedCrafter";default->null;};
        if(failed.contains(type))return false;
        if(FanProcessing.supports(type))return fan(g,type,x,y);
        if(fluids.isEmpty()&&(type.equals("create:filling")||type.equals("create:emptying"))){
            try{if(type.equals("create:filling"))emptySpout(g,x,y);else block(g,"create:item_drain",x+97,y+54);return true;}
            catch(ReflectiveOperationException|LinkageError e){failed.add(type);org.slf4j.LoggerFactory.getLogger("KubeJS Editor").warn("Create empty fluid scene unavailable",e);return false;}
        }
        if(name==null)return false;
        boolean basin=type.equals("create:mixing")||type.equals("create:compacting");
        g.pose().pushPose();try{
            Object animation=animations.get(type);if(animation==null){Class<?> cls=Class.forName("com.simibubi.create.compat.jei.category.animations."+name);animation=name.equals("AnimatedPress")?cls.getConstructor(boolean.class).newInstance(basin):cls.getConstructor().newInstance();animations.put(type,animation);}
            if(name.equals("AnimatedSpout"))animation.getClass().getMethod("withFluids",List.class).invoke(animation,fluids);
            if(name.equals("AnimatedItemDrain"))animation.getClass().getMethod("withFluid",FluidStack.class).invoke(animation,fluids.isEmpty()?FluidStack.EMPTY:fluids.getFirst());
            g.pose().translate(x,y,0);g.pose().scale(1.25f,1.25f,1.25f);
            animation.getClass().getMethod("draw",GuiGraphics.class,int.class,int.class).invoke(animation,g,75,22);
            if(type.equals("create:pressing")){g.pose().pushPose();g.pose().translate(75,22,100);g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(-15.5f));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(22.5f));model(g,net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("create:depot")).defaultBlockState(),24,2);g.pose().popPose();}return true;
        }catch(ReflectiveOperationException|LinkageError e){failed.add(type);org.slf4j.LoggerFactory.getLogger("KubeJS Editor").warn("Create JEI animation unavailable for {}",type,e);return false;}
        finally{g.pose().popPose();}
    }
    /** Empty spouts use the same full partial-model assembly, projection and anchor as JEI's filled animation. */
    private static void emptySpout(GuiGraphics g,int x,int y)throws ReflectiveOperationException{
        g.pose().pushPose();try{g.pose().translate(x,y,0);g.pose().scale(1.25f,1.25f,1.25f);g.pose().translate(75,22,100);g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(-15.5f));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(22.5f));
            model(g,net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("create:spout")).defaultBlockState(),20,0);
            var partials=Class.forName("com.simibubi.create.AllPartialModels");float time=((Number)Class.forName("net.createmod.catnip.animation.AnimationTickHolder").getMethod("getRenderTime").invoke(null)).floatValue()%30;float offset=time<20?(float)Math.sin(time/20*Math.PI)*20:0;
            g.pose().pushPose();try{model(g,partials.getField("SPOUT_TOP").get(null),20,0);g.pose().translate(0,-3*offset/32,0);model(g,partials.getField("SPOUT_MIDDLE").get(null),20,0);g.pose().translate(0,-3*offset/32,0);model(g,partials.getField("SPOUT_BOTTOM").get(null),20,0);}finally{g.pose().popPose();}
            model(g,net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("create:depot")).defaultBlockState(),20,2);
        }finally{g.pose().popPose();}
    }
    private static void model(GuiGraphics g,Object state,double scale,double y)throws ReflectiveOperationException{
        var kinetics=Class.forName("com.simibubi.create.compat.jei.category.animations.AnimatedKinetics");var builder=Class.forName("net.createmod.catnip.gui.element.GuiGameElement$GuiRenderBuilder");Class<?> arg=state instanceof net.minecraft.world.level.block.state.BlockState?net.minecraft.world.level.block.state.BlockState.class:Class.forName("dev.engine_room.flywheel.lib.model.baked.PartialModel");
        Object element=kinetics.getMethod("defaultBlockElement",arg).invoke(null,state);builder.getMethod("scale",double.class).invoke(element,scale);builder.getMethod("atLocal",double.class,double.class,double.class).invoke(element,0.0,y,0.0);Class.forName("net.createmod.catnip.gui.element.RenderElement").getMethod("render",GuiGraphics.class).invoke(element,g);
    }
    private static boolean fan(GuiGraphics g,String type,int x,int y){
        g.pose().pushPose();try{
            // Same rotor, projection and catalyst distance as Create's ProcessingViaFanCategory.
            g.pose().translate(x+92,y+54,100);
            g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(-12.5f));
            g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(22.5f));
            var kinetics=Class.forName("com.simibubi.create.compat.jei.category.animations.AnimatedKinetics");
            var builder=Class.forName("net.createmod.catnip.gui.element.GuiGameElement$GuiRenderBuilder");
            var partial=Class.forName("dev.engine_room.flywheel.lib.model.baked.PartialModel");
            Object rotor=kinetics.getMethod("defaultBlockElement",partial).invoke(null,Class.forName("com.simibubi.create.AllPartialModels").getField("ENCASED_FAN_INNER").get(null));
            double angle=((Number)kinetics.getMethod("getCurrentAngle").invoke(null)).doubleValue()*16;
            builder.getMethod("rotateBlock",double.class,double.class,double.class).invoke(rotor,180.0,0.0,angle);
            builder.getMethod("scale",double.class).invoke(rotor,24.0);renderElement(g,rotor);
            Object casing=kinetics.getMethod("defaultBlockElement",net.minecraft.world.level.block.state.BlockState.class).invoke(null,net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("create:encased_fan")).defaultBlockState());
            builder.getMethod("rotateBlock",double.class,double.class,double.class).invoke(casing,0.0,180.0,0.0);
            builder.getMethod("scale",double.class).invoke(casing,24.0);renderElement(g,casing);
            var mode=FanProcessing.get(type);
            if(!mode.category().isEmpty()){
                Object category=animations.get(type);if(category==null){category=Class.forName("plus.dragons.createdragonsplus.integration.jei.category."+mode.category()).getMethod("create").invoke(null);animations.put(type,category);}
                var attached=category.getClass().getDeclaredMethod("renderAttachedBlock",GuiGraphics.class);attached.setAccessible(true);attached.invoke(category,g);
            }else{
                var elementClass=Class.forName("net.createmod.catnip.gui.element.GuiGameElement");
                Object catalyst=type.equals("create:splashing")?elementClass.getMethod("of",net.minecraft.world.level.material.Fluid.class).invoke(null,net.minecraft.world.level.material.Fluids.WATER):elementClass.getMethod("of",net.minecraft.world.level.block.state.BlockState.class).invoke(null,net.minecraft.world.level.block.Blocks.SOUL_FIRE.defaultBlockState());
                builder.getMethod("scale",double.class).invoke(catalyst,24.0);
                builder.getMethod("atLocal",double.class,double.class,double.class).invoke(catalyst,0.0,0.0,2.0);
                builder.getMethod("lighting",Class.forName("net.createmod.catnip.gui.ILightingSettings")).invoke(catalyst,kinetics.getField("DEFAULT_LIGHTING").get(null));renderElement(g,catalyst);
            }
            return true;
        }catch(ReflectiveOperationException|LinkageError e){failed.add(type);org.slf4j.LoggerFactory.getLogger("KubeJS Editor").warn("Create fan scene unavailable",e);return false;}
        finally{g.pose().popPose();}
    }
    private static void renderElement(GuiGraphics g,Object element)throws ReflectiveOperationException{Class.forName("net.createmod.catnip.gui.element.RenderElement").getMethod("render",GuiGraphics.class).invoke(element,g);}
    private static void block(GuiGraphics g,String id,int x,int y)throws ReflectiveOperationException{
        g.pose().pushPose();try{
            g.pose().translate(x,y,0);
            Class<?> kinetics=Class.forName("com.simibubi.create.compat.jei.category.animations.AnimatedKinetics");
            Class<?> builder=Class.forName("net.createmod.catnip.gui.element.GuiGameElement$GuiRenderBuilder");
            var state=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(id)).defaultBlockState();
            Object element=kinetics.getMethod("defaultBlockElement",net.minecraft.world.level.block.state.BlockState.class).invoke(null,state);
            builder.getMethod("rotateBlock",double.class,double.class,double.class).invoke(element,22.5,45.0,0.0);
            builder.getMethod("scale",double.class).invoke(element,30.0);
            Class.forName("net.createmod.catnip.gui.element.RenderElement").getMethod("render",GuiGraphics.class).invoke(element,g);
        }finally{g.pose().popPose();}
    }
    public static Set<String> failures(){return Set.copyOf(failed);}
}
