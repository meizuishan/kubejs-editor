package dev.kubejseditor.client;

import net.minecraft.network.chat.Component;

/** Stable language keys for existing labels; mod-provided text retains its own fallback. */
public final class UiText {
    public static String key(String original){return "screen.kubejs_editor.text."+Integer.toUnsignedString(original.hashCode(),36);}
    public static Component component(String original){
        if(original.startsWith("● "))return Component.literal("● ").append(component(original.substring(2)));
        return Component.translatableWithFallback(key(original),original);
    }
    public static String text(String original){return component(original).getString();}
    public static String format(String original,Object... args){return Component.translatableWithFallback(key(original),original,args).getString();}
    private UiText(){}
}
