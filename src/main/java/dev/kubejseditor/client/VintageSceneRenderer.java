package dev.kubejseditor.client;

import dev.kubejseditor.recipe.VintageProcessing;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

/** Reuses installed JEI animations without packaging Vintage classes or textures. */
public final class VintageSceneRenderer {
    private static final Map<String,Object> scenes=new HashMap<>();
    private static final Set<String> failures=new HashSet<>();
    public static boolean draw(GuiGraphics graphics,String type,int x,int y){
        var mode=VintageProcessing.get(type);if(mode==null||failures.contains(type))return false;
        graphics.pose().pushPose();try{
            Object scene=scenes.get(type);if(scene==null){scene=Class.forName("dev.bluephs.createvintageneoforged.compat.jei.animated."+mode.animation()).getConstructor().newInstance();scenes.put(type,scene);}
            graphics.pose().translate(x,y,0);
            if(mode.animation().equals("AnimatedCurvingPress")){int head=switch(mode.path()){case "curving_concave"->2;case "curving_w_shaped"->3;case "curving_v_shaped"->4;default->1;};scene.getClass().getMethod("draw",GuiGraphics.class,int.class,int.class,int.class).invoke(scene,graphics,102,35,head);}
            else if(mode.animation().equals("AnimatedVacuumChamber"))scene.getClass().getMethod("draw",GuiGraphics.class,int.class,int.class,boolean.class).invoke(scene,graphics,102,30,mode.path().equals("pressurizing"));
            else if(mode.animation().equals("AnimatedHelve"))scene.getClass().getMethod("draw",GuiGraphics.class,int.class,int.class,int.class).invoke(scene,graphics,104,30,1);
            else scene.getClass().getMethod("draw",GuiGraphics.class,int.class,int.class).invoke(scene,graphics,102,35);
            return true;
        }catch(ReflectiveOperationException|LinkageError e){failures.add(type);org.slf4j.LoggerFactory.getLogger("KubeJS Editor").warn("Vintage scene unavailable for {}",type,e);return false;}
        finally{graphics.pose().popPose();}
    }
    public static Set<String> failures(){return Set.copyOf(failures);}
    private VintageSceneRenderer(){}
}
