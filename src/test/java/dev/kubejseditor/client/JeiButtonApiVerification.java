package dev.kubejseditor.client;

import java.lang.reflect.*;
import java.util.*;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.registration.IAdvancedRegistration;

/** Runs the actual optional controller against the selected JEI, without starting Minecraft. */
public final class JeiButtonApiVerification {
    private static int checks;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Exception{
        var buttons=Class.forName("dev.kubejseditor.client.JeiRecipeButtons");
        var isEditor=buttons.getMethod("isEditorController",Object.class);
        var add=Arrays.stream(IAdvancedRegistration.class.getMethods()).filter(m->m.getName().equals("addRecipeButtonFactory")).findFirst();
        if(add.isEmpty()){
            check(IAdvancedRegistration.class.getMethod("addRecipeCategoryDecorator",Class.forName("mezz.jei.api.recipe.RecipeType"),Class.forName("mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator"))!=null,"Legacy JEI exposes the decorator registration API");
            check(Class.forName("net.neoforged.neoforge.client.event.ScreenEvent$MouseButtonPressed$Pre").getMethod("setCanceled",boolean.class)!=null,"Legacy entrance can handle clicks using a public event");
            // Resolving the production class also catches accidental references to newer button types.
            check(Boolean.FALSE.equals(isEditor.invoke(null,new Object[]{null})),"Editor loads without newer JEI controller classes");
            System.out.println("JEI button API: "+checks+" checks passed; legacy decorator path");return;
        }
        var factory=add.get().getParameterTypes()[0];var type=Arrays.stream(factory.getMethods()).filter(m->m.getName().equals("createButtonController")).findFirst().orElseThrow().getReturnType();
        var sourceType=Class.forName("dev.kubejseditor.client.JeiRecipeSources$Source");
        var source=sourceType.getConstructor(Class.forName("net.minecraft.resources.ResourceLocation"),String.class).newInstance(null,"API verification");
        var create=buttons.getDeclaredMethod("controller",Class.class,Class.forName("mezz.jei.api.gui.IRecipeLayoutDrawable"),sourceType);create.setAccessible(true);
        var controller=create.invoke(null,type,null,source);
        check(Boolean.TRUE.equals(isEditor.invoke(null,controller)),"Registered proxy identifies as the editor controller");
        check(controller.equals(controller)&&!controller.equals(new Object()),"Controller identity remains stable");
        check(controller.hashCode()==System.identityHashCode(controller),"Controller hash does not invoke JEI methods");
        check(controller.toString().contains("KubeJS Editor"),"Controller has a diagnostic name");
        var init=Arrays.stream(type.getMethods()).filter(m->m.getName().equals("initState")).findFirst().orElseThrow();var stateType=init.getParameterTypes()[0];var calls=new HashMap<String,Object>();
        var state=Proxy.newProxyInstance(stateType.getClassLoader(),new Class<?>[]{stateType},(p,m,a)->{calls.put(m.getName(),a[0]);return null;});init.invoke(controller,state);
        check(Boolean.TRUE.equals(calls.get("setVisible")),"Button stays visible");check(Boolean.TRUE.equals(calls.get("setActive")),"Button stays active");check(calls.containsKey("setIcon"),"Icon uses the public button-state interface");
        var input=(IJeiUserInput)Proxy.newProxyInstance(IJeiUserInput.class.getClassLoader(),new Class<?>[]{IJeiUserInput.class},(p,m,a)->m.getName().equals("isSimulate")?true:null);
        check(Boolean.TRUE.equals(type.getMethod("onPress",IJeiUserInput.class).invoke(controller,input)),"JEI simulated input is handled without opening a screen");
        for(var method:type.getMethods())if(method.isDefault()&&!Set.of("initState","getTooltips").contains(method.getName())){
            Object[] values=new Object[method.getParameterCount()];for(int i=0;i<values.length;i++){var p=method.getParameterTypes()[i];if(p==int.class)values[i]=0;else if(p==float.class)values[i]=0f;else if(p==boolean.class)values[i]=false;}
            method.invoke(controller,values);checks++;
        }
        System.out.println("JEI button API: "+checks+" checks passed; native side-button path");
    }
}
