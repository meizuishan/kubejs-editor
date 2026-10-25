package dev.kubejseditor.client;

import java.lang.reflect.*;
import java.util.*;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import mezz.jei.api.registration.IAdvancedRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Vector3f;

/** Optional JEI 19.27 buttons; 19.25 uses public category decorators and screen events. */
@EventBusSubscriber(modid="kubejs_editor",value=Dist.CLIENT)
public final class JeiRecipeButtons {
    private static boolean legacy;
    private static IDrawable icon;
    private static Screen frame;
    private static int frameWidth,frameHeight;
    private record Entry(Rect2i area,IRecipeCategory<?> category,Object recipe){}
    private static final List<Entry> entries=new ArrayList<>();

    public static void clear(){frame=null;entries.clear();}
    public static boolean isEditorController(Object controller){return controller!=null&&Proxy.isProxyClass(controller.getClass())&&Proxy.getInvocationHandler(controller) instanceof Controller;}

    public static void register(IAdvancedRegistration registration){
        clear();
        var raw=registration.getJeiHelpers().getGuiHelper().createDrawableItemStack(new ItemStack(Items.WRITABLE_BOOK));
        icon=new IDrawable(){public int getWidth(){return 10;}public int getHeight(){return 10;}public void draw(GuiGraphics g,int x,int y){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.625f,.625f,1);raw.draw(g,0,0);g.pose().popPose();}};
        var method=Arrays.stream(IAdvancedRegistration.class.getMethods()).filter(m->m.getName().equals("addRecipeButtonFactory")&&m.getParameterCount()==1).findFirst();
        legacy=method.isEmpty();
        if(method.isPresent()){
            // Reference newer types only through the public method; older JVMs never resolve them.
            var add=method.get();var factoryType=add.getParameterTypes()[0];
            Object factory=Proxy.newProxyInstance(factoryType.getClassLoader(),new Class<?>[]{factoryType},(proxy,call,args)->{
                if(call.getDeclaringClass()==Object.class)return objectMethod(proxy,call,args);
                if(call.getName().equals("createButtonController")){
                    if(Minecraft.getInstance().level==null)return null;
                    var layout=(IRecipeLayoutDrawable<?>)args[0];var source=JeiRecipeSources.resolve(layout);
                    if(source.id()!=null)JeiRecipeSources.capture(layout,source.id());
                    return controller(call.getReturnType(),layout,source);
                }
                if(call.isDefault())return InvocationHandler.invokeDefault(proxy,call,args);
                throw new UnsupportedOperationException("Unknown JEI button API: "+call);
            });
            try{add.invoke(registration,factory);}catch(ReflectiveOperationException error){throw new IllegalStateException("JEI 配方按钮注册失败",error);}
        }else registration.getJeiHelpers().getAllRecipeTypes().forEach(type->registration.addRecipeCategoryDecorator(type,new LegacyDecorator<>()));
    }

    static Object controller(Class<?> type,IRecipeLayoutDrawable<?> layout,JeiRecipeSources.Source source){return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},new Controller(layout,source));}

    private record Controller(IRecipeLayoutDrawable<?> layout,JeiRecipeSources.Source source) implements InvocationHandler {
        @Override public Object invoke(Object proxy,Method call,Object[] args)throws Throwable{
            if(call.getDeclaringClass()==Object.class)return objectMethod(proxy,call,args);
            return switch(call.getName()){
                case "initState"->{var type=call.getParameterTypes()[0];type.getMethod("setIcon",IDrawable.class).invoke(args[0],icon);type.getMethod("setVisible",boolean.class).invoke(args[0],true);type.getMethod("setActive",boolean.class).invoke(args[0],true);yield null;}
                case "getTooltips"->{var tooltip=(ITooltipBuilder)args[0];tooltip.add(Component.literal(source.id()==null?"查看配方适配信息":"使用 KubeJS Editor 编辑此配方"));tooltip.add(Component.literal(source.id()==null?Objects.toString(source.reason(),""):source.id().toString()));yield null;}
                case "onPress"->{if(!((IJeiUserInput)args[0]).isSimulate())open(layout);yield true;}
                default->{if(call.isDefault())yield InvocationHandler.invokeDefault(proxy,call,args);throw new UnsupportedOperationException("Unknown JEI button controller API: "+call);}
            };
        }
    }
    private static Object objectMethod(Object proxy,Method call,Object[] args){return switch(call.getName()){case "toString"->"KubeJS Editor recipe button";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->throw new UnsupportedOperationException(call.toString());};}

    private static boolean recipesScreen(Screen screen){return legacy&&WorkspaceJeiPlugin.runtime!=null&&screen==(Object)WorkspaceJeiPlugin.runtime.getRecipesGui();}
    private static final class LegacyDecorator<T> implements IRecipeCategoryDecorator<T>{
        @Override public void draw(T recipe,IRecipeCategory<T> category,IRecipeSlotsView slots,GuiGraphics g,double mouseX,double mouseY){
            if(frame==null||!recipesScreen(Minecraft.getInstance().screen))return;
            // The book occupies the recipe border, outside all ingredient rectangles.
            var pose=g.pose().last().pose();var p=pose.transformPosition(new Vector3f(Math.max(0,category.getWidth()-10),-11,0));var q=pose.transformPosition(new Vector3f(category.getWidth(),-1,0));
            int x=(int)Math.floor(Math.min(p.x,q.x)),y=(int)Math.floor(Math.min(p.y,q.y));int w=(int)Math.ceil(Math.abs(q.x-p.x)),h=(int)Math.ceil(Math.abs(q.y-p.y));
            if(w>0&&h>0&&x>=0&&y>=0&&x+w<=frameWidth&&y+h<=frameHeight)entries.add(new Entry(new Rect2i(x,y,w,h),category,recipe));
        }
    }
    @SubscribeEvent public static void before(ScreenEvent.Render.Pre event){
        clear();if(recipesScreen(event.getScreen())){frame=event.getScreen();frameWidth=frame.width;frameHeight=frame.height;}
    }
    @SubscribeEvent public static void after(ScreenEvent.Render.Post event){
        if(frame!=event.getScreen()||!recipesScreen(frame))return;
        var g=event.getGuiGraphics();for(var e:entries){var r=e.area;boolean hover=r.contains(event.getMouseX(),event.getMouseY());g.fill(r.getX()-1,r.getY()-1,r.getX()+r.getWidth()+1,r.getY()+r.getHeight()+1,hover?0xFFF0DB8C:0xFFAAAAAA);g.pose().pushPose();g.pose().translate(r.getX(),r.getY(),200);g.pose().scale(r.getWidth()/10f,r.getHeight()/10f,1);icon.draw(g,0,0);g.pose().popPose();if(hover)g.renderTooltip(Minecraft.getInstance().font,Component.literal("使用 KubeJS Editor 打开此配方"),event.getMouseX(),event.getMouseY());}
    }
    @SubscribeEvent public static void click(ScreenEvent.MouseButtonPressed.Pre event){
        if(event.getButton()!=0||frame!=event.getScreen()||!recipesScreen(frame)||frame.width!=frameWidth||frame.height!=frameHeight)return;
        for(var e:List.copyOf(entries))if(e.area.contains((int)event.getMouseX(),(int)event.getMouseY())){event.setCanceled(true);clear();open(e);return;}
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void open(Entry e){
        var mc=Minecraft.getInstance();var previous=mc.screen;
        mc.tell(()->{try{var runtime=WorkspaceJeiPlugin.runtime;if(runtime==null)return;var layout=runtime.getRecipeManager().createRecipeLayoutDrawable((IRecipeCategory)e.category,e.recipe,runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();show(previous,(IRecipeLayoutDrawable<?>)layout);}catch(Exception error){failed(previous,e.category,error);}});
    }
    private static void open(IRecipeLayoutDrawable<?> layout){var mc=Minecraft.getInstance();var previous=mc.screen;mc.tell(()->show(previous,layout));}
    private static void show(Screen previous,IRecipeLayoutDrawable<?> layout){try{JeiDisplayScreen.open(previous,layout);}catch(Exception error){failed(previous,layout.getRecipeCategory(),error);}}
    private static void failed(Screen previous,IRecipeCategory<?> category,Exception error){var mc=Minecraft.getInstance();CompatDiagnostics.failure("open",category.getRecipeType().getUid().toString(),error);mc.setScreen(new TextScreen(previous,"打开配方失败",error+"\n草稿已保留，可在更多中查看最近诊断"));if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("无法打开配方："+error.getMessage()));}
    private JeiRecipeButtons(){}
}
